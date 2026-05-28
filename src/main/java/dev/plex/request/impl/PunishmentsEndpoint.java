package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.api.player.PlexPlayerView;
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

public class PunishmentsEndpoint extends AbstractServlet
{
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
            UUID pathUUID = UUID.fromString(request.getPathInfo().replace("/", ""));
            punishedPlayer = module.api().players().player(pathUUID).orElse(null);
        }
        catch (IllegalArgumentException ignored)
        {
            punishedPlayer = module.api().players().byName(request.getPathInfo().replace("/", "")).orElse(null);
        }

        if (punishedPlayer == null)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_NOT_FOUND, "This player has never joined the server before.");
        }

        AuthenticatedUser viewer = currentStaff(request);
        List<?> punishments;
        if (viewer == null)
        {
            punishments = punishedPlayer.punishments().stream().map(punishment -> serialize(punishment, true)).toList();
        }
        else
        {
            punishments = punishedPlayer.punishments().stream().map(punishment -> serialize(punishment, false)).toList();
        }

        Map<String, Object> player = new LinkedHashMap<>();
        player.put("uuid", punishedPlayer.uuid());
        player.put("name", punishedPlayer.name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("player", player);
        body.put("punishments", punishments);
        body.put("canViewIps", viewer != null);
        return JsonResponse.json(response, body);
    }

    private static Object serialize(PunishmentView punishment, boolean hideIp)
    {
        return new Object()
        {
            public final UUID punished = punishment.punished();
            public final UUID punisher = punishment.punisher();
            public final Object source = punishment.source();
            public final String punisherReference = punishment.punisherReference();
            public final String punisherDisplayName = punishment.punisherDisplayName();
            public final String ip = hideIp ? "" : punishment.ip();
            public final Object type = punishment.type();
            public final String reason = punishment.reason();
            public final boolean customTime = punishment.customTime();
            public final boolean active = punishment.active();
            public final ZonedDateTime issueDate = punishment.issueDate();
            public final ZonedDateTime endDate = punishment.endDate();
        };
    }

}
