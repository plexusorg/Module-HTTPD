package dev.plex.request.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ListEndpoint extends AbstractServlet
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long CACHE_MILLIS = 1000L;

    private volatile String cachedJson = "[]";
    private volatile long cachedAt;

    public ListEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/api/list/")
    @MappingHeaders(headers = "content-type;application/json")
    public String getOnlinePlayers(HttpServletRequest request, HttpServletResponse response)
    {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MILLIS) return cachedJson;
        synchronized (this)
        {
            now = System.currentTimeMillis();
            if (now - cachedAt < CACHE_MILLIS) return cachedJson;
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers())
            {
                players.add(player.getName());
            }
            cachedJson = GSON.toJson(players);
            cachedAt = now;
            return cachedJson;
        }
    }
}
