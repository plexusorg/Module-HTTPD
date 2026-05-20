package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.command.PlexCommand;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.JsonResponse;
import dev.plex.request.MappingHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;

public class CommandsEndpoint extends AbstractServlet
{
    private List<CommandGroup> cachedGroups;

    public CommandsEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/api/commands/")
    @MappingHeaders(headers = "content-type;application/json; charset=utf-8")
    public String getCommands(HttpServletRequest request, HttpServletResponse response)
    {
        if (cachedGroups == null)
        {
            cachedGroups = buildGroups();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groups", cachedGroups);
        return JsonResponse.json(response, body);
    }

    private List<CommandGroup> buildGroups()
    {
        final SortedMap<String, List<CommandInfo>> commandMap = new TreeMap<>();

        List<CommandInfo> plexCommands = commandMap.computeIfAbsent("Plex", k -> new ArrayList<>());
        for (PlexCommand command : module.api().commands().registeredCommands())
        {
            plexCommands.add(CommandInfo.from(command));
        }

        final CommandMap map = Bukkit.getCommandMap();
        for (Command command : map.getKnownCommands().values())
        {
            String plugin = "Bukkit";
            if (command instanceof PluginIdentifiableCommand pic)
            {
                plugin = pic.getPlugin().getName();
            }
            List<CommandInfo> pluginCommands = commandMap.computeIfAbsent(plugin, k -> new ArrayList<>());
            CommandInfo commandInfo = CommandInfo.from(command);
            if (!pluginCommands.contains(commandInfo))
            {
                pluginCommands.add(commandInfo);
            }
        }

        List<CommandGroup> groups = new ArrayList<>();
        for (String key : commandMap.keySet())
        {
            List<CommandInfo> commands = commandMap.get(key);
            commands.sort(Comparator.comparing(CommandInfo::name));
            groups.add(new CommandGroup(key, commands));
        }
        return groups;
    }

    private static String cleanPermission(String permission)
    {
        if (permission == null || permission.isBlank()) return "";
        return permission;
    }

    private static String cleanUsage(String usage)
    {
        if (usage == null || usage.isBlank()) return "";
        return usage.startsWith("/") ? usage : "/" + usage;
    }

    private record CommandGroup(String plugin, List<CommandInfo> commands)
    {
    }

    private record CommandInfo(String name, List<String> aliases, String description, String usage, String permission)
    {
        private static CommandInfo from(PlexCommand command)
        {
            List<String> aliases = command.getAliases() == null ? List.of() : command.getAliases();
            return new CommandInfo(command.getName(), aliases, command.getDescription(), cleanUsage(command.getUsage()), cleanPermission(command.getPermission()));
        }

        private static CommandInfo from(Command command)
        {
            List<String> aliases = command.getAliases() == null ? List.of() : command.getAliases();
            return new CommandInfo(command.getName(), aliases, command.getDescription(), cleanUsage(command.getUsage()), cleanPermission(command.getPermission()));
        }
    }
}
