package dev.plex.request.impl;

import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AssetsEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/assets/dashboard.js")
    @MappingHeaders(headers = {"content-type;application/javascript; charset=utf-8", "cache-control;public, max-age=300"})
    public String dashboardJs(HttpServletRequest request, HttpServletResponse response)
    {
        return readFileReal(this.getClass().getResourceAsStream("/httpd/assets/dashboard.js"));
    }

    @GetMapping(endpoint = "/assets/plexlogo.webp")
    @MappingHeaders(headers = {"content-type;image/webp", "cache-control;public, max-age=86400"})
    public String plexLogo(HttpServletRequest request, HttpServletResponse response)
    {
        serveResource("/httpd/assets/plexlogo.webp", response);
        return null;
    }

    private static void serveResource(String classpathPath, HttpServletResponse response)
    {
        try (InputStream in = AssetsEndpoint.class.getResourceAsStream(classpathPath);
             OutputStream out = response.getOutputStream())
        {
            if (in == null)
            {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            in.transferTo(out);
        }
        catch (IOException ignored)
        {
        }
    }
}
