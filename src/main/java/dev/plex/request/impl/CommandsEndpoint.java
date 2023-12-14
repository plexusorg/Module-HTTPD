package dev.plex.request.impl;

import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;

public class CommandsEndpoint extends AbstractServlet
{

    private final StringBuilder list = new StringBuilder();
    private boolean loadedCommands = false;

    @GetMapping(endpoint = "/api/commands/")
    public String getCommands(HttpServletRequest request, HttpServletResponse response)
    {
        if (!loadedCommands)
        {
            final SortedMap<String, List<Command>> commandMap = new TreeMap<>();
            final CommandMap map = Bukkit.getCommandMap();
            for (Command command : map.getKnownCommands().values())
            {
                String plugin = "Bukkit";
                if (command instanceof PluginIdentifiableCommand)
                {
                    plugin = ((PluginIdentifiableCommand) command).getPlugin().getName();
                }

                List<Command> pluginCommands = commandMap.computeIfAbsent(plugin, k -> new ArrayList<>());
                if (!pluginCommands.contains(command))
                {
                    pluginCommands.add(command);
                }
            }

            for (String key : commandMap.keySet())
            {
                commandMap.get(key).sort(Comparator.comparing(Command::getName));
                StringBuilder rows = new StringBuilder();
                for (Command command : commandMap.get(key))
                {
                    String permission = command.getPermission();
                    if (command instanceof PlexCommand plexCmd)
                    {
                        CommandPermissions perms = plexCmd.getClass().getAnnotation(CommandPermissions.class);
                        if (perms != null)
                        {
                            permission = (perms.permission().isBlank() ? "N/A" : perms.permission());
                        }
                    }

                    rows.append(createRow(command.getName(), command.getAliases(), command.getDescription(), command.getUsage(), permission));
                }

                list.append(createTable(key, rows.toString())).append("\n");
            }

            loadedCommands = true;
        }

        return commandsHTML(list.toString());
    }

    private String commandsHTML(String commandsList)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/commands.html"));
        file = file.replace("${commands}", commandsList);
        return file;
    }

    private String createTable(String pluginName, String commandRows)
    {
        return "<details id=\"" + pluginName + "\"><summary>" + pluginName + "</summary>\n"
            + "<table id=\"" + pluginName + "Table\" class=\"table table-striped table-bordered\">\n"
            + "  <thead>\n  <tr>\n    <th scope=\"col\">Name (Aliases)</th>\n    "
            + "<th scope=\"col\">Description</th>\n    "
            + "<th scope=\"col\">Usage</th>\n    "
            + "<th scope=\"col\">Permission</th>\n  </tr>\n</thead>\n"
            + "<tbody>\n  " + commandRows + "\n</tbody>\n</table>\n</details>";
    }

    private String createRow(String name, List<String> aliases, String description, String usage, String permission)
    {
        return "  <tr>\n    <th scope=\"row\">" + name
            + (aliases.isEmpty() || aliases.toString().equals("[]") ? "" : " (" + String.join(", ", aliases) + ")") + "</th>\n"
            + "    <th scope=\"row\">" + description + "</th>\n"
            + "    <th scope=\"row\"><code>" + cleanUsage(usage) + "</code></th>\n"
            + "    <th scope=\"row\">" + (permission != null ? permission.replaceAll(";", "<br>") : "N/A") + "</th>\n  </tr>";
    }

    private String cleanUsage(String usage)
    {
        usage = usage.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
        if (usage.isBlank())
        {
            usage = "Not Provided";
        }
        return usage.startsWith("/") || usage.equals("Not Provided") ? usage : "/" + usage;
    }
}
