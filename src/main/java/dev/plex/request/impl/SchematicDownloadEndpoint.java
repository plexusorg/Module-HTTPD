package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

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
        File[] alphabetical = worldeditFolder.listFiles();
        if (alphabetical != null)
        {
            Arrays.sort(alphabetical);
            for (File worldeditFile : alphabetical)
            {
                String sanitizedName = worldeditFile.getName().replaceAll("<","&lt;").replaceAll(">","&gt;");
                sb.append("<tr>" +
                        "<th scope=\"row\"><a href=\"" + worldeditFile.getName() + "\" download>" + sanitizedName + "</a></th>" +
                        "<td>" + formattedSize(worldeditFile.length()) + "</td>" +
                        "</tr>");
            }
            file = file.replace("${schematics}", sb.toString());
        }
        return file;
    }
}