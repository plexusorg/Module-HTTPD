package dev.plex.request.impl;

import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import dev.plex.Plex;
import dev.plex.util.PlexLog;
import jakarta.servlet.AsyncContext;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableItemNBT;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Streams a single player's live inventory + armor + offhand to staff SSE
 * subscribers. Samples on the Bukkit main thread once per second; only
 * touches UUIDs that have at least one subscriber so it stays free when
 * nobody is watching anyone.
 */
public final class PlayerInventoryBroadcaster
{
    private static final PlayerInventoryBroadcaster INSTANCE = new PlayerInventoryBroadcaster();
    private static final long REFRESH_TICKS = 20L; // 1 second
    private static final Map<String, Boolean> TEXTURE_EXISTS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> TEXTURE_RESOLVED = new ConcurrentHashMap<>();

    public static PlayerInventoryBroadcaster get()
    {
        return INSTANCE;
    }

    private final Map<UUID, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final AtomicInteger subscriberCount = new AtomicInteger();

    private ScheduledExecutorService executor;
    private BukkitTask refreshTask;
    private int maxConnections = 32;

    private PlayerInventoryBroadcaster() {}

    public synchronized void start()
    {
        if (executor != null) return;

        maxConnections = HTTPDModule.moduleConfig.getInt("server.sse.max-connections", 32);
        int threads = Math.max(1, HTTPDModule.moduleConfig.getInt("server.sse.threads", 2));

        executor = Executors.newScheduledThreadPool(threads, r ->
        {
            Thread t = new Thread(r, "Plex-HTTPD-Inv-SSE");
            t.setDaemon(true);
            return t;
        });

        try
        {
            refreshTask = Bukkit.getScheduler().runTaskTimer(
                Plex.get(), this::tick, 0L, REFRESH_TICKS);
        }
        catch (Throwable t)
        {
            PlexLog.debug("PlayerInventoryBroadcaster: could not register refresh task: " + t.getMessage());
        }

        try
        {
            NBT.preloadApi();
        }
        catch (Throwable t)
        {
            PlexLog.debug("PlayerInventoryBroadcaster: NBT-API preload failed: " + t.getMessage());
        }
    }

