package dev.plex.request.impl;

import com.google.common.collect.Lists;
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
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

//@RestController
//@RequestMapping("/api/admins")
public class GetEndpoints extends AbstractServlet
{
    @GetMapping(endpoint = "/api/admins/")
    public String getAdmins(HttpServletRequest request)
    {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null)
        {
            ipAddress = request.getRemoteAddr();
        }
        final PlexPlayer player = DataUtils.getPlayerByIP(ipAddress);
        if (player == null)
        {
            return "Couldn't load your IP Address: " + ipAddress + ". Check if your SSL settings are setup correctly.";
        }
        if (Plex.get().getSystem().equalsIgnoreCase("ranks"))
        {
            PlexLog.debug("Plex-HTTPD using ranks check");
            if (!player.getRankFromString().isAtLeast(Rank.ADMIN))
            {
                return new GsonBuilder().setPrettyPrinting().create().toJson(Plex.get().getAdminList().getAllAdminPlayers().stream().peek(plexPlayer -> plexPlayer.setIps(Lists.newArrayList())).collect(Collectors.toList()));
            }
        }
        else if (Plex.get().getSystem().equalsIgnoreCase("permissions"))
        {
            PlexLog.debug("Plex-HTTPD using permissions check");
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(player.getUuid()));
            if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.indefbans.access"))
            {
                return new GsonBuilder().setPrettyPrinting().create().toJson(Plex.get().getAdminList().getAllAdminPlayers().stream().peek(plexPlayer -> plexPlayer.setIps(Lists.newArrayList())).collect(Collectors.toList()));
            }
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(new ArrayList<>(Plex.get().getAdminList().getAllAdminPlayers()));
    }

    @GetMapping(endpoint = "/api/indefbans/")
    public String getBans(HttpServletRequest request)
    {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null)
        {
            ipAddress = request.getRemoteAddr();
        }
        final PlexPlayer player = DataUtils.getPlayerByIP(ipAddress);
        if (player == null)
        {
            return "Couldn't load your IP Address: " + ipAddress + ". Check if your SSL settings are setup correctly.";
        }
        if (Plex.get().getSystem().equalsIgnoreCase("ranks"))
        {
            PlexLog.debug("Plex-HTTPD using ranks check");
            if (!player.getRankFromString().isAtLeast(Rank.ADMIN))
            {
                return "Not a high enough rank to view this page.";
            }
        }
        else if (Plex.get().getSystem().equalsIgnoreCase("permissions"))
        {
            PlexLog.debug("Plex-HTTPD using permissions check");
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(player.getUuid()));
            if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.indefbans.access"))
            {
                return "Not enough permissions to view this page.";
            }
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(new ArrayList<>(Plex.get().getPunishmentManager().getIndefiniteBans().stream().toList()));
    }
}
