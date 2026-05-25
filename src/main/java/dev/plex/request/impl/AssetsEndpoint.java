package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class AssetsEndpoint extends AbstractServlet
{
    private static final Pattern TEXTURE_PATH = Pattern.compile("((item|block)/[a-z0-9_]+|entity/shield/[a-z0-9_]+)\\.png");
    private static final Pattern MODEL_PATH = Pattern.compile("(item|block)/[a-z0-9_]+\\.json");
    private static final Pattern ITEM_DEF_PATH = Pattern.compile("[a-z0-9_]+\\.json");

    public AssetsEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/assets/textures/")
    @MappingHeaders(headers = {"content-type;image/png", "cache-control;public, max-age=86400"})
    public String texture(HttpServletRequest request, HttpServletResponse response)
    {
        servePathUnder(request, response, "/assets/textures/", TEXTURE_PATH, "textures");
        return null;
    }

    @GetMapping(endpoint = "/assets/models/")
    @MappingHeaders(headers = {"content-type;application/json; charset=utf-8", "cache-control;public, max-age=86400"})
    public String model(HttpServletRequest request, HttpServletResponse response)
    {
        servePathUnder(request, response, "/assets/models/", MODEL_PATH, "models");
        return null;
    }

    @GetMapping(endpoint = "/assets/items/")
    @MappingHeaders(headers = {"content-type;application/json; charset=utf-8", "cache-control;public, max-age=86400"})
    public String itemDef(HttpServletRequest request, HttpServletResponse response)
    {
        servePathUnder(request, response, "/assets/items/", ITEM_DEF_PATH, "items");
        return null;
    }

    private void servePathUnder(HttpServletRequest request, HttpServletResponse response, String urlPrefix, Pattern allowed, String cacheCategory)
    {
        String uri = request.getRequestURI();
        if (!uri.startsWith(urlPrefix))
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String rest = uri.substring(urlPrefix.length());
        if (!allowed.matcher(rest).matches())
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (currentStaff(request) == null)
        {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (serveCached(cacheCategory, rest, response))
        {
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    private boolean serveCached(String category, String relativePath, HttpServletResponse response)
    {
        if (module.getMinecraftAssetsManager() == null)
        {
            return false;
        }
        Path path = module.getMinecraftAssetsManager().resolve(category, relativePath);
        if (path == null)
        {
            return false;
        }
        try (OutputStream out = response.getOutputStream())
        {
            Files.copy(path, out);
            return true;
        }
        catch (IOException ignored)
        {
            return false;
        }
    }
}
