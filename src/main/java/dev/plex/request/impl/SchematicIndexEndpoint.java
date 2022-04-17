package dev.plex.request.impl;

import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;

public class SchematicIndexEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/api/schematics/")
    public String schematicIndex(HttpServletRequest request)
    {
        return readFile(this.getClass().getResourceAsStream("/httpd/schematic_list.html"));
    }
}
