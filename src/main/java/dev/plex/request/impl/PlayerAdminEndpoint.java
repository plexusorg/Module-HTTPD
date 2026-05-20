package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;

public class PlayerAdminEndpoint extends AbstractServlet
{
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    public PlayerAdminEndpoint(HTTPDModule module)
    {
        super(module);
    }

    @GetMapping(endpoint = "/player/")
    public String getPlayer(HttpServletRequest request, HttpServletResponse response)
    {
        AuthenticatedUser staff = currentStaff(request);
        if (staff == null)
        {
            return errorPage(signInPrompt(request, "to access player admin tools"));
        }

        String path = request.getPathInfo();
        String query = path == null ? "" : path.replace("/", "").trim();
        if (query.isEmpty())
        {
            return errorPage("No player specified.");
        }

        PlexPlayerView player = lookupPlayer(query);
        if (player == null)
        {
            return errorPage("No player found matching <span class=\"font-mono\">" + escapeHtml(query) + "</span>.");
        }

        String file = readFile(this.getClass().getResourceAsStream("/httpd/player.html"));
        file = file.replace("${player_uuid}", player.uuid().toString());
        file = file.replace("${player_name}", escapeHtml(player.name()));
        file = file.replace("${player_ip}", lastIp(player));
        file = file.replace("${player_first_played}", firstPlayed(player.uuid()));
        file = file.replace("${player_namemc}", "https://namemc.com/profile/" + player.uuid());
        return file;
    }

    private PlexPlayerView lookupPlayer(String query)
    {
        try
        {
            return module.api().players().byUuid(UUID.fromString(query)).orElse(null);
        }
        catch (IllegalArgumentException ignored)
        {
            return module.api().players().byName(query).orElse(null);
        }
    }

    private static String lastIp(PlexPlayerView player)
    {
        List<String> ips = player.ips();
        if (ips == null || ips.isEmpty()) return "<span class=\"text-muted-foreground\">—</span>";
        return escapeHtml(ips.getLast());
    }

    private static String firstPlayed(UUID uuid)
    {
        try
        {
            long ms = Bukkit.getOfflinePlayer(uuid).getFirstPlayed();
            if (ms <= 0) return "<span class=\"text-muted-foreground\">Never</span>";
            ZonedDateTime when = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
            return escapeHtml(DATE_FMT.format(when));
        }
        catch (Throwable t)
        {
            return "<span class=\"text-muted-foreground\">—</span>";
        }
    }

    private String errorPage(String message)
    {
        String content = """
                Player
                PLAYERS
                <section class="rise">
                    <h1 class="text-3xl font-medium tracking-tight md:text-4xl">Player</h1>
                </section>
                <section class="rise rise-1 ring-card mt-6 rounded-2xl bg-card p-6">
                    <p class="text-sm text-foreground/80">%s</p>
                    <a href="/players/" class="mt-4 inline-flex h-9 items-center gap-1.5 rounded-full bg-muted px-4 text-sm font-medium transition-colors hover:bg-secondary">← Back to players</a>
                </section>
                """.formatted(message);
        return readFile(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
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
