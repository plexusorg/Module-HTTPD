package dev.plex.request.impl;

import dev.plex.Plex;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlayersEndpoint extends AbstractServlet
{
    private static volatile List<PlayerSnapshot> snapshot = Collections.emptyList();
    private static volatile int maxPlayers = 0;
    private static volatile boolean schedulerStarted = false;

    public PlayersEndpoint()
    {
        super();
        startSnapshotTask();
    }

    private static synchronized void startSnapshotTask()
    {
        if (schedulerStarted) return;
        try
        {
            Bukkit.getScheduler().runTaskTimer(Plex.get(), PlayersEndpoint::refreshSnapshot, 0L, 20L);
            schedulerStarted = true;
        }
        catch (Throwable ignored)
        {
        }
    }

    private static void refreshSnapshot()
    {
        List<PlayerSnapshot> next = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers())
        {
            next.add(PlayerSnapshot.of(p));
        }
        snapshot = List.copyOf(next);
        maxPlayers = Bukkit.getMaxPlayers();
    }

    @GetMapping(endpoint = "/players/")
    public String getPlayers(HttpServletRequest request, HttpServletResponse response)
    {
        List<PlayerSnapshot> players = snapshot;
        String cards = players.isEmpty() ? emptyState() : renderPlayerCards(players);

        String file = readFile(this.getClass().getResourceAsStream("/httpd/players.html"));
        file = file.replace("${player_count}", String.valueOf(players.size()));
        file = file.replace("${player_max}", String.valueOf(maxPlayers));
        file = file.replace("${player_cards}", cards);
        return file;
    }

    private static String renderPlayerCards(List<PlayerSnapshot> players)
    {
        StringBuilder sb = new StringBuilder();
        for (PlayerSnapshot p : players)
        {
            sb.append(renderCard(p));
        }
        return sb.toString();
    }

    private static String renderCard(PlayerSnapshot p)
    {
        String pingColor = p.ping < 80 ? "text-success" : p.ping < 200 ? "text-warning" : "text-destructive";
        String opChip = p.op
            ? "<span class=\"inline-flex h-5 items-center rounded-full bg-primary/12 px-2 text-xs text-primary\">op</span>"
            : "";
        String location = p.world.isEmpty() ? "" : "In " + p.world;
        String separator = location.isEmpty() ? "" : "<span class=\"text-foreground/30\">·</span>";

        return """
            <a href="/punishments/%s"
               class="ring-card group flex items-center gap-3 rounded-2xl bg-card p-3 transition-colors hover:bg-secondary/50"
               data-name="%s"
               title="View punishments for %s">
                <img class="size-10 rounded-lg bg-muted [image-rendering:pixelated]"
                     src="https://vzge.me/face/512/%s.png"
                     alt="" loading="lazy" width="40" height="40">
                <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2">
                        <span class="truncate text-sm font-medium">%s</span>
                        %s
                    </div>
                    <div class="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
                        <span>%s</span>
                        %s
                        <span class="tabular %s">%dms</span>
                    </div>
                </div>
            </a>
            """.formatted(p.uuid, p.name, p.name, p.uuid, p.name, opChip, location, separator, pingColor, p.ping);
    }

    private static String emptyState()
    {
        return """
            <div class="ring-card col-span-full rounded-2xl bg-card p-10 text-center">
                <svg class="mx-auto size-8 text-muted-foreground/60" aria-hidden="true"><use href="#i-users"/></svg>
                <p class="mt-3 text-sm text-muted-foreground">No players online right now.</p>
            </div>
            """;
    }

    private static String escapeHtml(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record PlayerSnapshot(UUID uuid, String name, String world, boolean op, int ping)
    {
        static PlayerSnapshot of(Player p)
        {
            int ping;
            try { ping = p.getPing(); } catch (Throwable t) { ping = 0; }
            return new PlayerSnapshot(
                    p.getUniqueId(),
                    escapeHtml(p.getName()),
                    escapeHtml(p.getWorld() != null ? p.getWorld().getName() : ""),
                    p.isOp(),
                    ping);
        }
    }
}
