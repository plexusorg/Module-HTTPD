package dev.plex.request.impl;

import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SchematicUploadEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/api/schematics/upload/")
    public String uploadSchematic(HttpServletRequest request, HttpServletResponse response)
    {
        return readFile(this.getClass().getResourceAsStream("/httpd/schematic_upload.html"));
    }
}
