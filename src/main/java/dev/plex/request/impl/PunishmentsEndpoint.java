package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.cache.DataUtils;
import dev.plex.player.PlexPlayer;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.util.adapter.ZonedDateTimeAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PunishmentsEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/api/punishments/")
    public String getPunishments(HttpServletRequest request, HttpServletResponse response)
    {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/"))
        {
            return readFile(this.getClass().getResourceAsStream("/httpd/punishments.html"));
        }

        PlexPlayer punishedPlayer;
        try
        {
            UUID pathUUID = UUID.fromString(request.getPathInfo().replace("/", ""));
            punishedPlayer = DataUtils.getPlayer(pathUUID);
        }
        catch (IllegalArgumentException ignored)
        {
            punishedPlayer = DataUtils.getPlayer(request.getPathInfo().replace("/", ""));
        }

        if (punishedPlayer == null)
        {
            return punishmentsHTML("This player has never joined the server before.");
        }
        if (punishedPlayer.getPunishments().isEmpty())
        {
            return punishmentsGoodHTML("This player has been a good boy. They have no punishments!");
        }

        AuthenticatedUser viewer = currentStaff(request);
        response.setHeader("content-type", "application/json");
        if (viewer == null)
        {
            return new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).setPrettyPrinting().create().toJson(punishedPlayer.getPunishments().stream().peek(punishment -> punishment.setIp("")).toList());
        }
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
