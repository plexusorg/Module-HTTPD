package dev.plex.request.impl;

import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlayersEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/players/")
    public String getPlayers(HttpServletRequest request, HttpServletResponse response)
    {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        String cards = players.isEmpty() ? emptyState() : renderPlayerCards(players);

        String file = readFile(this.getClass().getResourceAsStream("/httpd/players.html"));
        file = file.replace("${player_count}", String.valueOf(players.size()));
        file = file.replace("${player_max}", String.valueOf(Bukkit.getMaxPlayers()));
        file = file.replace("${player_cards}", cards);
        return file;
    }

    private static String renderPlayerCards(Collection<? extends Player> players)
    {
        StringBuilder sb = new StringBuilder();
        for (Player p : players)
        {
            sb.append(renderCard(p));
        }
        return sb.toString();
    }

    private static String renderCard(Player p)
    {
        String uuid = p.getUniqueId().toString();
        String name = escapeHtml(p.getName());
        String gamemode = p.getGameMode().name().toLowerCase();
        String world = escapeHtml(p.getWorld().getName());
        int ping = safePing(p);
        String pingColor = ping < 80 ? "text-success" : ping < 200 ? "text-warning" : "text-destructive";
        String opChip = p.isOp()
            ? "<span class=\"inline-flex h-5 items-center rounded-full bg-primary/12 px-2 font-mono text-[10px] uppercase tracking-wider text-primary\">op</span>"
            : "";

        return """
            <article class="ring-card group rounded-2xl bg-card p-4 transition-colors hover:bg-secondary/50" data-name="%s">
                <div class="flex items-center gap-3">
                    <img class="size-12 rounded-xl bg-muted [image-rendering:pixelated]"
                         src="https://vzge.me/face/512/%s.png"
                         alt="" loading="lazy" width="48" height="48">
                    <div class="min-w-0 flex-1">
                        <div class="flex items-center gap-2">
                            <span class="truncate font-medium">%s</span>
                            %s
                        </div>
                        <div class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 font-mono text-[11px] text-muted-foreground">
                            <span class="inline-flex h-5 items-center rounded-full bg-muted px-2">%s</span>
                            <span class="text-foreground/30">·</span>
                            <span>%s</span>
                        </div>
                    </div>
                </div>
                <div class="mt-3 flex items-center justify-between border-t border-border/60 pt-3 font-mono text-[11px]">
                    <span class="text-muted-foreground">ping</span>
                    <span class="tabular %s">%dms</span>
                </div>
            </article>
            """.formatted(name, uuid, name, opChip, gamemode, world, pingColor, ping);
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

    private static int safePing(Player p)
    {
        try
        {
            return p.getPing();
        }
        catch (Throwable t)
        {
            return 0;
        }
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
