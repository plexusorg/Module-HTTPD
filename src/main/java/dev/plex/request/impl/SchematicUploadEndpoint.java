package dev.plex.request.impl;

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

public class SchematicUploadEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/api/schematics/upload/")
    public String uploadSchematic(HttpServletRequest request, HttpServletResponse response)
    {
        String ipAddress = request.getRemoteAddr();
        if (ipAddress == null)
        {
            return schematicsHTML("An IP address could not be detected. Please ensure you are connecting using IPv4.");
        }
        final PlexPlayer player = DataUtils.getPlayerByIP(ipAddress);
        if (player == null)
        {
            return schematicsHTML("Couldn't load your IP Address: " + ipAddress + ". Have you joined the server before?");
        }
        if (Plex.get().getSystem().equalsIgnoreCase("ranks"))
        {
            PlexLog.debug("Plex-HTTPD using ranks check");
            if (!player.getRankFromString().isAtLeast(Rank.ADMIN))
            {
                return schematicsHTML("You must be an admin or above to upload schematics.");
            }
        }
        else if (Plex.get().getSystem().equalsIgnoreCase("permissions"))
        {
            PlexLog.debug("Plex-HTTPD using permissions check");
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUuid());
            if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.schematics.upload"))
            {
                return schematicsHTML("You do not have permission to upload schematics.");
            }
        }
        return readFile(this.getClass().getResourceAsStream("/httpd/schematic_upload.html"));
    }

    private String schematicsHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/schematic_upload_bad.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }
}
