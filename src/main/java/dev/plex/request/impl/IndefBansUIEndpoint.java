package dev.plex.request.impl;

import dev.plex.Plex;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.punishment.PunishmentManager.IndefiniteBan;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.UUID;

public class IndefBansUIEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/indefbans/")
    public String getBans(HttpServletRequest request, HttpServletResponse response)
    {
        AuthenticatedUser viewer = currentStaff(request);
        if (viewer == null)
        {
            return errorHTML(signInPrompt("to view this page"));
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
        String reason = (ban.getReason() == null || ban.getReason().isBlank())
            ? "<span class=\"italic text-muted-foreground/70\">No reason provided</span>"
            : escapeHtml(ban.getReason());

        int total = ban.getUsernames().size() + ban.getUuids().size() + ban.getIps().size();

        StringBuilder rows = new StringBuilder();
        if (!ban.getUsernames().isEmpty())
        {
            rows.append(renderRow("users", "text-foreground/90 break-all", ban.getUsernames().stream().map(IndefBansUIEndpoint::escapeHtml).toList()));
        }
        if (!ban.getUuids().isEmpty())
        {
            rows.append(renderRow("uuids", "text-foreground/55 break-all", ban.getUuids().stream().map(UUID::toString).toList()));
        }
        if (!ban.getIps().isEmpty())
        {
            rows.append(renderRow("ips", "text-warning break-all", ban.getIps().stream().map(IndefBansUIEndpoint::escapeHtml).toList()));
        }

        return """
            <article class="ring-card rounded-2xl bg-card p-5">
                <header class="flex flex-wrap items-baseline justify-between gap-3">
                    <p class="text-sm">%s</p>
                    <span class="font-mono text-[11px] uppercase tracking-wider text-muted-foreground">%d %s</span>
                </header>
                <dl class="mt-4 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 border-t border-border/60 pt-3 font-mono text-[11px]">
                    %s
                </dl>
            </article>
            """.formatted(reason, total, total == 1 ? "entry" : "entries", rows);
    }

    private static String renderRow(String label, String valueClasses, List<String> values)
    {
        StringBuilder items = new StringBuilder();
        for (String value : values)
        {
            items.append("<span>").append(value).append("</span>");
        }
        return """
            <dt class="text-muted-foreground uppercase tracking-wider">%s</dt>
            <dd class="flex flex-wrap gap-x-3 gap-y-1 %s">%s</dd>
            """.formatted(label, valueClasses, items);
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
