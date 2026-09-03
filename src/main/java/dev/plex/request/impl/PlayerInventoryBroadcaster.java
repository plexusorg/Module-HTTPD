package dev.plex.request.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.plex.HTTPDModule;
import jakarta.servlet.AsyncContext;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.ScoreComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Streams a single player's live inventory + armor + offhand to staff SSE
     * subscribers. Samples on each player's entity scheduler once per second; only
 * touches UUIDs that have at least one subscriber so it stays free when
 * nobody is watching anyone.
 */
public final class PlayerInventoryBroadcaster
{
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final long REFRESH_TICKS = 20L; // 1 second
    private static final int MAX_NAME_CHARS = 256;
    private static final int MAX_LORE_LINES = 20;
    private static final int MAX_LORE_LINE_CHARS = 256;
    private static final int MAX_NBT_CHARS = 4096;
    private static final int MAX_PDC_KEYS = 64;
    private static final int MAX_PDC_KEY_CHARS = 128;

    private final HTTPDModule module;
    private final Map<UUID, String> cachedPayloads = new ConcurrentHashMap<>();
    private final Map<UUID, Object> snapshotsInProgress = new ConcurrentHashMap<>();
    private SseTransport<UUID, Void> transport;
    private ScheduledTask refreshTask;

    public PlayerInventoryBroadcaster(HTTPDModule module)
    {
        this.module = module;
    }

    public synchronized void start()
    {
        if (transport != null) return;
        int maxConnections = module.getModuleConfig().getInt("server.sse.max-connections", 32);
        int threads = module.getModuleConfig().getInt("server.sse.threads", 2);
        transport = new SseTransport<>(maxConnections, threads, "Plex-HTTPD-Inv-SSE", () -> {}, this::removeKey);
        refreshTask = module.ownTask(Bukkit.getGlobalRegionScheduler().runAtFixedRate(module.plugin(),
                task -> tick(), 1L, REFRESH_TICKS));
    }

    public synchronized void shutdown()
    {
        if (refreshTask != null)
        {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (transport != null)
        {
            transport.shutdown();
        }
        cachedPayloads.clear();
        snapshotsInProgress.clear();
    }

    public boolean atCapacity()
    {
        return transport.atCapacity();
    }

    public boolean addSubscriber(UUID uuid, AsyncContext context, PrintWriter writer)
    {
        return transport.add(uuid, context, writer, null);
    }

    public void removeSubscriber(UUID uuid, AsyncContext context)
    {
        transport.remove(uuid, context);
    }

    public String currentPayload(UUID uuid)
    {
        return cachedPayloads.getOrDefault(uuid, "{\"online\":false}");
    }

    private void tick()
    {
        SseTransport<UUID, Void> activeTransport = transport;
        if (activeTransport == null) return;
        for (UUID uuid : activeTransport.keys())
        {
            Object snapshot = new Object();
            if (snapshotsInProgress.putIfAbsent(uuid, snapshot) != null) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null)
            {
                snapshotsInProgress.remove(uuid, snapshot);
                publish(uuid, "{\"online\":false}");
                continue;
            }
            boolean scheduled = player.getScheduler().execute(module.plugin(), () ->
            {
                try
                {
                    publish(uuid, buildPayload(player));
                }
                finally
                {
                    snapshotsInProgress.remove(uuid, snapshot);
                }
            }, () ->
            {
                snapshotsInProgress.remove(uuid, snapshot);
                publish(uuid, "{\"online\":false}");
            }, 1L);
            if (!scheduled)
            {
                snapshotsInProgress.remove(uuid, snapshot);
                publish(uuid, "{\"online\":false}");
            }
        }
    }

    private void publish(UUID uuid, String json)
    {
        SseTransport<UUID, Void> activeTransport = transport;
        if (activeTransport == null || !activeTransport.hasSubscribers(uuid))
        {
            cachedPayloads.remove(uuid);
            return;
        }
        cachedPayloads.put(uuid, json);
        String frame = "data: " + json + "\n\n";
        activeTransport.publish(uuid, ignored -> frame);
    }

    private void removeKey(UUID uuid)
    {
        cachedPayloads.remove(uuid);
        snapshotsInProgress.remove(uuid);
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
        return GSON.toJson(root);
    }

