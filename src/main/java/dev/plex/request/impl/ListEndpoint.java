package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ListEndpoint extends AbstractServlet
{
    List<String> players = new ArrayList<>();

    @GetMapping(endpoint = "/api/list/")
    public String getOnlinePlayers(HttpServletRequest request, HttpServletResponse response)
    {
        for (Player player : Bukkit.getOnlinePlayers())
        {
            players.add(player.getName());
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(players.stream().toList());
    }
}
