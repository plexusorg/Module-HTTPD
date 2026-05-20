package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.JsonResponse;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class IndefBansEndpoint extends AbstractServlet
{
    public IndefBansEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/api/indefbans/")
    @MappingHeaders(headers = "content-type;application/json; charset=utf-8")
    public String getBans(HttpServletRequest request, HttpServletResponse response)
    {
        AuthenticatedUser user = currentStaff(request);
        if (user == null)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_FORBIDDEN, "You must sign in as staff to view indefinite bans.");
        }

        return JsonResponse.json(response, module.api().punishments().indefiniteBans());
    }
}
