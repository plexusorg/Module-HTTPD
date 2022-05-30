package dev.plex.request.impl;

import com.google.common.collect.Lists;
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
import dev.plex.util.adapter.ZonedDateTimeSerializer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZonedDateTime;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class AdminsEndpoint extends AbstractServlet
{
    private static final String TITLE = "Admins - Plex HTTPD";


    @GetMapping(endpoint = "/api/admins/")
    @MappingHeaders(headers = "content-type;application/json")
    public String getAdmins(HttpServletRequest request, HttpServletResponse response)
    {

        GsonBuilder json = new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeSerializer()).setPrettyPrinting();

        String adminJson = json.create().toJson(Plex.get().getAdminList().getAllAdminPlayers().stream().peek(plexPlayer -> plexPlayer.setPunishments(Lists.newArrayList())).peek(plexPlayer -> plexPlayer.setNotes(Lists.newArrayList())).collect(Collectors.toList()));

        String nonAdminJson = json.create().toJson(Plex.get().getAdminList().getAllAdminPlayers().stream().peek(plexPlayer -> plexPlayer.setVanished(false)).peek(plexPlayer -> plexPlayer.setCommandSpy(false)).peek(plexPlayer -> plexPlayer.setAdminActive(false)).peek(plexPlayer -> plexPlayer.setIps(Lists.newArrayList())).peek(plexPlayer -> plexPlayer.setPunishments(Lists.newArrayList())).peek(plexPlayer -> plexPlayer.setNotes(Lists.newArrayList())).collect(Collectors.toList()));

        String ipAddress = request.getRemoteAddr();
        if (ipAddress == null)
        {
            return adminsHTML("An IP address could not be detected. Please ensure you are connecting using IPv4.");
        }
        final PlexPlayer player = DataUtils.getPlayerByIP(ipAddress);
        if (player == null)
        {
            // This likely means they've never joined the server before. That's okay. We can just not return IPs.
            return nonAdminJson;
        }
        if (Plex.get().getSystem().equalsIgnoreCase("ranks"))
        {
            PlexLog.debug("Plex-HTTPD using ranks check");
            if (!player.getRankFromString().isAtLeast(Rank.ADMIN))
            {
                // Don't return IPs either if the person is not an Admin or above.
                return nonAdminJson;
            }
        }
        else if (Plex.get().getSystem().equalsIgnoreCase("permissions"))
        {
            PlexLog.debug("Plex-HTTPD using permissions check");
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUuid());
            if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.admins.access"))
            {
                // If the person doesn't have permission, don't return IPs
                return nonAdminJson;
            }
        }
        return adminJson;
    }

    private String adminsHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/admins.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }
}