    public synchronized void shutdown()
    {
        if (refreshTask != null)
        {
            try { refreshTask.cancel(); } catch (Throwable ignored) {}
            refreshTask = null;
        }
        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }
        for (Set<Subscriber> set : subscribers.values())
        {
            for (Subscriber sub : set)
            {
                try { sub.ctx.complete(); } catch (Throwable ignored) {}
            }
        }
        subscribers.clear();
        subscriberCount.set(0);
    }

    public boolean atCapacity()
    {
        return subscriberCount.get() >= maxConnections;
    }

    public boolean addSubscriber(UUID uuid, AsyncContext ctx, PrintWriter writer)
    {
        if (subscriberCount.get() >= maxConnections) return false;
        Subscriber sub = new Subscriber(ctx, writer);
        Set<Subscriber> set = subscribers.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        if (set.add(sub))
        {
            subscriberCount.incrementAndGet();
            return true;
        }
        return false;
    }

    public void removeSubscriber(UUID uuid, AsyncContext ctx)
    {
        Set<Subscriber> set = subscribers.get(uuid);
        if (set == null) return;
        Subscriber match = null;
        for (Subscriber sub : set)
        {
            if (sub.ctx == ctx) { match = sub; break; }
        }
        if (match != null && set.remove(match))
        {
            subscriberCount.decrementAndGet();
            if (set.isEmpty()) subscribers.remove(uuid, set);
        }
    }

    public String currentPayload(UUID uuid)
    {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return "{\"online\":false}";
        return buildPayload(p);
    }

    // Runs on the Bukkit main thread.
    private void tick()
    {
        if (subscribers.isEmpty()) return;
        for (Map.Entry<UUID, Set<Subscriber>> entry : subscribers.entrySet())
        {
            Set<Subscriber> set = entry.getValue();
            if (set.isEmpty()) continue;
            UUID uuid = entry.getKey();
            String json;
            try
            {
                Player p = Bukkit.getPlayer(uuid);
                json = (p == null) ? "{\"online\":false}" : buildPayload(p);
            }
            catch (Throwable t)
            {
                json = "{\"online\":false}";
            }
            final String frame = "data: " + json + "\n\n";
            ScheduledExecutorService exec = executor;
            if (exec == null) return;
            for (Subscriber sub : set)
            {
                try
                {
                    exec.execute(() -> writeFrame(uuid, sub, frame));
                }
                catch (Throwable t)
                {
                    drop(uuid, sub);
                }
            }
        }
    }

    private void writeFrame(UUID uuid, Subscriber sub, String frame)
    {
        try
        {
            sub.writer.write(frame);
            sub.writer.flush();
            if (sub.writer.checkError()) drop(uuid, sub);
        }
        catch (Throwable t)
        {
            drop(uuid, sub);
        }
    }

    private void drop(UUID uuid, Subscriber sub)
    {
        Set<Subscriber> set = subscribers.get(uuid);
        if (set != null && set.remove(sub))
        {
            subscriberCount.decrementAndGet();
            if (set.isEmpty()) subscribers.remove(uuid, set);
        }
        try { sub.ctx.complete(); } catch (Throwable ignored) {}
    }

    private String buildPayload(Player p)
    {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("online", true);
        root.put("name", p.getName());

        PlayerInventory inv = p.getInventory();
        List<Map<String, Object>> hotbar = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) hotbar.add(serializeItem(inv.getItem(i)));
        List<Map<String, Object>> storage = new ArrayList<>(27);
        for (int i = 9; i < 36; i++) storage.add(serializeItem(inv.getItem(i)));

        Map<String, Object> armor = new LinkedHashMap<>();
        armor.put("helmet", serializeItem(inv.getHelmet()));
        armor.put("chest", serializeItem(inv.getChestplate()));
        armor.put("legs", serializeItem(inv.getLeggings()));
        armor.put("boots", serializeItem(inv.getBoots()));

        root.put("hotbar", hotbar);
        root.put("storage", storage);
        root.put("armor", armor);
        root.put("offhand", serializeItem(inv.getItemInOffHand()));

        return new GsonBuilder().serializeNulls().create().toJson(root);
    }

    private static Map<String, Object> serializeItem(ItemStack item)
    {
        if (item == null || item.getType().isAir()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        String type = item.getType().name();
        m.put("type", type);
        m.put("amount", item.getAmount());

        Map<String, String> texture = resolveTextures(item.getType());
        if (texture != null && !texture.isEmpty()) m.put("texture", texture);

        try
        {
            short maxDur = item.getType().getMaxDurability();
            if (maxDur > 0)
            {
                m.put("maxDamage", (int) maxDur);
                if (item.hasItemMeta() && item.getItemMeta() instanceof Damageable d)
                {
                    m.put("damage", d.getDamage());
                }
            }
        }
        catch (Throwable ignored) {}

        if (item.hasItemMeta())
        {
            ItemMeta meta = item.getItemMeta();
            try
            {
                Component name = meta.displayName();
                if (name != null) m.put("name", PlainTextComponentSerializer.plainText().serialize(name));
            }
            catch (Throwable ignored) {}
            try
            {
                List<Component> lore = meta.lore();
                if (lore != null && !lore.isEmpty())
                {
                    List<String> out = new ArrayList<>(lore.size());
                    for (Component c : lore)
                    {
                        out.add(PlainTextComponentSerializer.plainText().serialize(c));
                    }
                    m.put("lore", out);
                }
            }
            catch (Throwable ignored) {}
            try
            {
                Map<Enchantment, Integer> enchants = meta.getEnchants();
                if (enchants != null && !enchants.isEmpty())
                {
                    Map<String, Integer> out = new LinkedHashMap<>();
                    for (Map.Entry<Enchantment, Integer> e : enchants.entrySet())
                    {
                        out.put(e.getKey().getKey().getKey(), e.getValue());
                    }
                    m.put("enchants", out);
                }
            }
            catch (Throwable ignored) {}
            try
            {
                if (meta.isUnbreakable()) m.put("unbreakable", true);
            }
            catch (Throwable ignored) {}
            try
            {
                Set<ItemFlag> flags = meta.getItemFlags();
                if (flags != null && !flags.isEmpty())
                {
                    List<String> out = new ArrayList<>(flags.size());
                    for (ItemFlag f : flags) out.add(f.name());
                    m.put("flags", out);
                }
            }
            catch (Throwable ignored) {}
            try
            {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                Set<NamespacedKey> keys = pdc.getKeys();
                if (!keys.isEmpty())
                {
                    Set<String> out = new TreeSet<>();
                    for (NamespacedKey k : keys) out.add(k.toString());
                    m.put("pdcKeys", out);
                }
            }
            catch (Throwable ignored) {}

            try
            {
                Function<ReadableItemNBT, String> toSnbt = ReadableItemNBT::toString;
                String snbt = NBT.get(item, toSnbt);
                if (snbt != null && !snbt.isEmpty() && !"{}".equals(snbt))
                {
                    m.put("nbt", snbt);
                }
            }
            catch (Throwable ignored) {}
        }
        return m;
    }

    /**
     * Resolves textures for a Material. For blocks held in 3D form (no
     * dedicated item sprite, but has block face textures) returns
     * {@code {top, side}} so the client can render an isometric cube. Items
     * with a dedicated item sprite — including blocks that render as 2D
     * sprites in inventory like doors and signs — return {@code {flat}}.
     * Variant blocks (slab, stairs, wall, fence, etc.) fall back to the
     * parent block's textures when no dedicated texture exists, mirroring how
     * Minecraft itself reuses the parent's faces. Results are cached per-material.
     */
    private static Map<String, String> resolveTextures(Material material)
    {
        if (material == null) return null;
        String key = material.name().toLowerCase();
        Map<String, String> cached = TEXTURE_RESOLVED.get(key);
        if (cached != null) return cached.isEmpty() ? null : cached;

        Map<String, String> result = resolveTexturesForName(material, key);

        if (result.isEmpty())
        {
            String base = stripVariantSuffix(key);
            if (base != null)
            {
                // Stone-style variants reuse the base block (cobblestone_slab → cobblestone);
                // wood variants reuse planks (oak_slab → oak_planks);
                // brick variants use the plural form (stone_brick_slab → stone_bricks).
                for (String candidate : List.of(base, base + "_planks", base + "s"))
                {
                    result = resolveTexturesForName(material, candidate);
                    if (!result.isEmpty()) break;
                }
            }
        }

        TEXTURE_RESOLVED.put(key, result);
        return result.isEmpty() ? null : result;
    }

    private static String stripVariantSuffix(String key)
    {
        String[] suffixes = {
            "_slab", "_stairs", "_wall", "_fence_gate", "_fence",
            "_pressure_plate", "_button"
        };
        for (String suffix : suffixes)
        {
            if (key.endsWith(suffix)) return key.substring(0, key.length() - suffix.length());
        }
        return null;
    }

    private static Map<String, String> resolveTexturesForName(Material material, String key)
    {
        Map<String, String> result = new LinkedHashMap<>();
        boolean hasItemSprite = textureExists("item/" + key + ".png");

        if (material.isBlock() && !hasItemSprite)
        {
            String top = pickFirstTexture(
                "block/" + key + "_top.png",
                "block/" + key + ".png",
                "block/" + key + "_side.png",
                "block/" + key + "_front.png");
            String side = pickFirstTexture(
                "block/" + key + "_side.png",
                "block/" + key + ".png",
                "block/" + key + "_front.png",
                "block/" + key + "_top.png");
            if (top != null)
            {
                result.put("top", "/assets/textures/" + top);
                result.put("side", "/assets/textures/" + (side != null ? side : top));
            }
        }

        if (result.isEmpty())
        {
            String flat = pickFirstTexture(
                "item/" + key + ".png",
                "block/" + key + ".png",
                "block/" + key + "_side.png",
                "block/" + key + "_front.png",
                "block/" + key + "_top.png");
            if (flat != null) result.put("flat", "/assets/textures/" + flat);
        }

        return result;
    }

    private static String pickFirstTexture(String... candidates)
    {
        for (String c : candidates)
        {
            if (textureExists(c)) return c;
        }
        return null;
    }

    private static boolean textureExists(String relative)
    {
        return TEXTURE_EXISTS.computeIfAbsent(relative, p ->
            PlayerInventoryBroadcaster.class.getResource("/httpd/assets/textures/" + p) != null);
    }

    private static final class Subscriber
    {
        final AsyncContext ctx;
        final PrintWriter writer;
        Subscriber(AsyncContext ctx, PrintWriter writer)
        {
            this.ctx = ctx;
            this.writer = writer;
        }
    }
}
