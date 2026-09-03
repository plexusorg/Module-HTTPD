package dev.plex.request.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import jakarta.servlet.AsyncContext;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Samples Bukkit/JMX/Runtime stats off the request thread and fans the
 * resulting JSON out to every connected SSE subscriber. One sampler tick on
 * the Minecraft main thread; assembly and writes happen on a dedicated
 * executor so slow clients can't stall the global-region sampler.
 */
public final class StatsBroadcaster
{
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final HTTPDModule module;
    private static final String CHANNEL = "stats";
    private SseTransport<String, Void> transport;

    private volatile int cachedChunks;
    private volatile int cachedEntities;
    private volatile int cachedWorlds;
    private volatile int cachedOnlinePlayers;
    private volatile int cachedMaxPlayers;
    private volatile int cachedPlugins;
    private volatile double[] cachedTps = new double[]{20d, 20d, 20d};
    private volatile String cachedVersion = "unknown";

    private final long serverStartTime =
        System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getUptime();

    private ScheduledExecutorService broadcastExecutor;
    private ScheduledTask bukkitTask;
    private ScheduledFuture<?> broadcastTask;

    private int maxConnections = 32;
    private long broadcastIntervalMs = 2000L;

    public StatsBroadcaster(HTTPDModule module)
    {
        this.module = module;
    }

    public synchronized void start()
    {
        if (transport != null) return;

        maxConnections = module.getModuleConfig().getInt("server.sse.max-connections", 32);
        broadcastIntervalMs = module.getModuleConfig().getLong("server.sse.broadcast-interval-ms", 2000L);
        int threads = Math.max(1, module.getModuleConfig().getInt("server.sse.threads", 2));

        transport = new SseTransport<>(maxConnections, threads, "Plex-HTTPD-SSE",
                () -> module.ownTask(org.bukkit.Bukkit.getGlobalRegionScheduler().run(module.plugin(),
                        task -> sampleBukkit())), ignored -> {});
        broadcastExecutor = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread t = new Thread(r, "Plex-HTTPD-SSE");
            t.setDaemon(true);
            return t;
        });

        long sampleTicks = Math.max(20L, module.getModuleConfig().getLong("server.sse.stats-sample-interval-ticks", 100L));
        bukkitTask = module.ownTask(org.bukkit.Bukkit.getGlobalRegionScheduler().runAtFixedRate(module.plugin(),
                task -> sampleBukkit(), 1L, sampleTicks));

        broadcastTask = broadcastExecutor.scheduleAtFixedRate(
            this::tick, broadcastIntervalMs, broadcastIntervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void shutdown()
    {
        if (bukkitTask != null)
        {
            bukkitTask.cancel();
            bukkitTask = null;
        }
        if (broadcastTask != null)
        {
            broadcastTask.cancel(false);
            broadcastTask = null;
        }
        if (broadcastExecutor != null)
        {
            broadcastExecutor.shutdownNow();
            broadcastExecutor = null;
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

    public boolean addSubscriber(AsyncContext ctx, PrintWriter writer)
    {
        return transport.add(CHANNEL, ctx, writer, null);
    }

    public void removeSubscriber(AsyncContext ctx)
    {
        transport.remove(CHANNEL, ctx);
    }

    public String currentPayload()
    {
        return buildPayload();
    }

    private void sampleBukkit()
    {
        SseTransport<String, Void> activeTransport = transport;
        if (activeTransport == null || !activeTransport.hasSubscribers()) return;
        int chunks = 0;
        int entities = 0;
        for (World world : Bukkit.getWorlds())
        {
            chunks += world.getChunkCount();
            entities += world.getEntityCount();
        }
        cachedChunks = chunks;
        cachedEntities = entities;
        cachedWorlds = Bukkit.getWorlds().size();
        cachedOnlinePlayers = Bukkit.getOnlinePlayers().size();
        cachedMaxPlayers = Bukkit.getMaxPlayers();
        cachedPlugins = Bukkit.getPluginManager().getPlugins().length;
        cachedTps = Bukkit.getTPS();
        cachedVersion = Bukkit.getMinecraftVersion();
    }

    private void tick()
    {
        SseTransport<String, Void> activeTransport = transport;
        if (activeTransport == null || !activeTransport.hasSubscribers()) return;
        final String frame = "data: " + buildPayload() + "\n\n";
        activeTransport.publish(CHANNEL, ignored -> frame);
    }

    private String buildPayload()
    {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("version", cachedVersion);
        server.put("startTime", serverStartTime);
        server.put("tps", cachedTps);
        root.put("server", server);

        com.sun.management.OperatingSystemMXBean os =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("process", clamp01(os.getProcessCpuLoad()));
        cpu.put("system", clamp01(os.getCpuLoad()));
        cpu.put("cores", os.getAvailableProcessors());
        cpu.put("loadAverage", os.getSystemLoadAverage());
        root.put("cpu", cpu);

        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        long used = total - free;
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("used", used);
        memory.put("total", total);
        memory.put("max", max);
        root.put("memory", memory);

        Map<String, Object> players = new LinkedHashMap<>();
        players.put("online", cachedOnlinePlayers);
        players.put("max", cachedMaxPlayers);
        root.put("players", players);

        Map<String, Object> world = new LinkedHashMap<>();
        world.put("loadedChunks", cachedChunks);
        world.put("entities", cachedEntities);
        world.put("worlds", cachedWorlds);
        root.put("world", world);

        Map<String, Object> plugins = new LinkedHashMap<>();
        plugins.put("active", cachedPlugins);
        root.put("plugins", plugins);

        return GSON.toJson(root);
    }

    private static double clamp01(double v)
    {
        if (Double.isNaN(v) || v < 0) return 0d;
        if (v > 1) return 1d;
        return v;
    }

}
