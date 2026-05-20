package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SchematicUploadEndpoint extends AbstractServlet
{
    public SchematicUploadEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/api/schematics/upload/")
    public String uploadSchematic(HttpServletRequest request, HttpServletResponse response)
    {
        AuthenticatedUser user = currentStaff(request);
        if (user == null)
        {
            return schematicsHTML(signInPrompt(request, "to upload schematics"));
        }
        return readFile(this.getClass().getResourceAsStream("/httpd/schematic_upload.html"));
    }

    private String schematicsHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/schematic_upload_bad.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }
}
