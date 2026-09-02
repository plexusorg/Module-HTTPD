package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.api.punishment.PunishmentSource;
import dev.plex.api.punishment.PunishmentType;
import dev.plex.api.punishment.PunishmentView;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.JsonResponse;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class PunishmentsEndpoint extends AbstractServlet
{
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int LOOKUP_TIMEOUT_SECONDS = 10;

    public PunishmentsEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/api/punishments/")
    @MappingHeaders(headers = "content-type;application/json; charset=utf-8")
    public String getPunishments(HttpServletRequest request, HttpServletResponse response)
    {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/"))
        {
            return JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "Missing player UUID or username.");
        }

        PlexPlayerView punishedPlayer;
        try
        {
            punishedPlayer = lookupPlayer(request.getPathInfo().replace("/", ""));
        }
        catch (CompletionException failure)
        {
            module.api().logging().error("Failed to look up punishments target: " + failure.getMessage());
            return JsonResponse.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Player storage is temporarily unavailable.");
        }

        if (punishedPlayer == null)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_NOT_FOUND, "This player has never joined the server before.");
        }

        int offset;
        int limit;
        try
        {
            offset = nonNegativeInt(request.getParameter("offset"), 0);
            limit = nonNegativeInt(request.getParameter("limit"), DEFAULT_PAGE_SIZE);
        }
        catch (IllegalArgumentException ignored)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "offset and limit must be non-negative integers.");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "limit must be between 1 and " + MAX_PAGE_SIZE + ".");
        }

        AuthenticatedUser viewer = currentStaff(module, request);
        List<PunishmentView> source = punishedPlayer.punishments();
        int total = source.size();
        int from = Math.min(offset, total);
        int to = (int)Math.min((long)total, (long)from + limit);
        boolean hideIp = viewer == null;
        List<?> punishments = source.subList(from, to).stream()
            .map(punishment -> serialize(punishment, hideIp))
            .toList();

        Map<String, Object> player = new LinkedHashMap<>();
        player.put("uuid", punishedPlayer.uuid());
        player.put("name", punishedPlayer.name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("player", player);
        body.put("punishments", punishments);
        body.put("canViewIps", viewer != null);
        body.put("pagination", Map.of(
            "offset", from,
            "limit", limit,
            "total", total,
            "hasMore", to < total));
        return JsonResponse.json(response, body);
    }

    private PlexPlayerView lookupPlayer(String query)
    {
        try
        {
            UUID uuid = UUID.fromString(query);
            return module.api().players().player(uuid).orTimeout(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join().orElse(null);
        }
        catch (IllegalArgumentException ignored)
        {
            return module.api().players().byName(query).orTimeout(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join().orElse(null);
        }
    }

    private static int nonNegativeInt(String value, int fallback)
    {
        if (value == null || value.isBlank()) return fallback;
        int parsed = Integer.parseInt(value);
        if (parsed < 0) throw new IllegalArgumentException();
        return parsed;
    }

    private static PunishmentResponse serialize(PunishmentView punishment, boolean hideIp)
    {
        return new PunishmentResponse(
            punishment.punished(),
            punishment.punisher(),
            punishment.source(),
            punishment.punisherReference(),
            punishment.punisherDisplayName(),
            hideIp ? "" : punishment.ip(),
            punishment.type(),
            punishment.reason(),
            punishment.active(),
            punishment.issueDate(),
            punishment.endDate());
    }

    private record PunishmentResponse(
        UUID punished,
        UUID punisher,
        PunishmentSource source,
        String punisherReference,
        String punisherDisplayName,
        String ip,
        PunishmentType type,
        String reason,
        boolean active,
        ZonedDateTime issueDate,
        ZonedDateTime endDate)
    {
    }
}
