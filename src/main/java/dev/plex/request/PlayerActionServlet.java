package dev.plex.request;

import dev.plex.HTTPDModule;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.api.punishment.PunishmentRequest;
import dev.plex.api.punishment.PunishmentType;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.logging.Log;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class PlayerActionServlet extends HttpServlet
{
    private static final long FAR_FUTURE_DAYS = 365L * 50L;
    private static final List<String> PERMANENT_ACTIONS = List.of("ban", "mute");
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
        if (!PERMANENT_ACTIONS.contains(action) && !TEMP_ACTIONS.contains(action) && !INVENTORY_ACTIONS.contains(action))
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

        PlexPlayerView target = module.api().players().byUuid(uuid).orElse(null);
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

        String safeReason = (reason == null || reason.isBlank()) ? "No reason provided" : reason.trim();
        if (safeReason.length() > 500) safeReason = safeReason.substring(0, 500);

        PunishmentType type = mapType(action);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime endDate = TEMP_ACTIONS.contains(action)
            ? now.plusSeconds(parseDurationSeconds(durationStr))
            : now.plusDays(FAR_FUTURE_DAYS);

        List<String> ips = target.ips();
        String ip = ips == null || ips.isEmpty() ? "" : ips.getLast();
        PunishmentRequest punishment = new PunishmentRequest(
            uuid,
            null,
            "xf:" + staff.username(),
            ip,
            target.name(),
            type,
            safeReason,
            TEMP_ACTIONS.contains(action),
            true,
            endDate
        );

        String ipAddress = request.getRemoteAddr();
        if ("127.0.0.1".equals(ipAddress))
        {
            String forwarded = request.getHeader("X-FORWARDED-FOR");
            if (forwarded != null) ipAddress = forwarded;
        }
        Log.log(ipAddress + " (xf:" + staff.username() + ") issued " + action + " on " + target.name() + " (" + uuid + ")");

        final boolean kick = action.equals("ban") || action.equals("tempban");
        final PunishmentRequest toApply = punishment;
        module.api().scheduler().runGlobal(() ->
        {
            try
            {
                module.api().punishments().punish(target, toApply);
            }
            catch (Throwable t)
            {
                t.printStackTrace();
                return;
            }
            if (kick)
            {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null)
                {
                    module.api().scheduler().runEntity(online, () ->
                    {
                        try { online.kick(Component.text("You have been banned: " + toApply.reason())); }
                        catch (Throwable t) { t.printStackTrace(); }
                    });
                }
            }
        });

        response.getWriter().write(JsonResponse.ok(response, "Action queued."));
    }

    private void handleInventoryAction(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser staff, UUID uuid, PlexPlayerView target, String action, String slot)
        throws IOException
    {
        String ipAddress = request.getRemoteAddr();
        if ("127.0.0.1".equals(ipAddress))
        {
            String forwarded = request.getHeader("X-FORWARDED-FOR");
            if (forwarded != null) ipAddress = forwarded;
        }

        Log.log(ipAddress + " (xf:" + staff.username() + ") issued " + action + " on " + target.name() + " (" + uuid + ")" + (slot == null || slot.isBlank() ? "" : " slot " + slot));

        module.api().scheduler().runGlobal(() ->
        {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) return;
            module.api().scheduler().runEntity(online, () ->
            {
                PlayerInventory inv = online.getInventory();
                if ("clear-inventory".equals(action))
                {
                    inv.clear();
                    inv.setArmorContents(null);
                    inv.setItemInOffHand(null);
                    online.updateInventory();
                    return;
                }
                if ("clear-selected".equals(action))
                {
                    clearSlot(inv, slot);
                    online.updateInventory();
                }
            });
        });

        response.getWriter().write(JsonResponse.ok(response, "Inventory action queued."));
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
        if (s == null || s.length() < 2) return 24L * 3600L;
        char unit = s.charAt(s.length() - 1);
        long n;
        try { n = Long.parseLong(s.substring(0, s.length() - 1)); }
        catch (NumberFormatException e) { return 24L * 3600L; }
        if (n <= 0) return 24L * 3600L;
        return switch (unit)
        {
            case 'm' -> Math.min(n, 60L * 24L * 365L) * 60L;
            case 'h' -> Math.min(n, 24L * 365L) * 3600L;
            case 'd' -> Math.min(n, 365L * 50L) * 86400L;
            default -> 24L * 3600L;
        };
    }
}