    private static String limit(String value, int maxChars)
    {
        if (value == null || value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "… [Truncated " + (value.length() - maxChars) + " characters]";
    }

    private static void putLimited(Map<String, Object> map, String key, String value, int maxChars)
    {
        if (value == null || value.isEmpty()) return;
        map.put(key, limit(value, maxChars));
        if (value.length() > maxChars)
        {
            map.put(key + "Truncated", true);
            map.put(key + "TruncatedChars", value.length() - maxChars);
        }
    }

    private static void putLimited(Map<String, Object> map, String key, Component component, int maxChars)
    {
        LimitedText text = limitedPlainText(component, maxChars);
        if (text.text().isEmpty()) return;
        map.put(key, text.truncated()
            ? text.text() + "… [Truncated " + (text.totalChars() - maxChars) + " characters]"
            : text.text());
        if (text.truncated())
        {
            map.put(key + "Truncated", true);
            map.put(key + "TruncatedChars", text.totalChars() - maxChars);
        }
    }

    private static LimitedText limitedPlainText(Component component, int maxChars)
    {
        StringBuilder out = new StringBuilder(Math.min(maxChars, 256));
        int total = appendPlain(component, out, maxChars);
        return new LimitedText(out.toString(), total, total > maxChars);
    }

    private static int appendPlain(Component component, StringBuilder out, int maxChars)
    {
        int total = appendComponentValue(component, out, maxChars);
        for (Component child : component.children())
        {
            total += appendPlain(child, out, maxChars - Math.min(out.length(), maxChars));
        }
        return total;
    }

    private static int appendComponentValue(Component component, StringBuilder out, int remaining)
    {
        String value = null;
        if (component instanceof TextComponent text) value = text.content();
        else if (component instanceof TranslatableComponent translatable) value = translatable.fallback() != null ? translatable.fallback() : translatable.key();
        else if (component instanceof KeybindComponent keybind) value = keybind.keybind();
        else if (component instanceof ScoreComponent score) value = score.value() != null ? score.value() : score.name();
        else if (component instanceof SelectorComponent selector) value = selector.pattern();
        if (value == null || value.isEmpty()) return 0;
        if (remaining > 0) out.append(value, 0, Math.min(value.length(), remaining));
        return value.length();
    }

    private Map<String, Object> serializeItem(ItemStack item)
    {
        if (item == null || item.getType().isAir()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", item.getType().name());
        m.put("amount", item.getAmount());

        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        short maxDur = item.getType().getMaxDurability();
        if (maxDur > 0)
        {
            m.put("maxDamage", (int) maxDur);
            if (meta instanceof Damageable d)
            {
                m.put("damage", d.getDamage());
            }
        }

        if (meta != null)
        {
            putItemMeta(m, meta);
            putNbt(m, item);
        }
        return m;
    }

    private static void putItemMeta(Map<String, Object> item, ItemMeta meta)
    {
        Component name = meta.displayName();
        if (name != null) putLimited(item, "name", name, MAX_NAME_CHARS);
        putLore(item, meta.lore());

        Map<Enchantment, Integer> enchants = meta.getEnchants();
        if (!enchants.isEmpty())
        {
            Map<String, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<Enchantment, Integer> enchant : enchants.entrySet())
            {
                out.put(enchant.getKey().getKey().getKey(), enchant.getValue());
            }
            item.put("enchants", out);
        }

        if (meta.isUnbreakable()) item.put("unbreakable", true);

        Set<ItemFlag> flags = meta.getItemFlags();
        if (!flags.isEmpty())
        {
            List<String> out = new ArrayList<>(flags.size());
            for (ItemFlag flag : flags) out.add(flag.name());
            item.put("flags", out);
        }

        putPersistentDataKeys(item, meta.getPersistentDataContainer());
    }

    private static void putLore(Map<String, Object> item, List<Component> lore)
    {
        if (lore == null || lore.isEmpty()) return;

        int count = Math.min(lore.size(), MAX_LORE_LINES);
        List<String> out = new ArrayList<>(count);
        boolean truncated = lore.size() > MAX_LORE_LINES;
        for (int i = 0; i < count; i++)
        {
            LimitedText line = limitedPlainText(lore.get(i), MAX_LORE_LINE_CHARS);
            if (line.truncated()) truncated = true;
            out.add(line.truncated()
                ? line.text() + "… [Truncated " + (line.totalChars() - MAX_LORE_LINE_CHARS) + " characters]"
                : line.text());
        }
        item.put("lore", out);
        if (truncated) item.put("loreTruncated", true);
    }

    private static void putPersistentDataKeys(Map<String, Object> item, PersistentDataContainer pdc)
    {
        Set<NamespacedKey> keys = pdc.getKeys();
        if (keys.isEmpty()) return;

        Set<String> out = new TreeSet<>();
        boolean truncated = keys.size() > MAX_PDC_KEYS;
        int count = 0;
        for (NamespacedKey namespacedKey : keys)
        {
            if (count++ >= MAX_PDC_KEYS) break;
            String key = namespacedKey.toString();
            if (key.length() > MAX_PDC_KEY_CHARS) truncated = true;
            out.add(limit(key, MAX_PDC_KEY_CHARS));
        }
        item.put("pdcKeys", out);
        if (truncated) item.put("pdcKeysTruncated", true);
    }

    private void putNbt(Map<String, Object> itemData, ItemStack item)
    {
        try
        {
            CompoundBinaryTag tag = BinaryTagIO.reader().read(
                new ByteArrayInputStream(item.serializeAsBytes()), BinaryTagIO.Compression.GZIP);
            String snbt = TagStringIO.tagStringIO().asString(tag);
            if (snbt != null && !snbt.isEmpty() && !"{}".equals(snbt))
            {
                putLimited(itemData, "nbt", snbt, MAX_NBT_CHARS);
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Paper returned invalid NBT for an item", e);
        }
    }

    private record LimitedText(String text, int totalChars, boolean truncated) {}
}
