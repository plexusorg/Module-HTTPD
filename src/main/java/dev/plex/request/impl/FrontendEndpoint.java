package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FrontendEndpoint extends AbstractServlet
{
    private static final String APP_PREFIX = "/app/";
    private static final String RESOURCE_PREFIX = "/httpd/app/";

    public FrontendEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/app/")
    public String app(HttpServletRequest request, HttpServletResponse response)
    {
        String uri = request.getRequestURI();
        if (uri.startsWith(APP_PREFIX + "assets/"))
        {
            serveAsset(uri.substring(APP_PREFIX.length()), response);
            return null;
        }
        return indexHtml(response);
    }

    @GetMapping(endpoint = "/players/")
    public String players(HttpServletRequest request, HttpServletResponse response)
    {
        return indexHtml(response);
    }

    @GetMapping(endpoint = "/player/")
    public String player(HttpServletRequest request, HttpServletResponse response)
    {
        if (currentStaff(request) == null)
        {
            return staffOnly(request, response, "to access player admin tools");
        }
        return indexHtml(response);
    }

    @GetMapping(endpoint = "/commands/")
    public String commands(HttpServletRequest request, HttpServletResponse response)
    {
        return indexHtml(response);
    }

    @GetMapping(endpoint = "/punishments/")
    public String punishments(HttpServletRequest request, HttpServletResponse response)
    {
        return indexHtml(response);
    }

    @GetMapping(endpoint = "/indefbans/")
    public String indefBans(HttpServletRequest request, HttpServletResponse response)
    {
        if (currentStaff(request) == null)
        {
            return staffOnly(request, response, "to view this page");
        }
        return indexHtml(response);
    }

    @GetMapping(endpoint = "/schematics/")
    public String schematics(HttpServletRequest request, HttpServletResponse response)
    {
        if (requestPath(request).startsWith("/schematics/upload") && currentStaff(request) == null)
        {
            return staffOnly(request, response, "to upload schematics");
        }
        return indexHtml(response);
    }

    private String staffOnly(HttpServletRequest request, HttpServletResponse response, String action)
    {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html; charset=UTF-8");
        return "<!doctype html><meta charset=utf-8><title>Staff access required</title>"
                + "<body style=\"font-family:system-ui;padding:2rem;max-width:34rem;margin:auto\">"
                + "<h1 style=\"font-size:1.25rem\">Staff access required</h1>"
                + "<p>" + signInPrompt(request, action) + "</p>"
                + "<p><a href=\"/\">Back to overview</a></p>";
    }

    private static String requestPath(HttpServletRequest request)
    {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/") && uri.startsWith(contextPath))
        {
            uri = uri.substring(contextPath.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }

    public static String indexHtml(HttpServletResponse response)
    {
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        return readFileReal(FrontendEndpoint.class.getResourceAsStream(RESOURCE_PREFIX + "index.html"));
    }

    private static void serveAsset(String relativePath, HttpServletResponse response)
    {
        if (!isSafeAssetPath(relativePath))
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(contentType(relativePath));
        response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        try (InputStream in = FrontendEndpoint.class.getResourceAsStream(RESOURCE_PREFIX + relativePath);
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

    private static boolean isSafeAssetPath(String path)
    {
        return path != null
                && path.startsWith("assets/")
                && !path.contains("..")
                && !path.contains("\\")
                && !path.contains("%2e")
                && !path.contains("%2E");
    }

    private static String contentType(String path)
    {
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".json")) return "application/json; charset=UTF-8";
        return "application/octet-stream";
    }
}
