package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import dev.plex.cache.DataUtils;
import dev.plex.player.PlexPlayer;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.util.adapter.ZonedDateTimeAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class PunishmentsEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/api/punishments/")
    public String getPunishments(HttpServletRequest request, HttpServletResponse response)
    {
        String ipAddress = request.getRemoteAddr();
        if (ipAddress == null)
        {
            return punishmentsHTML("An IP address could not be detected. Please ensure you are connecting using IPv4.");
        }
        if (request.getPathInfo() == null || request.getPathInfo().equals("/"))
        {
            return readFile(this.getClass().getResourceAsStream("/httpd/punishments.html"));
        }
        UUID pathUUID;
        String pathPlexPlayer;
        PlexPlayer punishedPlayer;
        try
        {
            pathUUID = UUID.fromString(request.getPathInfo().replace("/", ""));
            punishedPlayer = DataUtils.getPlayer(pathUUID);
        }
        catch (java.lang.IllegalArgumentException ignored)
        {
            pathPlexPlayer = request.getPathInfo().replace("/", "");
            punishedPlayer = DataUtils.getPlayer(pathPlexPlayer);
        }

        final PlexPlayer player = DataUtils.getPlayerByIP(ipAddress);
        if (punishedPlayer == null)
        {
            return punishmentsHTML("This player has never joined the server before.");
        }
        if (punishedPlayer.getPunishments().isEmpty())
        {
            return punishmentsGoodHTML("This player has been a good boy. They have no punishments!");
        }
        if (player == null)
        {
            // If the player is null, give it to them without the IPs
            return new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().peek(punishment -> punishment.setIp("")).toList());
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUuid());
        if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.punishments.access"))
        {
            // If the person doesn't have permission, don't return IPs
            return new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().peek(punishment -> punishment.setIp("")).toList());
        }

        response.setHeader("content-type", "application/json");
        return new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().toList());
    }

    private String punishmentsHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/punishments_error.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }

    private String punishmentsGoodHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/punishments_good.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }
}
