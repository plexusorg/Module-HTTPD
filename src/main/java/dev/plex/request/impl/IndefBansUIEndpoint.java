package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.Plex;
import dev.plex.cache.DataUtils;
import dev.plex.player.PlexPlayer;
import dev.plex.punishment.PunishmentManager.IndefiniteBan;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class IndefBansUIEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/indefbans/")
    public String getBans(HttpServletRequest request, HttpServletResponse response)
    {
        String ipAddress = request.getRemoteAddr();
        if (ipAddress == null)
        {
            return errorHTML("Cannot detect an IP address on this request.");
        }
        PlexPlayer viewer = DataUtils.getPlayerByIP(ipAddress);
        if (viewer == null)
        {
            return errorHTML("This IP (" + escapeHtml(ipAddress) + ") is not linked to a known player.");
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(viewer.getUuid());
        if (!HTTPDModule.getPermissions().playerHas(null, offline, "plex.httpd.indefbans.access"))
        {
            return errorHTML("Your account does not have <span class=\"font-mono text-foreground\">plex.httpd.indefbans.access</span>.");
        }

        List<IndefiniteBan> bans = Plex.get().getPunishmentManager().getIndefiniteBans();
        return listHTML(bans);
    }

    private String listHTML(List<IndefiniteBan> bans)
    {
        StringBuilder cards = new StringBuilder();
        int totalUsers = 0, totalUuids = 0, totalIps = 0;
        for (IndefiniteBan ban : bans)
        {
            totalUsers += ban.getUsernames().size();
            totalUuids += ban.getUuids().size();
            totalIps += ban.getIps().size();
            cards.append(renderCard(ban));
        }
        if (cards.length() == 0)
        {
            cards.append("""
                <div class="ring-card rounded-2xl bg-card p-10 text-center">
                    <svg class="mx-auto size-8 text-muted-foreground/60" aria-hidden="true"><use href="#i-check"/></svg>
                    <p class="mt-3 text-sm text-muted-foreground">No indefinite bans configured.</p>
                </div>
                """);
        }
        String file = readFile(this.getClass().getResourceAsStream("/httpd/indefbans_list.html"));
        file = file.replace("${group_count}", String.valueOf(bans.size()));
        file = file.replace("${total_users}", String.valueOf(totalUsers));
        file = file.replace("${total_uuids}", String.valueOf(totalUuids));
        file = file.replace("${total_ips}", String.valueOf(totalIps));
        file = file.replace("${bans}", cards.toString());
        return file;
    }

    private static String renderCard(IndefiniteBan ban)
    {
        StringBuilder chips = new StringBuilder();
        for (String name : ban.getUsernames())
        {
            chips.append(chip("user", escapeHtml(name)));
        }
        for (UUID id : ban.getUuids())
        {
            chips.append(chip("uuid", id.toString()));
        }
        for (String ip : ban.getIps())
        {
            chips.append(chip("ip", escapeHtml(ip)));
        }
        String reason = (ban.getReason() == null || ban.getReason().isBlank())
            ? "<span class=\"italic text-muted-foreground/70\">No reason provided</span>"
            : escapeHtml(ban.getReason());

        int total = ban.getUsernames().size() + ban.getUuids().size() + ban.getIps().size();
        return """
            <article class="ring-card rounded-2xl bg-card p-5">
                <header class="flex flex-wrap items-baseline justify-between gap-3">
                    <p class="text-sm">%s</p>
                    <span class="font-mono text-[11px] uppercase tracking-wider text-muted-foreground">%d %s</span>
                </header>
                <div class="mt-4 flex flex-wrap gap-1.5">
                    %s
                </div>
            </article>
            """.formatted(reason, total, total == 1 ? "entry" : "entries", chips);
    }

    private static String chip(String kind, String value)
    {
        String color = switch (kind)
        {
            case "user" -> "bg-muted text-foreground";
            case "uuid" -> "bg-primary/10 text-primary";
            case "ip"   -> "bg-warning/10 text-warning";
            default     -> "bg-muted text-foreground";
        };
        String label = switch (kind)
        {
            case "user" -> "user";
            case "uuid" -> "uuid";
            case "ip"   -> "ip";
            default     -> "";
        };
        return """
            <span class="inline-flex h-7 items-center gap-1.5 rounded-full px-2.5 font-mono text-xs %s">
                <span class="text-[9px] uppercase tracking-wider opacity-60">%s</span>
                <span>%s</span>
            </span>
            """.formatted(color, label, value);
    }

    private String errorHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/indefbans.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
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
