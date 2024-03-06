package dev.plex.request.impl;

import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.util.PlexLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;

import java.util.*;

public class AuthenticationEndpoint extends AbstractServlet
{


    @GetMapping(endpoint = "/oauth2")
    public String login(HttpServletRequest request, HttpServletResponse response)
    {
        // TODO: Nuh uh
        return "";
    }
}
