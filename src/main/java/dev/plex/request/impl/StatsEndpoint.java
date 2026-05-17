package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.Plex;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;

public class StatsEndpoint extends AbstractServlet
{
    private static volatile int cachedChunks = 0;
    private static volatile int cachedEntities = 0;
    private static volatile boolean schedulerStarted = false;

    public StatsEndpoint()
    {
        super();
        startWorldSnapshotTask();
    }

    private static synchronized void startWorldSnapshotTask()
    {
        if (schedulerStarted) return;
        try
        {
            Bukkit.getScheduler().runTaskTimer(Plex.get(), () ->
            {
                int chunks = 0;
                int entities = 0;
                for (World world : Bukkit.getWorlds())
                {
                    try
                    {
                        chunks += world.getLoadedChunks().length;
                        entities += world.getEntities().size();
                    }
                    catch (Throwable ignored)
                    {
                    }
                }
                cachedChunks = chunks;
                cachedEntities = entities;
            }, 0L, 40L);
            schedulerStarted = true;
        }
        catch (Throwable ignored)
        {
        }
    }

    @GetMapping(endpoint = "/api/stats/")
    @MappingHeaders(headers = {"content-type;application/json; charset=utf-8", "cache-control;no-store"})
    public String getStats(HttpServletRequest request, HttpServletResponse response)
    {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("version", safeServerVersion());
        server.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        server.put("tps", safeTps());
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
        players.put("online", Bukkit.getOnlinePlayers().size());
        players.put("max", Bukkit.getMaxPlayers());
        root.put("players", players);

        Map<String, Object> world = new LinkedHashMap<>();
        world.put("loadedChunks", cachedChunks);
        world.put("entities", cachedEntities);
        world.put("worlds", Bukkit.getWorlds().size());
        root.put("world", world);

        Map<String, Object> plugins = new LinkedHashMap<>();
        plugins.put("active", Bukkit.getPluginManager().getPlugins().length);
        root.put("plugins", plugins);

        return new GsonBuilder().serializeNulls().create().toJson(root);
    }

    private static double clamp01(double v)
    {
        if (Double.isNaN(v) || v < 0) return 0d;
        if (v > 1) return 1d;
        return v;
    }

    private static double[] safeTps()
    {
        try
        {
            return Bukkit.getTPS();
        }
        catch (Throwable t)
        {
            return new double[]{20d, 20d, 20d};
        }
    }

    private static String safeServerVersion()
    {
        try
        {
            return Bukkit.getMinecraftVersion();
        }
        catch (Throwable t)
        {
            return Bukkit.getBukkitVersion();
        }
    }
}
