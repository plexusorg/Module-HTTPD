package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class AssetsEndpoint extends AbstractServlet
{
    private static final Pattern TEXTURE_PATH = Pattern.compile("((item|block)/[a-z0-9_]+|entity/shield/[a-z0-9_]+)\\.png");
    private static final Pattern MODEL_PATH = Pattern.compile("(item|block)/[a-z0-9_]+\\.json");
    private static final Pattern ITEM_DEF_PATH = Pattern.compile("[a-z0-9_]+\\.json");


    @GetMapping(endpoint = "/assets/dashboard.js")
    @MappingHeaders(headers = {"content-type;application/javascript; charset=utf-8", "cache-control;public, max-age=300"})
    public String dashboardJs(HttpServletRequest request, HttpServletResponse response)
    {
        return readFileReal(this.getClass().getResourceAsStream("/httpd/assets/dashboard.js"));
    }

    @GetMapping(endpoint = "/assets/players.js")
    @MappingHeaders(headers = {"content-type;application/javascript; charset=utf-8", "cache-control;public, max-age=300"})
    public String playersJs(HttpServletRequest request, HttpServletResponse response)
    {
        return readFileReal(this.getClass().getResourceAsStream("/httpd/assets/players.js"));
    }

    @GetMapping(endpoint = "/assets/player.js")
    @MappingHeaders(headers = {"content-type;application/javascript; charset=utf-8", "cache-control;public, max-age=300"})
    public String playerJs(HttpServletRequest request, HttpServletResponse response)
    {
        return readFileReal(this.getClass().getResourceAsStream("/httpd/assets/player.js"));
    }

    @GetMapping(endpoint = "/assets/blockrenderer.js")
    @MappingHeaders(headers = {"content-type;application/javascript; charset=utf-8", "cache-control;public, max-age=300"})
    public String blockRendererJs(HttpServletRequest request, HttpServletResponse response)
    {
        return readFileReal(this.getClass().getResourceAsStream("/httpd/assets/blockrenderer.js"));
    }

    @GetMapping(endpoint = "/assets/three.module.js")
    @MappingHeaders(headers = {"content-type;application/javascript; charset=utf-8", "cache-control;public, max-age=86400"})
    public String threeJs(HttpServletRequest request, HttpServletResponse response)
    {
        serveResource("/httpd/assets/three.module.js", response);
        return null;
    }

    @GetMapping(endpoint = "/assets/three.core.js")
    @MappingHeaders(headers = {"content-type;application/javascript; charset=utf-8", "cache-control;public, max-age=86400"})
    public String threeCoreJs(HttpServletRequest request, HttpServletResponse response)
    {
        serveResource("/httpd/assets/three.core.js", response);
        return null;
    }

    @GetMapping(endpoint = "/assets/plexlogo.webp")
    @MappingHeaders(headers = {"content-type;image/webp", "cache-control;public, max-age=86400"})
    public String plexLogo(HttpServletRequest request, HttpServletResponse response)
    {
        serveResource("/httpd/assets/plexlogo.webp", response);
        return null;
    }

    @GetMapping(endpoint = "/assets/textures/")
    @MappingHeaders(headers = {"content-type;image/png", "cache-control;public, max-age=86400"})
    public String texture(HttpServletRequest request, HttpServletResponse response)
    {
        servePathUnder(request, response, "/assets/textures/", TEXTURE_PATH, "textures", "/httpd/assets/textures/");
        return null;
    }

    @GetMapping(endpoint = "/assets/models/")
    @MappingHeaders(headers = {"content-type;application/json; charset=utf-8", "cache-control;public, max-age=86400"})
    public String model(HttpServletRequest request, HttpServletResponse response)
    {
        servePathUnder(request, response, "/assets/models/", MODEL_PATH, "models", "/httpd/assets/models/");
        return null;
    }

    @GetMapping(endpoint = "/assets/items/")
    @MappingHeaders(headers = {"content-type;application/json; charset=utf-8", "cache-control;public, max-age=86400"})
    public String itemDef(HttpServletRequest request, HttpServletResponse response)
    {
        servePathUnder(request, response, "/assets/items/", ITEM_DEF_PATH, "items", "/httpd/assets/items/");
        return null;
    }

    private static void servePathUnder(HttpServletRequest request, HttpServletResponse response, String urlPrefix, Pattern allowed, String cacheCategory, String resourcePrefix)
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
        serveResource(resourcePrefix + rest, response);
    }

    private static boolean serveCached(String category, String relativePath, HttpServletResponse response)
    {
        if (HTTPDModule.getMinecraftAssetsManager() == null)
        {
            return false;
        }
        Path path = HTTPDModule.getMinecraftAssetsManager().resolve(category, relativePath);
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
