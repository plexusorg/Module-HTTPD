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
import dev.plex.util.adapter.LocalDateTimeSerializer;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class PunishmentsEndpoint extends AbstractServlet
{
    private static final String TITLE = "Punishments - Plex HTTPD";

    @GetMapping(endpoint = "/api/punishments/")
    public String getPunishments(HttpServletRequest request)
    {
        String ipAddress = request.getRemoteAddr();
        if (ipAddress == null)
        {
            return createBasicHTML(TITLE, "An IP address could not be detected. Please ensure you are connecting using IPv4.");
        }
        if (request.getPathInfo() == null)
        {
            StringBuilder contentBuilder = new StringBuilder();
            try
            {
                BufferedReader in = new BufferedReader(new InputStreamReader(Objects.requireNonNull(this.getClass().getResourceAsStream("/httpd/punishments.html"))));
                String str;
                while ((str = in.readLine()) != null)
                {
                    contentBuilder.append(str);
                }
                in.close();
            }
            catch (IOException ignored)
            {
            }
            return contentBuilder.toString();
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
            return createBasicHTML(TITLE, "This player has never joined the server before.");
        }
        if (punishedPlayer.getPunishments().isEmpty())
        {
            return createBasicHTML(TITLE, "This player has been a good boy. They have no punishments!");
        }
        if (player == null)
        {
            // If the player is null, give it to them without the IPs
            return createJSONHTML(TITLE, new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().peek(punishment -> punishment.setIp("")).toList()));
        }
        if (Plex.get().getSystem().equalsIgnoreCase("ranks"))
        {
            PlexLog.debug("Plex-HTTPD using ranks check");
            if (!player.getRankFromString().isAtLeast(Rank.ADMIN))
            {
                // Don't return IPs either if the person is not an Admin or above.
                return createJSONHTML(TITLE, new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().peek(punishment -> punishment.setIp("")).toList()));
            }
        }
        else if (Plex.get().getSystem().equalsIgnoreCase("permissions"))
        {
            PlexLog.debug("Plex-HTTPD using permissions check");
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUuid());
            if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.punishments.access"))
            {
                // If the person doesn't have permission, don't return IPs
                return createJSONHTML(TITLE, new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().peek(punishment -> punishment.setIp("")).toList()));
            }
        }
        return createJSONHTML(TITLE, new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().toList()));
    }
}
