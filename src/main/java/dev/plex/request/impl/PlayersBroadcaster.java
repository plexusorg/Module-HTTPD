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
import java.util.concurrent.atomic.AtomicReferenceArray;
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
    private static final String CHANNEL = "players";
    private SseTransport<String, Boolean> transport;
    private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);

    private volatile String cachedPublicFrame = "{\"players\":[],\"max\":0}";
    private volatile String cachedStaffFrame  = "{\"players\":[],\"max\":0}";

    private ScheduledTask refreshTask;
    private Listener listener;

    public PlayersBroadcaster(HTTPDModule module)
    {
        this.module = module;
    }

    public synchronized void start()
    {
        if (transport != null) return;
        int maxConnections = module.getModuleConfig().getInt("server.sse.max-connections", 32);
        int threads = module.getModuleConfig().getInt("server.sse.threads", 2);
        transport = new SseTransport<>(maxConnections, threads, "Plex-HTTPD-Players-SSE",
                this::scheduleRefresh, ignored -> {});
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
        if (transport != null)
        {
            transport.shutdown();
        }
    }

    public boolean atCapacity()
    {
        return transport.atCapacity();
    }

    public boolean addSubscriber(AsyncContext context, PrintWriter writer, boolean staff)
    {
        return transport.add(CHANNEL, context, writer, staff);
    }

    public void removeSubscriber(AsyncContext context)
    {
        transport.remove(CHANNEL, context);
    }
    public String currentPayload(boolean staff)
    {
        return staff ? cachedStaffFrame : cachedPublicFrame;
    }

    private void refreshAndBroadcast()
    {
        if (!hasSubscribers()) return;
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
        if (refreshPending.getAndSet(false) && hasSubscribers()) scheduleRefresh();
    }

    private void publish(List<Map<String, Object>> publicPlayers, List<Map<String, Object>> staffPlayers, int max)
    {
        String publicJson = buildPayload(publicPlayers, max);
        String staffJson = buildPayload(staffPlayers, max);
        cachedPublicFrame = publicJson;
        cachedStaffFrame = staffJson;
        SseTransport<String, Boolean> activeTransport = transport;
        if (activeTransport == null) return;
        String publicFrame = "data: " + publicJson + "\n\n";
        String staffFrame = "data: " + staffJson + "\n\n";
        activeTransport.publish(CHANNEL, staff -> staff ? staffFrame : publicFrame);
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

    private boolean hasSubscribers()
    {
        SseTransport<String, Boolean> activeTransport = transport;
        return activeTransport != null && activeTransport.hasSubscribers();
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

    private final class PlayersListener implements Listener
    {
        @EventHandler
        public void onJoin(PlayerJoinEvent e) { scheduleRefresh(); }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) { scheduleRefresh(); }

        @EventHandler
        public void onWorldChange(PlayerChangedWorldEvent e) { scheduleRefresh(); }
    }

}
