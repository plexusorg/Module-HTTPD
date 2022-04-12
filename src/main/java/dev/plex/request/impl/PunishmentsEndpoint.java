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
import java.io.FileReader;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.LocalDateTime;
import java.util.UUID;

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
            /*StringBuilder contentBuilder = new StringBuilder();
            PlexLog.log(this.getClass().getClassLoader().getResource("punishments.html").getPath());
            try
            {
                BufferedReader in = new BufferedReader(new FileReader(this.getClass().getClassLoader().getResource("punishments.html").getFile().replace("!", "")));
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
            return contentBuilder.toString();*/
            return """
                    <!DOCTYPE html>
                    <body>
                    <div style="text-align: center;">
                        <h2 style="font-family:Helvetica">Enter the UUID or username of the player you want to check</h2>
                        <input id="test" type="text" autofocus>
                        <button type="button" onclick="redirect()">Submit</button>

                        <script>
                            function redirect() {
                                var url = document.getElementById('test').value
                                window.location = "punishments/" + url
                            }
                        </script>
                    </div>
                    </body>
                    </html>""";
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
            return "This player has never joined the server before.";
        }
        if (punishedPlayer.getPunishments().isEmpty())
        {
            return "This player has been a good boy. They have no punishments!";
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
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUuid());
            if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.punishments.access"))
            {
                // If the person doesn't have permission, don't return IPs
                return new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().peek(punishment -> punishment.setIp("")).toList());
            }
        }
        return new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().toList());
    }
}
