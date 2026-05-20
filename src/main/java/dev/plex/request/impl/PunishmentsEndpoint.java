package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.api.punishment.PunishmentView;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.util.adapter.ZonedDateTimeAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PunishmentsEndpoint extends AbstractServlet
{
    public PunishmentsEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/api/punishments/")
    public String getPunishments(HttpServletRequest request, HttpServletResponse response)
    {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/"))
        {
            return readFile(this.getClass().getResourceAsStream("/httpd/punishments.html"));
        }

        PlexPlayerView punishedPlayer;
        try
        {
            UUID pathUUID = UUID.fromString(request.getPathInfo().replace("/", ""));
            punishedPlayer = module.api().players().byUuid(pathUUID).orElse(null);
        }
        catch (IllegalArgumentException ignored)
        {
            punishedPlayer = module.api().players().byName(request.getPathInfo().replace("/", "")).orElse(null);
        }

        if (punishedPlayer == null)
        {
            return punishmentsHTML("This player has never joined the server before.");
        }
        if (punishedPlayer.punishments().isEmpty())
        {
            return punishmentsGoodHTML("This player has been a good boy. They have no punishments!");
        }

        AuthenticatedUser viewer = currentStaff(request);
        response.setHeader("content-type", "application/json");
        if (viewer == null)
        {
            return new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).setPrettyPrinting().create().toJson(punishedPlayer.punishments().stream().map(PunishmentsEndpoint::hideIp).toList());
        }
        return new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).setPrettyPrinting().create().toJson(punishedPlayer.punishments().stream().toList());
    }

    private static Object hideIp(PunishmentView punishment)
    {
        return new Object()
        {
            public final UUID punished = punishment.punished();
            public final UUID punisher = punishment.punisher();
            public final String punisherName = punishment.punisherName();
            public final String ip = "";
            public final String punishedUsername = punishment.punishedUsername();
            public final Object type = punishment.type();
            public final String reason = punishment.reason();
            public final boolean customTime = punishment.customTime();
            public final boolean active = punishment.active();
            public final ZonedDateTime issueDate = punishment.issueDate();
            public final ZonedDateTime endDate = punishment.endDate();
        };
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
