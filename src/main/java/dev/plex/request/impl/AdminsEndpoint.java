package dev.plex.request.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import dev.plex.Plex;
import dev.plex.cache.DataUtils;
import dev.plex.player.PlexPlayer;
import dev.plex.rank.enums.Rank;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.MappingHeaders;
import dev.plex.util.PlexLog;
import dev.plex.util.adapter.ZonedDateTimeAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.ZonedDateTime;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class AdminsEndpoint extends AbstractServlet
{
    private static final Gson GSON =
            new GsonBuilder()
                    .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                    .setPrettyPrinting()
                    .create();

    private List<PlexPlayer> getAuthenticatedResponse(List<PlexPlayer> admins)
    {
        return admins
                .stream().peek(plexPlayer ->
                {
                    plexPlayer.setPunishments(null);
                    plexPlayer.setNotes(null);
                    plexPlayer.setPermissions(null);
                    plexPlayer.setCommandSpy(false);
                    plexPlayer.setVanished(false);
                }).toList();
    }

    private List<PlexPlayer> getUnauthenticatedResponse(List<PlexPlayer> admins)
    {
        return getAuthenticatedResponse(admins).stream().peek(plexPlayer ->
                plexPlayer.setIps(null)).toList();
    }

    @GetMapping(endpoint = "/api/admins/")
    @MappingHeaders(headers = "content-type;application/json")
    public String getAdmins(HttpServletRequest request, HttpServletResponse response)
    {
        String ipAddress = request.getRemoteAddr();
        if (ipAddress == null)
        {
            return adminsHTML("An IP address could not be detected. Please ensure you are connecting using IPv4.");
        }
        final PlexPlayer player = DataUtils.getPlayerByIP(ipAddress);
        final List<PlexPlayer> admins = Plex.get().getAdminList().getAllAdminPlayers();
        if (player == null)
        {
            // This likely means they've never joined the server before. That's okay. We can just not return IPs.
            return GSON.toJson(getUnauthenticatedResponse(admins));
        }
        if (Plex.get().getSystem().equalsIgnoreCase("ranks"))
        {
            PlexLog.debug("Plex-HTTPD using ranks check");
            if (!player.getRankFromString().isAtLeast(Rank.ADMIN))
            {
                // Don't return IPs either if the person is not an Admin or above.
                return GSON.toJson(getUnauthenticatedResponse(admins));
            }
        }
        else if (Plex.get().getSystem().equalsIgnoreCase("permissions"))
        {
            PlexLog.debug("Plex-HTTPD using permissions check");
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUuid());
            if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.admins.access"))
            {
                // If the person doesn't have permission, don't return IPs
                return GSON.toJson(getUnauthenticatedResponse(admins));
            }
        }
        return GSON.toJson(getAuthenticatedResponse(admins));
    }

    private String adminsHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/admins.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }
}
