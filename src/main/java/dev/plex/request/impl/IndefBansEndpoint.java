package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import dev.plex.Plex;
import dev.plex.cache.DataUtils;
import dev.plex.player.PlexPlayer;
import dev.plex.rank.enums.Rank;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.util.PlexLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class IndefBansEndpoint extends AbstractServlet
{
    private static final String TITLE = "Indefinite Bans - Plex HTTPD";

    @GetMapping(endpoint = "/api/indefbans/")
    public String getBans(HttpServletRequest request, HttpServletResponse response)
    {
        String ipAddress = request.getRemoteAddr();
        if (ipAddress == null)
        {
            return indefbansHTML("An IP address could not be detected. Please ensure you are connecting using IPv4.");
        }
        final PlexPlayer player = DataUtils.getPlayerByIP(ipAddress);
        if (player == null)
        {
            return indefbansHTML("Couldn't load your IP Address: " + ipAddress + ". Have you joined the server before?");
        }
        if (Plex.get().getSystem().equalsIgnoreCase("ranks"))
        {
            PlexLog.debug("Plex-HTTPD using ranks check");
            if (!player.getRankFromString().isAtLeast(Rank.ADMIN))
            {
                return indefbansHTML("Not a high enough rank to view this page.");
            }
        }
        else if (Plex.get().getSystem().equalsIgnoreCase("permissions"))
        {
            PlexLog.debug("Plex-HTTPD using permissions check");
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUuid());
            if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.indefbans.access"))
            {
                return indefbansHTML("Not enough permissions to view this page.");
            }
        }

        response.setHeader("content-type", "application/json");
        return new GsonBuilder().setPrettyPrinting().create().toJson(Plex.get().getPunishmentManager().getIndefiniteBans().stream().toList());
    }

    private String indefbansHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/indefbans.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }
}
