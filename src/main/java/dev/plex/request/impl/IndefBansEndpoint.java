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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class IndefBansEndpoint extends AbstractServlet
{
    private static final String TITLE = "Indefinite Bans - Plex HTTPD";

    @GetMapping(endpoint = "/api/indefbans/")
    public String getBans(HttpServletRequest request)
    {
        String ipAddress = request.getRemoteAddr();
        if (ipAddress == null)
        {
            return createBasicHTML(TITLE, "An IP address could not be detected. Please ensure you are connecting using IPv4.");
        }
        final PlexPlayer player = DataUtils.getPlayerByIP(ipAddress);
        if (player == null)
        {
            return createBasicHTML(TITLE, "Couldn't load your IP Address: " + ipAddress + ". Have you joined the server before?");
        }
        if (Plex.get().getSystem().equalsIgnoreCase("ranks"))
        {
            PlexLog.debug("Plex-HTTPD using ranks check");
            if (!player.getRankFromString().isAtLeast(Rank.ADMIN))
            {
                return createBasicHTML(TITLE, "Not a high enough rank to view this page.");
            }
        }
        else if (Plex.get().getSystem().equalsIgnoreCase("permissions"))
        {
            PlexLog.debug("Plex-HTTPD using permissions check");
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUuid());
            if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.indefbans.access"))
            {
                return createBasicHTML(TITLE, "Not enough permissions to view this page.");
            }
        }
        return createJSONHTML(TITLE, new GsonBuilder().setPrettyPrinting().create().toJson(Plex.get().getPunishmentManager().getIndefiniteBans().stream().toList()));
    }
}
