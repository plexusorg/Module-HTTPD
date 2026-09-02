package dev.plex.request.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import jakarta.servlet.AsyncContext;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final long REFRESH_TICKS = 100L; // 5 seconds at 20 TPS
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final HTTPDModule module;
    private final Set<Subscriber> subscribers = ConcurrentHashMap.newKeySet();
    private final AtomicInteger subscriberCount = new AtomicInteger();
    private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);

    private volatile String cachedPublicFrame = "{\"players\":[],\"max\":0}";
    private volatile String cachedStaffFrame  = "{\"players\":[],\"max\":0}";

    private ScheduledExecutorService executor;
    private ScheduledTask refreshTask;
    private Listener listener;
    private int maxConnections = 32;

    public PlayersBroadcaster(HTTPDModule module)
    {
        this.module = module;
    }

    public synchronized void start()
    {
        if (executor != null) return;

        maxConnections = module.getModuleConfig().getInt("server.sse.max-connections", 32);
        int threads = Math.max(1, module.getModuleConfig().getInt("server.sse.threads", 2));

        executor = Executors.newScheduledThreadPool(threads, r ->
        {
            Thread t = new Thread(r, "Plex-HTTPD-Players-SSE");
            t.setDaemon(true);
            return t;
        });

        listener = new PlayersListener();
        module.registerListener(listener);
        refreshTask = module.scheduler().runGlobalTimer(this::refreshAndBroadcast, 1L, REFRESH_TICKS);
    }

    public synchronized void shutdown()
    {
        if (listener != null)
        {
            module.unregisterListener(listener);
            listener = null;
        }
        if (refreshTask != null)
        {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }
        for (Subscriber sub : subscribers)
        {
            complete(sub.ctx);
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
        if (!reserveConnection()) return false;
        Subscriber sub = new Subscriber(ctx, writer, staff);
        if (subscribers.add(sub))
        {
            scheduleRefresh();
            return true;
        }
        subscriberCount.decrementAndGet();
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
        if (subscribers.isEmpty()) return;
        if (!refreshInProgress.compareAndSet(false, true))
        {
            refreshPending.set(true);
            return;
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int max = Bukkit.getMaxPlayers();
        if (online.isEmpty())
        {
            publish(List.of(), List.of(), max);
            finishRefresh();
            return;
        }

        AtomicReferenceArray<Map<String, Object>> publicPlayers = new AtomicReferenceArray<>(online.size());
        AtomicReferenceArray<Map<String, Object>> staffPlayers = new AtomicReferenceArray<>(online.size());
        AtomicInteger remaining = new AtomicInteger(online.size());
        for (int i = 0; i < online.size(); i++)
        {
            final int index = i;
            Player player = online.get(i);
            boolean scheduled = module.scheduler().executeEntity(player, () ->
            {
                try
                {
                    publicPlayers.set(index, buildPublicPlayer(player));
                    staffPlayers.set(index, buildStaffPlayer(player));
                }
                finally
                {
                    finishPlayer(publicPlayers, staffPlayers, remaining, max);
                }
            }, () -> finishPlayer(publicPlayers, staffPlayers, remaining, max), 1L);
            if (!scheduled)
            {
                finishPlayer(publicPlayers, staffPlayers, remaining, max);
            }
        }
    }

    private void finishPlayer(AtomicReferenceArray<Map<String, Object>> publicPlayers,
                              AtomicReferenceArray<Map<String, Object>> staffPlayers,
                              AtomicInteger remaining, int max)
    {
        if (remaining.decrementAndGet() == 0)
        {
            publishAndFinish(publicPlayers, staffPlayers, max);
        }
    }

    private void publishAndFinish(AtomicReferenceArray<Map<String, Object>> publicPlayers,
                                  AtomicReferenceArray<Map<String, Object>> staffPlayers, int max)
    {
        try
        {
            publish(compact(publicPlayers), compact(staffPlayers), max);
        }
        finally
        {
            finishRefresh();
        }
    }

    private void finishRefresh()
    {
        refreshInProgress.set(false);
        if (refreshPending.getAndSet(false) && !subscribers.isEmpty()) scheduleRefresh();
    }

    private void publish(List<Map<String, Object>> publicPlayers, List<Map<String, Object>> staffPlayers, int max)
    {
        String publicJson = buildPayload(publicPlayers, max);
        String staffJson = buildPayload(staffPlayers, max);
        cachedPublicFrame = publicJson;
        cachedStaffFrame = staffJson;

        ScheduledExecutorService exec = executor;
        if (exec == null || subscribers.isEmpty()) return;

        final String publicFrame = "data: " + publicJson + "\n\n";
        final String staffFrame  = "data: " + staffJson  + "\n\n";
        for (Subscriber sub : subscribers)
        {
            final String frame = sub.staff ? staffFrame : publicFrame;
            enqueueFrame(exec, sub, frame);
        }
    }

    private static List<Map<String, Object>> compact(AtomicReferenceArray<Map<String, Object>> players)
    {
        List<Map<String, Object>> result = new ArrayList<>(players.length());
        for (int i = 0; i < players.length(); i++)
        {
            Map<String, Object> player = players.get(i);
            if (player != null)
            {
                result.add(player);
            }
        }
        return result;
    }

    private void enqueueFrame(ScheduledExecutorService exec, Subscriber sub, String frame)
    {
        sub.pendingFrame.set(frame);
        if (!sub.writing.compareAndSet(false, true)) return;
        submitDrain(exec, sub);
    }

    private void submitDrain(ScheduledExecutorService exec, Subscriber sub)
    {
        try
        {
            exec.execute(() -> drainFrames(sub));
        }
        catch (RejectedExecutionException ignored)
        {
            sub.writing.set(false);
            dropSubscriber(sub);
        }
    }

    private void drainFrames(Subscriber sub)
    {
        try
        {
            while (subscribers.contains(sub))
            {
                String frame = sub.pendingFrame.getAndSet(null);
                if (frame == null) return;
                sub.writer.write(frame);
                sub.writer.flush();
                if (sub.writer.checkError())
                {
                    dropSubscriber(sub);
                    return;
                }
            }
        }
        finally
        {
            sub.writing.set(false);
            ScheduledExecutorService exec = executor;
            if (exec != null && subscribers.contains(sub) && sub.pendingFrame.get() != null && sub.writing.compareAndSet(false, true))
            {
                submitDrain(exec, sub);
            }
        }
    }

    private void dropSubscriber(Subscriber sub)
    {
        sub.pendingFrame.set(null);
        if (subscribers.remove(sub)) subscriberCount.decrementAndGet();
        complete(sub.ctx);
    }

    private boolean reserveConnection()
    {
        while (true)
        {
            int current = subscriberCount.get();
            if (current >= maxConnections) return false;
            if (subscriberCount.compareAndSet(current, current + 1)) return true;
        }
    }

    /**
     * Defers refresh by one tick so PlayerQuitEvent (which fires BEFORE the
     * player leaves the online list) samples the correct post-state, and so
     * concurrent events collapse into a single broadcast.
     */
    private void scheduleRefresh()
    {
        if (!refreshScheduled.compareAndSet(false, true)) return;
        module.scheduler().runGlobalLater(() ->
        {
            refreshScheduled.set(false);
            refreshAndBroadcast();
        }, 1L);
    }

    private String buildPayload(List<Map<String, Object>> players, int max)
    {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("players", players);
        root.put("max", max);
        return GSON.toJson(root);
    }

    private Map<String, Object> buildPublicPlayer(Player p)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuid", p.getUniqueId().toString());
        m.put("name", p.getName());
        m.put("world", p.getWorld().getName());
        m.put("ping", p.getPing());
        return m;
    }

    private Map<String, Object> buildStaffPlayer(Player p)
    {
        Map<String, Object> m = buildPublicPlayer(p);
        m.put("op", p.isOp());
        m.put("gamemode", p.getGameMode().name());
        return m;
    }

    private static void complete(AsyncContext context)
    {
        try
        {
            context.complete();
        }
        catch (IllegalStateException ignored)
        {
        }
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
        final AtomicReference<String> pendingFrame = new AtomicReference<>();
        final AtomicBoolean writing = new AtomicBoolean(false);
        Subscriber(AsyncContext ctx, PrintWriter writer, boolean staff)
        {
            this.ctx = ctx;
            this.writer = writer;
            this.staff = staff;
        }
    }
}
