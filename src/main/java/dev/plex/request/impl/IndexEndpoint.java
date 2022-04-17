package dev.plex.request.impl;

import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.bukkit.Bukkit;

public class IndexEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "//")
    public String getIndex(HttpServletRequest request)
    {
        return indexHTML();
    }

    @GetMapping(endpoint = "/api/")
    public String getAPI(HttpServletRequest request)
    {
        return indexHTML();
    }

    private String indexHTML()
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/index.html"));
        String isAre = Bukkit.getOnlinePlayers().size() == 1 ? " is " : " are ";
        String pluralOnline = Bukkit.getOnlinePlayers().size() == 1 ? " player " : " players ";
        String pluralMax = Bukkit.getMaxPlayers() == 1 ? " player " : " players ";
        file = file.replace("${is_are}", isAre);
        file = file.replace("${server_online_players}", Bukkit.getOnlinePlayers().size() + pluralOnline);
        file = file.replace("${server_total_players}", Bukkit.getMaxPlayers() + pluralMax);
        return file;
    }
}
