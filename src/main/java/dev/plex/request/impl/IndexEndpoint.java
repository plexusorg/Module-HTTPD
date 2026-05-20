package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.JsonResponse;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

public class IndexEndpoint extends AbstractServlet
{
    public IndexEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "//")
    public String getIndex(HttpServletRequest request, HttpServletResponse response)
    {
        return FrontendEndpoint.indexHtml(response);
    }

    @GetMapping(endpoint = "/api/")
    @MappingHeaders(headers = "content-type;application/json; charset=utf-8")
    public String getAPI(HttpServletRequest request, HttpServletResponse response)
    {
        return JsonResponse.json(response, Map.of(
                "name", "Plex HTTPD API",
                "routes", List.of(
                        "/oauth2/me",
                        "/api/stats/stream",
                        "/api/players/stream",
                        "/api/players/stream/staff",
                        "/api/player/{nameOrUuid}",
                        "/api/player/inventory/stream?uuid={uuid}",
                        "/api/admin/player-action",
                        "/api/commands/",
                        "/api/punishments/{nameOrUuid}",
                        "/api/indefbans/",
                        "/api/schematics/list",
                        "/api/schematics/download/{name}",
                        "/api/schematics/upload"
                )
        ));
    }
}
