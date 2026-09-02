package dev.plex.request.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ListEndpoint extends AbstractServlet
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ListEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/api/list/")
    @MappingHeaders(headers = "content-type;application/json")
    public String getOnlinePlayers(HttpServletRequest request, HttpServletResponse response)
    {
        return GSON.toJson(module.api().players().onlineNames());
    }
}
