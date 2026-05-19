package dev.plex.request.impl;

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
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

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
 * subscribers. Samples on the Bukkit main thread once per second; only
 * touches UUIDs that have at least one subscriber so it stays free when
 * nobody is watching anyone.
 */
public final class PlayerInventoryBroadcaster
{
    private static final PlayerInventoryBroadcaster INSTANCE = new PlayerInventoryBroadcaster();
    private static final long REFRESH_TICKS = 20L; // 1 second
    private static final int MAX_NAME_CHARS = 256;
    private static final int MAX_LORE_LINES = 20;
    private static final int MAX_LORE_LINE_CHARS = 256;
    private static final int MAX_NBT_CHARS = 4096;
    private static final int MAX_PDC_KEYS = 64;
    private static final int MAX_PDC_KEY_CHARS = 128;

    public static PlayerInventoryBroadcaster get()
    {
        return INSTANCE;
    }

    private final Map<UUID, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final Map<UUID, String> cachedPayloads = new ConcurrentHashMap<>();
    private final AtomicInteger subscriberCount = new AtomicInteger();

    private ScheduledExecutorService executor;
    private ScheduledTask refreshTask;
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
            refreshTask = HTTPDModule.plexApi().scheduler().runGlobalTimer(this::tick, 1L, REFRESH_TICKS);
        }
        catch (Throwable t)
        {
            HTTPDModule.plexApi().logging().debug("PlayerInventoryBroadcaster: could not register refresh task: " + t.getMessage());
        }

        try
        {
            NbtApiBridge.preload();
        }
        catch (Throwable t)
        {
            HTTPDModule.plexApi().logging().debug("PlayerInventoryBroadcaster: NBT-API preload failed: " + t.getMessage());
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
        cachedPayloads.clear();
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
        return cachedPayloads.getOrDefault(uuid, "{\"online\":false}");
    }

    // Runs on the global region and schedules per-player snapshots on entity schedulers.
    private void tick()
    {
        if (subscribers.isEmpty()) return;
        for (Map.Entry<UUID, Set<Subscriber>> entry : subscribers.entrySet())
        {
            Set<Subscriber> set = entry.getValue();
            if (set.isEmpty()) continue;
            UUID uuid = entry.getKey();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null)
            {
                publish(uuid, set, "{\"online\":false}");
                continue;
            }
            try
            {
                ScheduledTask task = HTTPDModule.plexApi().scheduler().runEntity(player, () ->
                {
                    String json;
                    try
                    {
                        json = buildPayload(player);
                    }
                    catch (Throwable t)
                    {
                        json = "{\"online\":false}";
                    }
                    publish(uuid, set, json);
                });
                if (task == null)
                {
                    publish(uuid, set, "{\"online\":false}");
                }
            }
            catch (Throwable t)
            {
                publish(uuid, set, "{\"online\":false}");
            }
        }
    }

    private void publish(UUID uuid, Set<Subscriber> set, String json)
    {
        cachedPayloads.put(uuid, json);
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

    private static Map<String, Object> serializeItem(ItemStack item)
    {
        if (item == null || item.getType().isAir()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        String type = item.getType().name();
        m.put("type", type);
        m.put("amount", item.getAmount());

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
                if (name != null) putLimited(m, "name", name, MAX_NAME_CHARS);
            }
            catch (Throwable ignored) {}
            try
            {
                List<Component> lore = meta.lore();
                if (lore != null && !lore.isEmpty())
                {
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
                    m.put("lore", out);
                    if (truncated) m.put("loreTruncated", true);
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
                    boolean truncated = keys.size() > MAX_PDC_KEYS;
                    int count = 0;
                    for (NamespacedKey k : keys)
                    {
                        if (count++ >= MAX_PDC_KEYS) break;
                        String key = k.toString();
                        if (key.length() > MAX_PDC_KEY_CHARS) truncated = true;
                        out.add(limit(key, MAX_PDC_KEY_CHARS));
                    }
                    m.put("pdcKeys", out);
                    if (truncated) m.put("pdcKeysTruncated", true);
                }
            }
            catch (Throwable ignored) {}

            try
            {
                String snbt = NbtApiBridge.toSnbt(item);
                if (snbt != null && !snbt.isEmpty() && !"{}".equals(snbt))
                {
                    putLimited(m, "nbt", snbt, MAX_NBT_CHARS);
                }
            }
            catch (Throwable ignored) {}
        }
        return m;
    }

    private record LimitedText(String text, int totalChars, boolean truncated) {}

    private static final class NbtApiBridge
    {
        private static volatile Method getMethod;
        private static volatile Method preloadMethod;
        static void preload() throws Exception
        {
            Method method = preloadMethod;
            if (method == null)
            {
                Class<?> nbt = nbtClass();
                method = nbt.getMethod("preloadApi");
                preloadMethod = method;
            }
            method.invoke(null);
        }

        static String toSnbt(ItemStack item) throws Exception
        {
            Method method = getMethod;
            if (method == null)
            {
                Class<?> nbt = nbtClass();
                method = nbt.getMethod("get", ItemStack.class, Function.class);
                getMethod = method;
            }
            Function<Object, String> stringify = Object::toString;
            Object result = method.invoke(null, item, stringify);
            return result instanceof String s ? s : null;
        }

        private static Class<?> nbtClass() throws ClassNotFoundException
        {
            return Class.forName("de.tr7zw.changeme.nbtapi.NBT", true, PlayerInventoryBroadcaster.class.getClassLoader());
        }
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
