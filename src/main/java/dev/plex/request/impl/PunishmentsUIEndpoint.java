package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.Plex;
import dev.plex.cache.DataUtils;
import dev.plex.player.PlexPlayer;
import dev.plex.punishment.Punishment;
import dev.plex.punishment.PunishmentType;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class PunishmentsUIEndpoint extends AbstractServlet
{
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    @GetMapping(endpoint = "/punishments/")
    public String getPunishments(HttpServletRequest request, HttpServletResponse response)
    {
        String path = request.getPathInfo();
        if (path == null || path.equals("/"))
        {
            return readFile(this.getClass().getResourceAsStream("/httpd/punishments.html"));
        }

        String query = path.replace("/", "").trim();
        if (query.isEmpty())
        {
            return readFile(this.getClass().getResourceAsStream("/httpd/punishments.html"));
        }

        PlexPlayer punished = lookupPlayer(query);
        if (punished == null)
        {
            return errorHTML("No player found matching <span class=\"font-mono text-foreground\">" + escapeHtml(query) + "</span>.");
        }

        List<Punishment> punishments = punished.getPunishments();
        if (punishments == null || punishments.isEmpty())
        {
            return goodHTML(escapeHtml(punished.getName()) + " has no punishments on record.");
        }

        boolean showIps = canViewIps(request.getRemoteAddr());
        return resultsHTML(punished, punishments, showIps);
    }

    private static PlexPlayer lookupPlayer(String query)
    {
        try
        {
            return DataUtils.getPlayer(UUID.fromString(query));
        }
        catch (IllegalArgumentException ignored)
        {
            return DataUtils.getPlayer(query);
        }
    }

    private static boolean canViewIps(String requesterIp)
    {
        if (requesterIp == null) return false;
        PlexPlayer viewer = DataUtils.getPlayerByIP(requesterIp);
        if (viewer == null) return false;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(viewer.getUuid());
        return HTTPDModule.getPermissions().playerHas(null, offline, "plex.httpd.punishments.access");
    }

    private String resultsHTML(PlexPlayer player, List<Punishment> punishments, boolean showIps)
    {
        StringBuilder cards = new StringBuilder();
        for (Punishment p : punishments)
        {
            cards.append(renderCard(p, showIps));
        }
        String file = readFile(this.getClass().getResourceAsStream("/httpd/punishments_results.html"));
        file = file.replace("${player_name}", escapeHtml(player.getName()));
        file = file.replace("${player_uuid}", player.getUuid().toString());
        file = file.replace("${punishment_count}", String.valueOf(punishments.size()));
        file = file.replace("${punishment_label}", punishments.size() == 1 ? "punishment" : "punishments");
        file = file.replace("${punishments}", cards.toString());
        return file;
    }

    private static String renderCard(Punishment p, boolean showIps)
    {
        PunishmentType type = p.getType();
        String typeName = type == null ? "UNKNOWN" : type.name();
        String accent = accentFor(type);

        String rawReason = (p.getReason() == null || p.getReason().isBlank()) ? "" : p.getReason();
        String reason = rawReason.isEmpty() ? "<span class=\"italic text-muted-foreground/70\">No reason provided</span>" : escapeHtml(rawReason);
        String punisher = resolveName(p.getPunisher());
        String endDate = p.getEndDate() == null ? "permanent" : escapeHtml(formatDate(p.getEndDate()));

        boolean isBan = type == PunishmentType.BAN || type == PunishmentType.TEMPBAN;
        String status = "";
        String statusChip = "";
        if (isBan)
        {
            if (p.isActive())
            {
                status = "active";
                statusChip = "<span class=\"inline-flex h-5 items-center rounded-full bg-destructive/10 px-2 font-mono text-[10px] uppercase tracking-wider text-destructive\">active</span>";
            }
            else
            {
                status = "expired";
                statusChip = "<span class=\"inline-flex h-5 items-center rounded-full bg-muted px-2 font-mono text-[10px] uppercase tracking-wider text-muted-foreground\">expired</span>";
            }
        }

        String ipRow = "";
        String ipBlob = "";
        if (showIps && p.getIp() != null && !p.getIp().isBlank())
        {
            ipBlob = p.getIp();
            ipRow = """
                <dt class="text-muted-foreground uppercase tracking-wider">IP</dt>
                <dd class="text-foreground/80 break-all">%s</dd>
                """.formatted(escapeHtml(p.getIp()));
        }

        String searchBlob = escapeHtml((typeName + " " + rawReason + " " + punisher + " " + status + " " + ipBlob).toLowerCase());

        return """
            <article class="ring-card rounded-2xl bg-card p-5" data-search="%s" data-type="%s" data-status="%s">
                <header class="flex flex-wrap items-center gap-2">
                    <span class="inline-flex h-6 items-center rounded-full bg-%s/10 px-2.5 font-mono text-xs font-medium uppercase tracking-wider text-%s">%s</span>
                    %s
                </header>
                <p class="mt-3 text-sm">%s</p>
                <dl class="mt-4 grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1.5 border-t border-border/60 pt-3 font-mono text-[11px]">
                    <dt class="text-muted-foreground uppercase tracking-wider">Punisher</dt>
                    <dd class="text-foreground/80">%s</dd>
                    <dt class="text-muted-foreground uppercase tracking-wider">Expires</dt>
                    <dd class="text-foreground/80">%s</dd>
                    %s
                </dl>
            </article>
            """.formatted(searchBlob, typeName, status, accent, accent, typeName, statusChip, reason, escapeHtml(punisher), endDate, ipRow);
    }

    private static String accentFor(PunishmentType type)
    {
        if (type == null) return "muted-foreground";
        return switch (type)
        {
            case BAN, SMITE -> "destructive";
            case TEMPBAN, MUTE -> "warning";
            case KICK, FREEZE -> "primary";
        };
    }

    private static String resolveName(UUID uuid)
    {
        if (uuid == null) return "CONSOLE";
        try
        {
            String name = Plex.get().getSqlPlayerData().getNameByUUID(uuid);
            if (name != null && !name.isBlank()) return name;
        }
        catch (Throwable ignored)
        {
        }
        return uuid.toString();
    }

    private static String formatDate(ZonedDateTime date)
    {
        try
        {
            return DATE_FMT.format(date);
        }
        catch (Throwable t)
        {
            return date.toString();
        }
    }

    private String errorHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/punishments_error.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }

    private String goodHTML(String message)
    {
        String file = readFile(this.getClass().getResourceAsStream("/httpd/punishments_good.html"));
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
