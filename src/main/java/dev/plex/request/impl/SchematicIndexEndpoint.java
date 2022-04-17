package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class SchematicIndexEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/api/list/")
    public String schematicIndex(HttpServletRequest request)
    {
    }
}
