package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class IndefBansEndpoint extends AbstractServlet
{
    public IndefBansEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/api/indefbans/")
    public String getBans(HttpServletRequest request, HttpServletResponse response)
    {
        AuthenticatedUser user = currentStaff(request);
        if (user == null)
        {
            return indefbansHTML(signInPrompt(request, "to view this page"));
        }

        response.setHeader("content-type", "application/json");
        return new GsonBuilder().setPrettyPrinting().create().toJson(module.api().punishments().indefiniteBans());
    }

    private String indefbansHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/indefbans.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }
}
