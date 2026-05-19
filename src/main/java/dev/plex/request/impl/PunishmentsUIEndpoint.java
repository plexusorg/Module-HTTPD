package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.api.punishment.PunishmentType;
import dev.plex.api.punishment.PunishmentView;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

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

        PlexPlayerView punished = lookupPlayer(query);
        if (punished == null)
        {
            return errorHTML("No player found matching <span class=\"font-mono text-foreground\">" + escapeHtml(query) + "</span>.");
        }

        List<? extends PunishmentView> punishments = punished.punishments();
        if (punishments == null || punishments.isEmpty())
        {
            return goodHTML(escapeHtml(punished.name()) + " has no punishments on record.");
        }

        boolean showIps = currentStaff(request) != null;
        return resultsHTML(punished, punishments, showIps);
    }

    private static PlexPlayerView lookupPlayer(String query)
    {
        try
        {
            return HTTPDModule.plexApi().players().byUuid(UUID.fromString(query)).orElse(null);
        }
        catch (IllegalArgumentException ignored)
        {
            return HTTPDModule.plexApi().players().byName(query).orElse(null);
        }
    }

    private String resultsHTML(PlexPlayerView player, List<? extends PunishmentView> punishments, boolean showIps)
    {
        StringBuilder cards = new StringBuilder();
        for (PunishmentView p : punishments)
        {
            cards.append(renderCard(p, showIps));
        }
        String file = readFile(this.getClass().getResourceAsStream("/httpd/punishments_results.html"));
        file = file.replace("${player_name}", escapeHtml(player.name()));
        file = file.replace("${player_uuid}", player.uuid().toString());
        file = file.replace("${punishment_count}", String.valueOf(punishments.size()));
        file = file.replace("${punishment_label}", punishments.size() == 1 ? "punishment" : "punishments");
        file = file.replace("${punishments}", cards.toString());
        return file;
    }

    private static String renderCard(PunishmentView p, boolean showIps)
    {
        PunishmentType type = p.type();
        String typeName = type == null ? "UNKNOWN" : type.name();
        String accent = accentFor(type);

        String rawReason = (p.reason() == null || p.reason().isBlank()) ? "" : p.reason();
        String reason = rawReason.isEmpty() ? "<span class=\"italic text-muted-foreground/70\">No reason provided</span>" : escapeHtml(rawReason);
        String punisher = resolvePunisher(p);
        String endDate = p.endDate() == null ? "permanent" : escapeHtml(formatDate(p.endDate()));

        boolean isBan = type == PunishmentType.BAN || type == PunishmentType.TEMPBAN;
        String status = "";
        String statusChip = "";
        if (isBan)
        {
            if (p.active())
            {
                status = "active";
                statusChip = "<span class=\"inline-flex h-5 items-center rounded-full bg-destructive/10 px-2 text-xs text-destructive\">Active</span>";
            }
            else
            {
                status = "expired";
                statusChip = "<span class=\"inline-flex h-5 items-center rounded-full bg-muted px-2 text-xs text-muted-foreground\">Expired</span>";
            }
        }

        String ipRow = "";
        String ipBlob = "";
        if (showIps && p.ip() != null && !p.ip().isBlank())
        {
            ipBlob = p.ip();
            ipRow = """
                <dt class="text-muted-foreground">IP</dt>
                <dd class="font-mono text-foreground/80 break-all">%s</dd>
                """.formatted(escapeHtml(p.ip()));
        }

        String searchBlob = escapeHtml((typeName + " " + rawReason + " " + punisher + " " + status + " " + ipBlob).toLowerCase());
        String typeLabel = titleCase(typeName);

        return """
            <article class="ring-card rounded-2xl bg-card p-5" data-search="%s" data-type="%s" data-status="%s">
                <header class="flex flex-wrap items-center gap-2">
                    <span class="inline-flex h-6 items-center rounded-full bg-%s/10 px-2.5 text-xs font-medium text-%s">%s</span>
                    %s
                </header>
                <p class="mt-3 text-sm">%s</p>
                <dl class="mt-4 grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1.5 border-t border-border/60 pt-3 text-xs">
                    <dt class="text-muted-foreground">Punisher</dt>
                    <dd class="text-foreground/80">%s</dd>
                    <dt class="text-muted-foreground">Expires</dt>
                    <dd class="text-foreground/80">%s</dd>
                    %s
                </dl>
            </article>
            """.formatted(searchBlob, typeName, status, accent, accent, typeLabel, statusChip, reason, escapeHtml(punisher), endDate, ipRow);
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

    private static String resolvePunisher(PunishmentView p)
    {
        if (p.punisherName() != null && !p.punisherName().isBlank()) return p.punisherName();
        UUID uuid = p.punisher();
        return uuid == null ? "CONSOLE" : uuid.toString();
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

    private static String titleCase(String s)
    {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
