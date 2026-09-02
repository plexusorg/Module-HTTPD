package dev.plex.request.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.tr7zw.nbtapi.NBT;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    private final Map<UUID, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final Map<UUID, String> cachedPayloads = new ConcurrentHashMap<>();
    private final Map<UUID, Object> snapshotsInProgress = new ConcurrentHashMap<>();
    private final AtomicInteger subscriberCount = new AtomicInteger();

    private ScheduledExecutorService executor;
    private ScheduledTask refreshTask;
    private int maxConnections = 32;
    private final AtomicBoolean nbtAvailable = new AtomicBoolean();

    public PlayerInventoryBroadcaster(HTTPDModule module)
    {
        this.module = module;
        nbtAvailable.set(loadNbtApi());
    }

    public synchronized void start()
    {
        if (executor != null) return;

        maxConnections = module.getModuleConfig().getInt("server.sse.max-connections", 32);
        int threads = Math.max(1, module.getModuleConfig().getInt("server.sse.threads", 2));

        executor = Executors.newScheduledThreadPool(threads, r ->
        {
            Thread t = new Thread(r, "Plex-HTTPD-Inv-SSE");
            t.setDaemon(true);
            return t;
        });

        refreshTask = module.scheduler().runGlobalTimer(this::tick, 1L, REFRESH_TICKS);
    }

    private boolean loadNbtApi()
    {
        if (!Bukkit.getPluginManager().isPluginEnabled("NBTAPI"))
        {
            module.api().logging().warn("NBT-API was not found; inventory NBT viewing will not be available.");
            return false;
        }
        return true;
    }

    public synchronized void shutdown()
    {
        if (refreshTask != null)
        {
            refreshTask.cancel();
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
                complete(sub.ctx);
            }
        }
        subscribers.clear();
        cachedPayloads.clear();
        snapshotsInProgress.clear();
        subscriberCount.set(0);
    }

    public boolean atCapacity()
    {
        return subscriberCount.get() >= maxConnections;
    }

    public boolean addSubscriber(UUID uuid, AsyncContext ctx, PrintWriter writer)
    {
        if (!reserveConnection()) return false;
        Subscriber sub = new Subscriber(ctx, writer);
        Set<Subscriber> set = subscribers.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        if (set.add(sub))
        {
            return true;
        }
        subscriberCount.decrementAndGet();
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
            match.pendingFrame.set(null);
            subscriberCount.decrementAndGet();
            removeEmptySet(uuid, set);
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
            Object snapshot = new Object();
            if (snapshotsInProgress.putIfAbsent(uuid, snapshot) != null) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null)
            {
                snapshotsInProgress.remove(uuid, snapshot);
                publish(uuid, set, "{\"online\":false}");
                continue;
            }
            boolean scheduled = module.scheduler().executeEntity(player, () ->
            {
                try
                {
                    publish(uuid, set, buildPayload(player));
                }
                finally
                {
                    snapshotsInProgress.remove(uuid, snapshot);
                }
            }, () ->
            {
                snapshotsInProgress.remove(uuid, snapshot);
                publish(uuid, set, "{\"online\":false}");
            }, 1L);
            if (!scheduled)
            {
                snapshotsInProgress.remove(uuid, snapshot);
                publish(uuid, set, "{\"online\":false}");
            }
        }
    }

    private void publish(UUID uuid, Set<Subscriber> set, String json)
    {
        if (set.isEmpty() || subscribers.get(uuid) != set)
        {
            cachedPayloads.remove(uuid);
            return;
        }
        cachedPayloads.put(uuid, json);
        final String frame = "data: " + json + "\n\n";
        ScheduledExecutorService exec = executor;
        if (exec == null) return;
        for (Subscriber sub : set)
        {
            enqueueFrame(uuid, sub, frame, exec);
        }
    }

    private void enqueueFrame(UUID uuid, Subscriber sub, String frame, ScheduledExecutorService exec)
    {
        sub.pendingFrame.set(frame);
        if (sub.writing.compareAndSet(false, true)) submitDrain(uuid, sub, exec);
    }

    private void submitDrain(UUID uuid, Subscriber sub, ScheduledExecutorService exec)
    {
        try
        {
            exec.execute(() -> drainFrames(uuid, sub, exec));
        }
        catch (RejectedExecutionException ignored)
        {
            sub.writing.set(false);
            drop(uuid, sub);
        }
    }

    private void drainFrames(UUID uuid, Subscriber sub, ScheduledExecutorService exec)
    {
        try
        {
            String frame;
            while ((frame = sub.pendingFrame.getAndSet(null)) != null)
            {
                Set<Subscriber> set = subscribers.get(uuid);
                if (set == null || !set.contains(sub)) return;
                sub.writer.write(frame);
                sub.writer.flush();
                if (sub.writer.checkError())
                {
                    drop(uuid, sub);
                    return;
                }
            }
        }
        finally
        {
            sub.writing.set(false);
            if (sub.pendingFrame.get() != null && sub.writing.compareAndSet(false, true))
            {
                submitDrain(uuid, sub, exec);
            }
        }
    }

    private void drop(UUID uuid, Subscriber sub)
    {
        Set<Subscriber> set = subscribers.get(uuid);
        if (set != null && set.remove(sub))
        {
            sub.pendingFrame.set(null);
            subscriberCount.decrementAndGet();
            removeEmptySet(uuid, set);
        }
        complete(sub.ctx);
    }

    private boolean reserveConnection()
    {
        int current;
        do
        {
            current = subscriberCount.get();
            if (current >= maxConnections) return false;
        }
        while (!subscriberCount.compareAndSet(current, current + 1));
        return true;
    }

    private void removeEmptySet(UUID uuid, Set<Subscriber> set)
    {
        if (set.isEmpty() && subscribers.remove(uuid, set))
        {
            cachedPayloads.remove(uuid);
            snapshotsInProgress.computeIfPresent(uuid, (key, marker) -> subscribers.containsKey(key) ? marker : null);
        }
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
        root.put("nbtAvailable", nbtAvailable.get());

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
        String type = item.getType().name();
        m.put("type", type);
        m.put("amount", item.getAmount());

        short maxDur = item.getType().getMaxDurability();
        if (maxDur > 0)
        {
            m.put("maxDamage", (int) maxDur);
            if (item.hasItemMeta() && item.getItemMeta() instanceof Damageable d)
            {
                m.put("damage", d.getDamage());
            }
        }

        if (item.hasItemMeta())
        {
            ItemMeta meta = item.getItemMeta();
            Component name = meta.displayName();
            if (name != null) putLimited(m, "name", name, MAX_NAME_CHARS);

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

            Map<Enchantment, Integer> enchants = meta.getEnchants();
            if (!enchants.isEmpty())
            {
                Map<String, Integer> out = new LinkedHashMap<>();
                for (Map.Entry<Enchantment, Integer> e : enchants.entrySet())
                {
                    out.put(e.getKey().getKey().getKey(), e.getValue());
                }
                m.put("enchants", out);
            }

            if (meta.isUnbreakable()) m.put("unbreakable", true);

            Set<ItemFlag> flags = meta.getItemFlags();
            if (!flags.isEmpty())
            {
                List<String> out = new ArrayList<>(flags.size());
                for (ItemFlag f : flags) out.add(f.name());
                m.put("flags", out);
            }

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

            if (nbtAvailable.get())
            {
                try
                {
                    String snbt = NBT.get(item, Object::toString);
                    if (snbt != null && !snbt.isEmpty() && !"{}".equals(snbt))
                    {
                        putLimited(m, "nbt", snbt, MAX_NBT_CHARS);
                    }
                }
                catch (RuntimeException e)
                {
                    if (nbtAvailable.compareAndSet(true, false))
                    {
                        module.getLogger().warn("NBT-API failed while reading an item; inventory NBT viewing has been disabled.", e);
                    }
                }
            }
        }
        return m;
    }

    private static void complete(AsyncContext context)
    {
        try
        {
            context.complete();
        }
        catch (IllegalStateException ignored)
        {
        }
    }

    private record LimitedText(String text, int totalChars, boolean truncated) {}

    private static final class Subscriber
    {
        final AsyncContext ctx;
        final PrintWriter writer;
        final AtomicReference<String> pendingFrame = new AtomicReference<>();
        final AtomicBoolean writing = new AtomicBoolean();
        Subscriber(AsyncContext ctx, PrintWriter writer)
        {
            this.ctx = ctx;
            this.writer = writer;
        }
    }
}
