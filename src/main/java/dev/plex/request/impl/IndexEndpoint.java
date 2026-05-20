package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class IndexEndpoint extends AbstractServlet
{
    public IndexEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "//")
    public String getIndex(HttpServletRequest request, HttpServletResponse response)
    {
        return readFile(this.getClass().getResourceAsStream("/httpd/index.html"));
    }

    @GetMapping(endpoint = "/api/")
    public String getAPI(HttpServletRequest request, HttpServletResponse response)
    {
        return readFile(this.getClass().getResourceAsStream("/httpd/index.html"));
    }
}
