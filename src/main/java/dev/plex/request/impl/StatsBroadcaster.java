package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import jakarta.servlet.AsyncContext;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Samples Bukkit/JMX/Runtime stats off the request thread and fans the
 * resulting JSON out to every connected SSE subscriber. One sampler tick on
 * the Minecraft main thread; assembly and writes happen on a dedicated
 * executor so slow clients can't stall the tick loop.
 */
public final class StatsBroadcaster
{
    private static final StatsBroadcaster INSTANCE = new StatsBroadcaster();

    public static StatsBroadcaster get()
    {
        return INSTANCE;
    }

    private final Set<Subscriber> subscribers = ConcurrentHashMap.newKeySet();
    private final AtomicInteger subscriberCount = new AtomicInteger();

    // Sampled on the Bukkit main thread.
    private volatile int cachedChunks;
    private volatile int cachedEntities;
    private volatile int cachedWorlds;
    private volatile int cachedOnlinePlayers;
    private volatile int cachedMaxPlayers;
    private volatile int cachedPlugins;
    private volatile double[] cachedTps = new double[]{20d, 20d, 20d};
    private volatile String cachedVersion = "unknown";

    // Epoch ms when the JVM started — derived once, used by the client to tick uptime locally.
    private final long serverStartTime =
        System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getUptime();

    private ScheduledExecutorService executor;
    private BukkitTask bukkitTask;
    private ScheduledFuture<?> broadcastTask;

    private int maxConnections = 32;
    private long broadcastIntervalMs = 2000L;

    private StatsBroadcaster() {}

    public synchronized void start()
    {
        if (executor != null) return;

        maxConnections = HTTPDModule.moduleConfig.getInt("server.sse.max-connections", 32);
        broadcastIntervalMs = HTTPDModule.moduleConfig.getLong("server.sse.broadcast-interval-ms", 2000L);
        int threads = Math.max(1, HTTPDModule.moduleConfig.getInt("server.sse.threads", 2));

        executor = Executors.newScheduledThreadPool(threads, r ->
        {
            Thread t = new Thread(r, "Plex-HTTPD-SSE");
            t.setDaemon(true);
            return t;
        });

        try
        {
            bukkitTask = (BukkitTask)HTTPDModule.plexApi().scheduler().runTimer(this::sampleBukkit, 0L, 40L);
        }
        catch (Throwable t)
        {
            HTTPDModule.plexApi().logging().debug("StatsBroadcaster: could not register Bukkit sampling task: " + t.getMessage());
        }

        broadcastTask = executor.scheduleAtFixedRate(
            this::tick, broadcastIntervalMs, broadcastIntervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void shutdown()
    {
        if (bukkitTask != null)
        {
            try { bukkitTask.cancel(); } catch (Throwable ignored) {}
            bukkitTask = null;
        }
        if (broadcastTask != null)
        {
            broadcastTask.cancel(false);
            broadcastTask = null;
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

    public boolean addSubscriber(AsyncContext ctx, PrintWriter writer)
    {
        if (subscriberCount.get() >= maxConnections) return false;
        Subscriber sub = new Subscriber(ctx, writer);
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
            if (sub.ctx == ctx)
            {
                match = sub;
                break;
            }
        }
        if (match != null && subscribers.remove(match))
        {
            subscriberCount.decrementAndGet();
        }
    }

    public String currentPayload()
    {
        return buildPayload();
    }

    private void sampleBukkit()
    {
        try
        {
            int chunks = 0;
            int entities = 0;
            for (World w : Bukkit.getWorlds())
            {
                try
                {
                    chunks += w.getLoadedChunks().length;
                    entities += w.getEntities().size();
                }
                catch (Throwable ignored) {}
            }
            cachedChunks = chunks;
            cachedEntities = entities;
            cachedWorlds = Bukkit.getWorlds().size();
            cachedOnlinePlayers = Bukkit.getOnlinePlayers().size();
            cachedMaxPlayers = Bukkit.getMaxPlayers();
            cachedPlugins = Bukkit.getPluginManager().getPlugins().length;
            try { cachedTps = Bukkit.getTPS(); } catch (Throwable ignored) {}
            try
            {
                cachedVersion = Bukkit.getMinecraftVersion();
            }
            catch (Throwable ignored)
            {
                try { cachedVersion = Bukkit.getBukkitVersion(); } catch (Throwable ignored2) {}
            }
        }
        catch (Throwable ignored) {}
    }

    private void tick()
    {
        if (subscribers.isEmpty()) return;
        final String frame = "data: " + buildPayload() + "\n\n";
        ScheduledExecutorService exec = executor;
        if (exec == null) return;
        for (Subscriber sub : subscribers)
        {
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

    private void writeFrame(Subscriber sub, String frame)
    {
        try
        {
            sub.writer.write(frame);
            sub.writer.flush();
            if (sub.writer.checkError())
            {
                dropSubscriber(sub);
            }
        }
        catch (Throwable t)
        {
            dropSubscriber(sub);
        }
    }

    private void dropSubscriber(Subscriber sub)
    {
        if (subscribers.remove(sub))
        {
            subscriberCount.decrementAndGet();
        }
        try { sub.ctx.complete(); } catch (Throwable ignored) {}
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

        return new GsonBuilder().serializeNulls().create().toJson(root);
    }

    private static double clamp01(double v)
    {
        if (Double.isNaN(v) || v < 0) return 0d;
        if (v > 1) return 1d;
        return v;
    }

    private static final class Subscriber
    {
        final AsyncContext ctx;
        final PrintWriter writer;

        Subscriber(AsyncContext ctx, PrintWriter writer)
        {
            this.ctx = ctx;
            this.writer = writer;
        }
    }
}
