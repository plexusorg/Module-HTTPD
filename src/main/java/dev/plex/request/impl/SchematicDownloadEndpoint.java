package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.util.PlexLog;
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
            schematicServe(request.getPathInfo().replace("/", ""), outputStream);
            return null;
        }
    }

    private void schematicServe(String requestedSchematic, OutputStream outputStream)
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
                    }
                }
                catch (IOException ignored)
                {
                }
            }
        }
    }

    private String schematicHTML()
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/schematic_download.html"));
        File worldeditFolder = HTTPDModule.getWorldeditFolder();
        if (worldeditFolder == null)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (File worldeditFile : listFilesForFolder(worldeditFolder))
        {
            String fixedPath = worldeditFile.getPath().replace("plugins/FastAsyncWorldEdit/schematics/", "");
            fixedPath = fixedPath.replace("plugins/WorldEdit/schematics/", "");
            String sanitizedName = fixedPath.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
            sb.append("    <tr>\n" +
                    "        <th scope=\"row\">\n            <a href=\"" + fixedPath + "\" download>" + sanitizedName + "</a>\n        </th>\n" +
                    "        <td>\n            " + formattedSize(worldeditFile.length()) + "\n        </td>\n" +
                    "    </tr>\n");
        }
        file = file.replace("${schematics}", sb.toString());
        return file;
    }

    List<File> files = new ArrayList<>();

    public List<File> listFilesForFolder(final File folder)
    {
        for (File fileEntry : folder.listFiles())
        {
            if (fileEntry.isDirectory())
            {
                PlexLog.debug("Found directory");
                listFilesForFolder(fileEntry);
            }
            else
            {
                files.add(fileEntry);
            }
        }
        PlexLog.debug(files.toString());
        return files;
    }
}
