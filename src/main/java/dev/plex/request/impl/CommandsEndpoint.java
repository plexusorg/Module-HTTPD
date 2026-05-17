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
    private String cachedHtml;

    @GetMapping(endpoint = "/api/commands/")
    public String getCommands(HttpServletRequest request, HttpServletResponse response)
    {
        if (cachedHtml == null)
        {
            cachedHtml = buildSections();
        }
        String file = readFile(this.getClass().getResourceAsStream("/httpd/commands.html"));
        file = file.replace("${commands}", cachedHtml);
        return file;
    }

    private static String buildSections()
    {
        final SortedMap<String, List<Command>> commandMap = new TreeMap<>();
        final CommandMap map = Bukkit.getCommandMap();
        for (Command command : map.getKnownCommands().values())
        {
            String plugin = "Bukkit";
            if (command instanceof PluginIdentifiableCommand pic)
            {
                plugin = pic.getPlugin().getName();
            }
            List<Command> pluginCommands = commandMap.computeIfAbsent(plugin, k -> new ArrayList<>());
            if (!pluginCommands.contains(command))
            {
                pluginCommands.add(command);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String key : commandMap.keySet())
        {
            List<Command> commands = commandMap.get(key);
            commands.sort(Comparator.comparing(Command::getName));
            sb.append(renderSection(key, commands));
        }
        return sb.toString();
    }

    private static String renderSection(String plugin, List<Command> commands)
    {
        StringBuilder cards = new StringBuilder();
        for (Command c : commands)
        {
            cards.append(renderCard(c));
        }
        String name = escapeHtml(plugin);
        return """
            <details class="command-section group mt-3 first:mt-0" data-plugin="%s" open>
                <summary class="group flex cursor-pointer list-none items-center justify-between gap-3 rounded-2xl px-2 py-3 transition-colors hover:bg-muted/40 [&::-webkit-details-marker]:hidden">
                    <span class="flex items-center gap-2.5 text-lg font-medium tracking-tight">
                        <svg class="size-4 text-muted-foreground transition-transform group-open:rotate-90" aria-hidden="true"><use href="#i-arrow-right"/></svg>
                        %s
                    </span>
                    <span class="font-mono text-[11px] uppercase tracking-wider text-muted-foreground">
                        %d %s
                    </span>
                </summary>
                <div class="mt-3 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                    %s
                </div>
            </details>
            """.formatted(name, name, commands.size(), commands.size() == 1 ? "command" : "commands", cards);
    }

    private static String renderCard(Command c)
    {
        String name = escapeHtml(c.getName());
        String aliases = c.getAliases() == null || c.getAliases().isEmpty() ? "" : String.join(", ", c.getAliases());
        String description = c.getDescription() == null || c.getDescription().isBlank() ? "" : escapeHtml(c.getDescription());
        String usage = cleanUsage(c.getUsage());
        String permission = resolvePermission(c);

        String aliasMarkup = aliases.isEmpty()
            ? ""
            : "<span class=\"font-mono text-xs text-muted-foreground\">/ " + escapeHtml(aliases) + "</span>";

        String descMarkup = description.isEmpty()
            ? "<p class=\"mt-2 text-sm text-muted-foreground/70 italic\">No description provided.</p>"
            : "<p class=\"mt-2 text-sm text-muted-foreground\">" + description + "</p>";

        String searchBlob = (name + " " + aliases + " " + description + " " + permission).toLowerCase();

        return """
            <article class="ring-card group flex flex-col rounded-2xl bg-card p-4 transition-colors hover:bg-secondary/50" data-search="%s">
                <header class="flex flex-wrap items-baseline gap-2">
                    <code class="rounded-md bg-muted px-2 py-0.5 font-mono text-sm font-medium text-foreground">/%s</code>
                    %s
                </header>
                %s
                <dl class="mt-3 grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1.5 border-t border-border/60 pt-3 font-mono text-[11px]">
                    <dt class="text-muted-foreground uppercase tracking-wider">usage</dt>
                    <dd class="text-foreground/80 break-all">%s</dd>
                    <dt class="text-muted-foreground uppercase tracking-wider">perm</dt>
                    <dd class="text-foreground/80 break-all">%s</dd>
                </dl>
            </article>
            """.formatted(searchBlob, name, aliasMarkup, descMarkup, usage, permission);
    }

    private static String resolvePermission(Command c)
    {
        String permission = c.getPermission();
        if (c instanceof PlexCommand plexCmd)
        {
            CommandPermissions perms = plexCmd.getClass().getAnnotation(CommandPermissions.class);
            if (perms != null)
            {
                permission = perms.permission().isBlank() ? "N/A" : perms.permission();
            }
        }
        if (permission == null || permission.isBlank()) return "N/A";
        return escapeHtml(permission).replace(";", "<br>");
    }

    private static String cleanUsage(String usage)
    {
        if (usage == null || usage.isBlank()) return "Not provided";
        String escaped = escapeHtml(usage);
        return escaped.startsWith("/") ? escaped : "/" + escaped;
    }

    private static String escapeHtml(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
