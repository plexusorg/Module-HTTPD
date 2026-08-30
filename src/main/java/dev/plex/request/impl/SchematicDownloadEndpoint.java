package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.logging.Log;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.JsonResponse;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

public class SchematicDownloadEndpoint extends AbstractServlet
{
    private static final long LIST_CACHE_MILLIS = 5000L;

    private final Semaphore downloadSlots;
    private final long maxDownloadBytes;
    private volatile List<SchematicInfo> cachedListing = List.of();
    private volatile long cachedListingAt;

    public SchematicDownloadEndpoint(HTTPDModule module)
    {
        super(module);
        this.downloadSlots = new Semaphore(Math.max(1, module.getModuleConfig().getInt("server.limits.schematic-download-concurrency", 4)), true);
        this.maxDownloadBytes = Math.max(1L, module.getModuleConfig().getLong("server.limits.schematic-download-bytes", 25L * 1024L * 1024L));
    }

    @GetMapping(endpoint = "/api/schematics/list")
    @MappingHeaders(headers = "content-type;application/json; charset=utf-8")
    public String listSchematics(HttpServletRequest request, HttpServletResponse response)
    {
        File worldeditFolder = HTTPDModule.getWorldeditFolder();
        if (worldeditFolder == null)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "WorldEdit is not installed.");
        }

        List<SchematicInfo> schematics = cachedSchematics(worldeditFolder);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schematics", schematics);
        return JsonResponse.json(response, body);
    }

    @GetMapping(endpoint = "/api/schematics/download/")
    public String downloadSchematic(HttpServletRequest request, HttpServletResponse response)
    {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/"))
        {
            return JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "Missing schematic filename.");
        }

        File worldeditFolder = HTTPDModule.getWorldeditFolder();
        if (worldeditFolder == null)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "WorldEdit is not installed.");
        }

        String requestedSchematic;
        try
        {
            requestedSchematic = decodeSchematicName(request.getPathInfo());
        }
        catch (IllegalArgumentException ignored)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid schematic name.");
        }
        File[] schems = worldeditFolder.listFiles();
        File schemFile = schems == null ? null : Arrays.stream(schems)
                .filter(file -> file.getName().equals(requestedSchematic))
                .filter(SchematicDownloadEndpoint::isDownloadableSchematic)
                .findFirst()
                .orElse(null);
        if (schemFile == null)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_NOT_FOUND, "Schematic not found.");
        }

        long size = schemFile.length();
        if (size > maxDownloadBytes)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Schematic exceeds the download size limit.");
        }
        if (!downloadSlots.tryAcquire())
        {
            response.setHeader("Retry-After", "2");
            return JsonResponse.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Too many schematic downloads are already in progress.");
        }

        response.setContentType("application/octet-stream");
        response.setContentLengthLong(size);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + schemFile.getName().replace("\"", "") + "\"");
        try (OutputStream outputStream = response.getOutputStream())
        {
            java.nio.file.Files.copy(schemFile.toPath(), outputStream);
            logDownload(request, schemFile);
        }
        catch (IOException e)
        {
            if (!response.isCommitted())
            {
                response.reset();
                return JsonResponse.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to download schematic.");
            }
        }
        finally
        {
            downloadSlots.release();
        }
        return null;
    }

    private void logDownload(HttpServletRequest request, File schemFile)
    {
        AuthenticatedUser user = currentUser(module, request);
        String who = user != null ? user.username() + " (xf:" + user.userId() + ")" : request.getRemoteAddr();
        module.api().logging().info("{0} downloaded schematic {1}", who, schemFile.getName());
        Log.log("{0} downloaded schematic {1}", who, schemFile.getName());
    }

    private List<SchematicInfo> cachedSchematics(File folder)
    {
        long now = System.currentTimeMillis();
        List<SchematicInfo> current = cachedListing;
        if (now - cachedListingAt < LIST_CACHE_MILLIS) return current;
        synchronized (this)
        {
            now = System.currentTimeMillis();
            if (now - cachedListingAt < LIST_CACHE_MILLIS) return cachedListing;
            File[] children = folder.listFiles();
            List<SchematicInfo> refreshed = children == null ? List.of() : Arrays.stream(children)
                    .filter(SchematicDownloadEndpoint::isDownloadableSchematic)
                    .map(file -> new SchematicInfo(
                            file.getName(),
                            file.length(),
                            formattedSize(file.length()),
                            "/api/schematics/download/" + encodePathSegment(file.getName())))
                    .sorted(Comparator.comparing(SchematicInfo::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            cachedListing = refreshed;
            cachedListingAt = now;
            return refreshed;
        }
    }

    private static boolean isDownloadableSchematic(File file)
    {
        if (file == null || !java.nio.file.Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) return false;
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".schem") || name.endsWith(".schematic");
    }

    private static String encodePathSegment(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decodeSchematicName(String pathInfo)
    {
        if (pathInfo == null || pathInfo.length() < 2 || pathInfo.charAt(0) != '/' || pathInfo.indexOf('/', 1) >= 0)
        {
            throw new IllegalArgumentException();
        }
        String decoded = URLDecoder.decode(pathInfo.substring(1), StandardCharsets.UTF_8);
        if (decoded.isBlank() || decoded.indexOf('/') >= 0 || decoded.indexOf('\\') >= 0 || decoded.indexOf('\0') >= 0)
        {
            throw new IllegalArgumentException();
        }
        return decoded;
    }

    private record SchematicInfo(String name, long size, String formattedSize, String downloadUrl) {}
}
