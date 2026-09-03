package dev.plex.request;

import dev.plex.HTTPDModule;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.api.punishment.PunishmentRequest;
import dev.plex.api.punishment.PunishmentSource;
import dev.plex.api.punishment.PunishmentType;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.logging.Log;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class PlayerActionServlet extends HttpServlet
{
    private static final List<String> STANDARD_ACTIONS = List.of("ban", "mute");
    private static final List<String> TEMP_ACTIONS = List.of("tempban", "tempmute", "freeze");
    private static final List<String> INVENTORY_ACTIONS = List.of("clear-inventory", "clear-selected");
    private final HTTPDModule module;

    public PlayerActionServlet(HTTPDModule module)
    {
        this.module = module;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        AuthenticatedUser staff = AbstractServlet.currentStaff(module, request);
        if (staff == null)
        {
            response.getWriter().write(JsonResponse.error(response, HttpServletResponse.SC_FORBIDDEN, "Not authorized."));
            return;
        }

        String uuidStr = request.getParameter("uuid");
        String action = request.getParameter("action");
        String reason = request.getParameter("reason");
        String durationStr = request.getParameter("duration");
        String slot = request.getParameter("slot");

        if (uuidStr == null || action == null)
        {
            response.getWriter().write(JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "Missing parameters."));
            return;
        }
        if (!STANDARD_ACTIONS.contains(action) && !TEMP_ACTIONS.contains(action) && !INVENTORY_ACTIONS.contains(action))
        {
            response.getWriter().write(JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "Unknown action."));
            return;
        }

        UUID uuid;
        try
        {
            uuid = UUID.fromString(uuidStr);
        }
        catch (IllegalArgumentException e)
        {
            response.getWriter().write(JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "Bad UUID."));
            return;
        }

        PlexPlayerView target;
        try
        {
            target = AbstractServlet.lookupPlayer(module, uuid.toString());
        }
        catch (CompletionException failure)
        {
            module.api().logging().error("Failed to look up player " + uuid + ": " + failure.getMessage());
            response.getWriter().write(JsonResponse.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "Player lookup failed."));
            return;
        }
        if (target == null)
        {
            response.getWriter().write(JsonResponse.error(response, HttpServletResponse.SC_NOT_FOUND, "Player not found."));
            return;
        }

        if (INVENTORY_ACTIONS.contains(action))
        {
            handleInventoryAction(request, response, staff, uuid, target, action, slot);
            return;
        }

        PunishmentRequest punishment;
        try
        {
            punishment = createPunishment(staff, uuid, target, action, reason, durationStr);
        }
        catch (IllegalArgumentException failure)
        {
            response.getWriter().write(JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, failure.getMessage()));
            return;
        }

        applyPunishment(request, response, staff, uuid, target, action, punishment);
    }

    private PunishmentRequest createPunishment(AuthenticatedUser staff, UUID uuid, PlexPlayerView target, String action,
                                                String reason, String duration)
    {
        String safeReason = (reason == null || reason.isBlank()) ? "No reason provided" : reason.trim();
        if (safeReason.length() > 500) safeReason = safeReason.substring(0, 500);

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime endDate = switch (action)
        {
            case "ban" -> now.plus(PunishmentType.STANDARD_BAN_DURATION);
            case "mute" -> now.plusSeconds(Math.max(1, module.api().configuration().mainConfig()
                .getInt("punishments.mute-timer", 300)));
            default -> now.plusSeconds(parseDurationSeconds(duration));
        };
        List<String> ips = target.ips();
        String ip = ips.isEmpty() ? "" : ips.getLast();
        return new PunishmentRequest(
            uuid,
            null,
            PunishmentSource.WEB,
            "xf:" + staff.userId() + ":" + staff.username(),
            ip,
            mapType(action),
            safeReason,
            endDate
        );
    }

    private void applyPunishment(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser staff,
                                 UUID uuid, PlexPlayerView target, String action, PunishmentRequest punishment)
        throws IOException
    {
        AsyncContext async = request.startAsync();
        async.setTimeout(15_000L);
        String auditIp = requestIp(request);
        try
        {
            module.api().punishments().punish(punishment).orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((ignored, failure) ->
                {
                    try
                    {
                        if (failure != null)
                        {
                            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                                ? failure.getCause() : failure;
                            module.api().logging().error("Failed to apply " + action + " to " + target.name(), cause);
                            response.getWriter().write(JsonResponse.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                "Failed to apply punishment."));
                            return;
                        }

                        module.getAccessLog().log(auditIp + " (xf:" + staff.username() + ") issued " + action + " on " + target.name() + " (" + uuid + ")");
                        response.getWriter().write(JsonResponse.ok(response, "Action completed."));
                    }
                    catch (IOException writeFailure)
                    {
                        module.api().logging().error("Failed to write player-action response", writeFailure);
                    }
                    finally
                    {
                        async.complete();
                    }
                });
        }
        catch (RuntimeException failure)
        {
            module.api().logging().error("Failed to apply " + action + " to " + target.name(), failure);
            response.getWriter().write(JsonResponse.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Failed to apply punishment."));
            async.complete();
        }
    }

    private void handleInventoryAction(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser staff, UUID uuid, PlexPlayerView target, String action, String slot)
        throws IOException
    {
        module.getAccessLog().log(requestIp(request) + " (xf:" + staff.username() + ") issued " + action + " on " + target.name() + " (" + uuid + ")" + (slot == null || slot.isBlank() ? "" : " slot " + slot));

        module.scheduler().executeGlobal(() ->
        {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null)
            {
                return;
            }
            module.scheduler().runEntity(online, () ->
            {
                PlayerInventory inventory = online.getInventory();
                if ("clear-inventory".equals(action))
                {
                    inventory.clear();
                    inventory.setArmorContents(null);
                    inventory.setItemInOffHand(null);
                    online.updateInventory();
                    return;
                }
                if ("clear-selected".equals(action))
                {
                    clearSlot(inventory, slot);
                    online.updateInventory();
                }
            });
        });

        response.getWriter().write(JsonResponse.ok(response, "Inventory action queued."));
    }

    private static String requestIp(HttpServletRequest request)
    {
        String ipAddress = request.getRemoteAddr();
        if (!"127.0.0.1".equals(ipAddress)) return ipAddress;
        String forwarded = request.getHeader("X-FORWARDED-FOR");
        return forwarded == null ? ipAddress : forwarded;
    }

    private static void clearSlot(PlayerInventory inv, String slot)
    {
        if (slot == null) return;
        if (slot.startsWith("hotbar-"))
        {
            Integer index = parseSlotIndex(slot.substring(7), 0, 8);
            if (index != null) inv.setItem(index, null);
            return;
        }
        if (slot.startsWith("storage-"))
        {
            Integer index = parseSlotIndex(slot.substring(8), 0, 26);
            if (index != null) inv.setItem(index + 9, null);
            return;
        }
        switch (slot)
        {
            case "armor-helmet" -> inv.setHelmet(null);
            case "armor-chest" -> inv.setChestplate(null);
            case "armor-legs" -> inv.setLeggings(null);
            case "armor-boots" -> inv.setBoots(null);
            case "offhand" -> inv.setItemInOffHand(null);
            default -> { }
        }
    }

    private static Integer parseSlotIndex(String value, int min, int max)
    {
        try
        {
            int index = Integer.parseInt(value);
            return index >= min && index <= max ? index : null;
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private static PunishmentType mapType(String action)
    {
        return switch (action)
        {
            case "ban" -> PunishmentType.BAN;
            case "tempban" -> PunishmentType.TEMPBAN;
            case "mute", "tempmute" -> PunishmentType.MUTE;
            case "freeze" -> PunishmentType.FREEZE;
            default -> throw new IllegalArgumentException("unknown action: " + action);
        };
    }

    private static long parseDurationSeconds(String s)
    {
        if (s == null || s.length() < 2) throw new IllegalArgumentException("Invalid duration.");
        char unit = s.charAt(s.length() - 1);
        long n;
        try { n = Long.parseLong(s.substring(0, s.length() - 1)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid duration."); }
        if (n <= 0) throw new IllegalArgumentException("Duration must be positive.");
        return switch (unit)
        {
            case 'm' -> Math.min(n, 60L * 24L * 365L) * 60L;
            case 'h' -> Math.min(n, 24L * 365L) * 3600L;
            case 'd' -> Math.min(n, 365L * 50L) * 86400L;
            default -> throw new IllegalArgumentException("Invalid duration unit.");
        };
    }
}
