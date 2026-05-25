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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchematicDownloadEndpoint extends AbstractServlet
{
    public SchematicDownloadEndpoint(HTTPDModule module)
    {
        super(module);
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

        List<Map<String, Object>> schematics = new ArrayList<>();
        for (File file : listFilesForFolder(worldeditFolder))
        {
            String name = file.getName();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("size", file.length());
            entry.put("formattedSize", formattedSize(file.length()));
            entry.put("downloadUrl", "/api/schematics/download/" + name);
            schematics.add(entry);
        }
        schematics.sort(Comparator.comparing(entry -> String.valueOf(entry.get("name")), String.CASE_INSENSITIVE_ORDER));

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

        String requestedSchematic = URLDecoder.decode(request.getPathInfo().replace("/", ""), StandardCharsets.UTF_8);
        File[] schems = worldeditFolder.listFiles();
        File schemFile = schems == null ? null : Arrays.stream(schems).filter(file -> file.getName().equals(requestedSchematic)).findFirst().orElse(null);
        if (schemFile == null)
        {
            return JsonResponse.error(response, HttpServletResponse.SC_NOT_FOUND, "Schematic not found.");
        }

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + schemFile.getName().replace("\"", "") + "\"");
        try (OutputStream outputStream = response.getOutputStream())
        {
            byte[] schemData = module.getFileCache().getFile(schemFile);
            if (schemData != null)
            {
                outputStream.write(schemData);
                logDownload(request, schemFile);
            }
        }
        catch (IOException ignored)
        {
        }
        return null;
    }

    private void logDownload(HttpServletRequest request, File schemFile)
    {
        AuthenticatedUser user = currentUser(request);
        String who = user != null ? user.username() + " (xf:" + user.userId() + ")" : request.getRemoteAddr();
        module.api().logging().info("{0} downloaded schematic {1}", who, schemFile.getName());
        Log.log("{0} downloaded schematic {1}", who, schemFile.getName());
    }

    public List<File> listFilesForFolder(final File folder)
    {
        List<File> files = new ArrayList<>();
        listFilesForFolder(folder, files);
        return files;
    }

    private void listFilesForFolder(final File folder, List<File> files)
    {
        File[] children = folder.listFiles();
        if (children == null)
        {
            return;
        }
        for (File fileEntry : children)
        {
            if (fileEntry.isDirectory())
            {
                module.api().logging().debug("Found directory");
                listFilesForFolder(fileEntry, files);
            }
            else
            {
                files.add(fileEntry);
            }
        }
    }
}
