package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import dev.plex.Plex;
import dev.plex.cache.DataUtils;
import dev.plex.player.PlexPlayer;
import dev.plex.player.PunishedPlayer;
import dev.plex.rank.enums.Rank;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.util.PlexLog;
import dev.plex.util.adapter.LocalDateTimeSerializer;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class PunishmentsEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/api/punishments/")
    public String getPunishments(HttpServletRequest request)
    {
        String ipAddress = request.getRemoteAddr();
        if (ipAddress == null)
        {
            return "An IP address could not be detected. Please ensure you are connecting using IPv4.";
        }
        if (request.getPathInfo() == null)
        {
            return "Please specify the UUID of the player you would like to check.\nExample: /api/punishments/<uuid>";
        }
        try
        {
            UUID uuid = UUID.fromString(request.getPathInfo().replace("/", ""));
            final PunishedPlayer punishedPlayer = new PunishedPlayer(uuid);
            final PlexPlayer player = DataUtils.getPlayerByIP(ipAddress);
            if (punishedPlayer.getPunishments().isEmpty())
            {
                return "This player has been a good boy. They have no punishments! Or they've never been on the server before. Take your pick.";
            }
            if (player == null)
            {
                // If the player is null, give it to them without the IPs
                return new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().peek(punishment -> punishment.setIp("")).toList());
            }
            if (Plex.get().getSystem().equalsIgnoreCase("ranks"))
            {
                PlexLog.debug("Plex-HTTPD using ranks check");
                if (!player.getRankFromString().isAtLeast(Rank.ADMIN))
                {
                    // Don't return IPs either if the person is not an Admin or above.
                    return new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().peek(punishment -> punishment.setIp("")).toList());
                }
            }
            else if (Plex.get().getSystem().equalsIgnoreCase("permissions"))
            {
                PlexLog.debug("Plex-HTTPD using permissions check");
                final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(player.getUuid()));
                if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.punishments.access"))
                {
                    // If the person doesn't have permission, don't return IPs
                    return new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().peek(punishment -> punishment.setIp("")).toList());
                }
            }
            return new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().toList());
        }
        catch (java.lang.IllegalArgumentException ignored)
        {
            return "Invalid UUID";
        }
    }

    public File getPunishmentsFile(UUID uuid)
    {
        File folder = new File(Plex.get().getDataFolder() + File.separator + "punishments");
        if (!folder.exists())
        {
            folder.mkdir();
        }

        File file = new File(folder, "" + uuid.toString() + ".json");
        if (!file.exists())
        {
            try
            {
                file.createNewFile();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return file;
    }
}
