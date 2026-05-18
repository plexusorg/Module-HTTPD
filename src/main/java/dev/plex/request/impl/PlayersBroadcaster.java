package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import dev.plex.Plex;
import dev.plex.util.PlexLog;
import jakarta.servlet.AsyncContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pushes the online-player list to SSE subscribers on join/quit/world-change,
 * plus a 5-second periodic refresh so ping values stay fresh (Player#getPing
 * returns 0 until the first keepalive packet round-trip after join). Two
 * payload variants are produced each refresh — a minimal one for anonymous
 * viewers and a richer one for staff — so the public endpoint can't leak
 * staff-only fields.
 */
public final class PlayersBroadcaster
{
    private static final PlayersBroadcaster INSTANCE = new PlayersBroadcaster();
    private static final long REFRESH_TICKS = 100L; // 5 seconds at 20 TPS

    public static PlayersBroadcaster get()
    {
        return INSTANCE;
    }

    private final Set<Subscriber> subscribers = ConcurrentHashMap.newKeySet();
    private final AtomicInteger subscriberCount = new AtomicInteger();
    private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);

    private volatile String cachedPublicFrame = "{\"players\":[],\"max\":0}";
    private volatile String cachedStaffFrame  = "{\"players\":[],\"max\":0}";

    private ScheduledExecutorService executor;
    private BukkitTask refreshTask;
    private Listener listener;
    private int maxConnections = 32;

    private PlayersBroadcaster() {}

    public synchronized void start()
    {
        if (executor != null) return;

        maxConnections = HTTPDModule.moduleConfig.getInt("server.sse.max-connections", 32);
        int threads = Math.max(1, HTTPDModule.moduleConfig.getInt("server.sse.threads", 2));

        executor = Executors.newScheduledThreadPool(threads, r ->
        {
            Thread t = new Thread(r, "Plex-HTTPD-Players-SSE");
            t.setDaemon(true);
            return t;
        });

        listener = new PlayersListener();
        try
        {
            Bukkit.getPluginManager().registerEvents(listener, Plex.get());
        }
        catch (Throwable t)
        {
            PlexLog.debug("PlayersBroadcaster: could not register Bukkit listener: " + t.getMessage());
        }

        try
        {
            refreshTask = Bukkit.getScheduler().runTaskTimer(
                Plex.get(), this::refreshAndBroadcast, 0L, REFRESH_TICKS);
        }
        catch (Throwable t)
        {
            PlexLog.debug("PlayersBroadcaster: could not register refresh task: " + t.getMessage());
        }
    }

    public synchronized void shutdown()
    {
        if (listener != null)
        {
            try { HandlerList.unregisterAll(listener); } catch (Throwable ignored) {}
            listener = null;
        }
        if (refreshTask != null)
        {
            try { refreshTask.cancel(); } catch (Throwable ignored) {}
            refreshTask = null;
        }
        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }
        for (Subscriber sub : subscribers)
        {
            try { sub.ctx.complete(); } catch (Throwable ignored) {}
        }
        subscribers.clear();
        subscriberCount.set(0);
    }

    public boolean atCapacity()
    {
        return subscriberCount.get() >= maxConnections;
    }

    public boolean addSubscriber(AsyncContext ctx, PrintWriter writer, boolean staff)
    {
        if (subscriberCount.get() >= maxConnections) return false;
        Subscriber sub = new Subscriber(ctx, writer, staff);
        if (subscribers.add(sub))
        {
            subscriberCount.incrementAndGet();
            return true;
        }
        return false;
    }

    public void removeSubscriber(AsyncContext ctx)
    {
        Subscriber match = null;
        for (Subscriber sub : subscribers)
        {
            if (sub.ctx == ctx) { match = sub; break; }
        }
        if (match != null && subscribers.remove(match))
        {
            subscriberCount.decrementAndGet();
        }
    }

    public String currentPayload(boolean staff)
    {
        return staff ? cachedStaffFrame : cachedPublicFrame;
    }

    private void refreshAndBroadcast()
    {
        try
        {
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            int max = Bukkit.getMaxPlayers();
            String publicJson = buildPublicPayload(online, max);
            String staffJson  = buildStaffPayload(online, max);
            cachedPublicFrame = publicJson;
            cachedStaffFrame  = staffJson;

            ScheduledExecutorService exec = executor;
            if (exec == null || subscribers.isEmpty()) return;

            final String publicFrame = "data: " + publicJson + "\n\n";
            final String staffFrame  = "data: " + staffJson  + "\n\n";
            for (Subscriber sub : subscribers)
            {
                final String frame = sub.staff ? staffFrame : publicFrame;
                try
                {
                    exec.execute(() -> writeFrame(sub, frame));
                }
                catch (Throwable t)
                {
                    dropSubscriber(sub);
                }
            }
        }
        catch (Throwable ignored) {}
    }

    private void writeFrame(Subscriber sub, String frame)
    {
        try
        {
            sub.writer.write(frame);
            sub.writer.flush();
            if (sub.writer.checkError()) dropSubscriber(sub);
        }
        catch (Throwable t)
        {
            dropSubscriber(sub);
        }
    }

    private void dropSubscriber(Subscriber sub)
    {
        if (subscribers.remove(sub)) subscriberCount.decrementAndGet();
        try { sub.ctx.complete(); } catch (Throwable ignored) {}
    }

    /**
     * Defers refresh by one tick so PlayerQuitEvent (which fires BEFORE the
     * player leaves the online list) samples the correct post-state, and so
     * concurrent events collapse into a single broadcast.
     */
    private void scheduleRefresh()
    {
        if (!refreshScheduled.compareAndSet(false, true)) return;
        try
        {
            Bukkit.getScheduler().runTaskLater(Plex.get(), () ->
            {
                refreshScheduled.set(false);
                refreshAndBroadcast();
            }, 1L);
        }
        catch (Throwable t)
        {
            refreshScheduled.set(false);
        }
    }

    private String buildPublicPayload(List<Player> online, int max)
    {
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player p : online)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", p.getUniqueId().toString());
            m.put("name", p.getName());
            try { m.put("world", p.getWorld() != null ? p.getWorld().getName() : ""); }
            catch (Throwable ignored) { m.put("world", ""); }
            int ping = 0;
            try { ping = p.getPing(); } catch (Throwable ignored) {}
            m.put("ping", ping);
            players.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("players", players);
        root.put("max", max);
        return new GsonBuilder().serializeNulls().create().toJson(root);
    }

    private String buildStaffPayload(List<Player> online, int max)
    {
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player p : online)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", p.getUniqueId().toString());
            m.put("name", p.getName());
            try { m.put("world", p.getWorld() != null ? p.getWorld().getName() : ""); }
            catch (Throwable ignored) { m.put("world", ""); }
            try { m.put("op", p.isOp()); } catch (Throwable ignored) { m.put("op", false); }
            try { m.put("gamemode", p.getGameMode().name()); }
            catch (Throwable ignored) { m.put("gamemode", ""); }
            int ping = 0;
            try { ping = p.getPing(); } catch (Throwable ignored) {}
            m.put("ping", ping);
            players.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("players", players);
        root.put("max", max);
        return new GsonBuilder().serializeNulls().create().toJson(root);
    }

    private final class PlayersListener implements Listener
    {
        @EventHandler
        public void onJoin(PlayerJoinEvent e) { scheduleRefresh(); }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) { scheduleRefresh(); }

        @EventHandler
        public void onWorldChange(PlayerChangedWorldEvent e) { scheduleRefresh(); }
    }

    private static final class Subscriber
    {
        final AsyncContext ctx;
        final PrintWriter writer;
        final boolean staff;
        Subscriber(AsyncContext ctx, PrintWriter writer, boolean staff)
        {
            this.ctx = ctx;
            this.writer = writer;
            this.staff = staff;
        }
    }
}
