package dev.plex.request.impl;

import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class PlayersEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/players/")
    public String getPlayers(HttpServletRequest request, HttpServletResponse response)
    {
        boolean isStaff = currentStaff(request) != null;
        String file = readFile(this.getClass().getResourceAsStream("/httpd/players.html"));
        return file.replace("${IS_STAFF}", String.valueOf(isStaff));
    }
}
