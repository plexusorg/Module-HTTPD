package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.JsonResponse;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class PlayerAdminEndpoint extends AbstractServlet
{
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
    private static final int LOOKUP_TIMEOUT_SECONDS = 10;

    public PlayerAdminEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/api/player/")
    @MappingHeaders(headers = "content-type;application/json; charset=utf-8")
    public String getPlayer(HttpServletRequest request, HttpServletResponse response)
    {
        AuthenticatedUser staff = currentStaff(module, request);
        if (staff == null)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_FORBIDDEN, "You must sign in as staff to access player admin tools.");
        }

        String path = request.getPathInfo();
        String query = path == null ? "" : path.replace("/", "").trim();
        if (query.isEmpty())
        {
            return JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "No player specified.");
        }

        PlexPlayerView player;
        try
        {
            player = lookupPlayer(query);
        }
        catch (CompletionException failure)
        {
            module.api().logging().error("Failed to look up player admin target: " + failure.getMessage());
            return JsonResponse.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Player storage is temporarily unavailable.");
        }
        if (player == null)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_NOT_FOUND, "No player found matching " + query + ".");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uuid", player.uuid().toString());
        data.put("name", player.name());
        data.put("ip", lastIp(player));
        data.put("firstPlayed", firstPlayed(player.uuid()));
        data.put("nameMcUrl", "https://namemc.com/profile/" + player.uuid());
        body.put("player", data);
        return JsonResponse.json(response, body);
    }

    private PlexPlayerView lookupPlayer(String query)
    {
        try
        {
            return module.api().players().player(UUID.fromString(query)).orTimeout(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join().orElse(null);
        }
        catch (IllegalArgumentException ignored)
        {
            return module.api().players().byName(query).orTimeout(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join().orElse(null);
        }
    }

    private static String lastIp(PlexPlayerView player)
    {
        List<String> ips = player.ips();
        if (ips.isEmpty()) return null;
        return ips.getLast();
    }

    private static String firstPlayed(UUID uuid)
    {
        long ms = Bukkit.getOfflinePlayer(uuid).getFirstPlayed();
        if (ms <= 0) return null;
        ZonedDateTime when = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
        return DATE_FMT.format(when);
    }
}
