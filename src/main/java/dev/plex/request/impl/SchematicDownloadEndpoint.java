package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.logging.Log;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SchematicDownloadEndpoint extends AbstractServlet
{
    List<File> files = new ArrayList<>();

    @GetMapping(endpoint = "/api/schematics/download/")
    public String downloadSchematic(HttpServletRequest request, HttpServletResponse response)
    {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/"))
        {
            return schematicHTML();
        }
        else
        {
            OutputStream outputStream;
            try
            {
                outputStream = response.getOutputStream();
            }
            catch (IOException e)
            {
                return null;
            }
            schematicServe(request, request.getPathInfo().replace("/", ""), outputStream);
            return null;
        }
    }

    private void schematicServe(HttpServletRequest request, String requestedSchematic, OutputStream outputStream)
    {
        File worldeditFolder = HTTPDModule.getWorldeditFolder();
        if (worldeditFolder == null)
        {
            return;
        }
        File[] schems = worldeditFolder.listFiles();
        if (schems != null)
        {
            File schemFile = Arrays.stream(schems).filter(file -> file.getName().equals(requestedSchematic)).findFirst().orElse(null);
            if (schemFile != null)
            {
                try
                {
                    byte[] schemData = HTTPDModule.fileCache.getFile(schemFile);
                    if (schemData != null)
                    {
                        outputStream.write(schemData);
                        logDownload(request, schemFile);
                    }
                }
                catch (IOException ignored)
                {
                }
            }
        }
    }

    private static void logDownload(HttpServletRequest request, File schemFile)
    {
        AuthenticatedUser user = currentUser(request);
        String who = user != null ? user.username() + " (xf:" + user.userId() + ")" : request.getRemoteAddr();
        HTTPDModule.plexApi().logging().info("{0} downloaded schematic {1}", who, schemFile.getName());
        Log.log("{0} downloaded schematic {1}", who, schemFile.getName());
    }

    private String schematicHTML()
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/schematic_download.html"));
        File worldeditFolder = HTTPDModule.getWorldeditFolder();
        if (worldeditFolder == null)
        {
            return null;
        }
        List<File> entries = listFilesForFolder(worldeditFolder);
        StringBuilder sb = new StringBuilder();
        if (entries.isEmpty())
        {
            sb.append("<tr><td colspan=\"3\" class=\"px-4 py-8 text-center text-sm text-muted-foreground\">No schematics yet.</td></tr>");
        }
        for (File worldeditFile : entries)
        {
            String fixedPath = worldeditFile.getPath()
                .replace("plugins/FastAsyncWorldEdit/schematics/", "")
                .replace("plugins/WorldEdit/schematics/", "");
            String sanitizedName = fixedPath.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
            String size = formattedSize(worldeditFile.length());
            sb.append("<tr data-name=\"").append(sanitizedName).append("\" class=\"transition-colors hover:bg-muted/40\">\n")
                .append("  <td class=\"px-4 py-2.5\"><a class=\"font-mono text-foreground hover:text-primary\" href=\"")
                .append(sanitizedName).append("\" download>").append(sanitizedName).append("</a></td>\n")
                .append("  <td class=\"px-4 py-2.5 text-right font-mono text-xs text-muted-foreground tabular\">").append(size).append("</td>\n")
                .append("  <td class=\"pr-3\"><a href=\"").append(sanitizedName).append("\" download class=\"inline-flex size-7 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground\" aria-label=\"Download\"><svg class=\"size-3.5\" aria-hidden=\"true\"><use href=\"#i-download\"/></svg></a></td>\n")
                .append("</tr>\n");
        }
        file = file.replace("${schematics}", sb.toString());
        files.clear();
        return file;
    }

    public List<File> listFilesForFolder(final File folder)
    {
        for (File fileEntry : folder.listFiles())
        {
            if (fileEntry.isDirectory())
            {
                HTTPDModule.plexApi().logging().debug("Found directory");
                listFilesForFolder(fileEntry);
            }
            else
            {
                files.add(fileEntry);
            }
        }
        return files;
    }
}
