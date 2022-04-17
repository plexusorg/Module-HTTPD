package dev.plex.request.impl;

import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Arrays;
import org.bukkit.Bukkit;

public class SchematicDownloadEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/api/schematics/download/")
    public String downloadSchematics(HttpServletRequest request)
    {
        return schematicHTML();
    }

    private String schematicHTML()
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/schematic_list.html"));
        File worldeditFolder;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit"))
        {
            worldeditFolder = new File(Bukkit.getPluginManager().getPlugin("WorldEdit").getDataFolder() + "/schematics/");
        }
        else if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit"))
        {
            worldeditFolder = new File(Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit").getDataFolder() + "/schematics/");
        }
        else
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        File[] alphabetical = worldeditFolder.listFiles();
        if (alphabetical != null)
        {
            Arrays.sort(alphabetical);
        }
        for (File worldeditFile : alphabetical)
        {
            sb.append("<tr>" +
                    "<th scope=\"row\">" + worldeditFile.getName() + "</th>" +
                    "<td>" + worldeditFile.length() + "B" + "</td>" +
                    "</tr>");
        }
        file = file.replace("${schematics}", sb.toString());
        return file;
    }
}
