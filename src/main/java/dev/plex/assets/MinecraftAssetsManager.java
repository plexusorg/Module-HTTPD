package dev.plex.assets;

import dev.plex.api.PlexApi;
import org.bukkit.Bukkit;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MinecraftAssetsManager
{
    private static final URI VERSION_MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private final Path root;
    private final Path versionFile;
    private final HttpClient client;
    private volatile boolean ready;
    private final String minecraftVersion;
    private final PlexApi api;
    private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor(r ->
    {
        Thread thread = new Thread(r, "Plex-HTTPD-Assets");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Future<?> refreshFuture;

    public MinecraftAssetsManager(Path dataFolder, PlexApi api)
    {
        this.root = dataFolder.resolve("minecraft-assets");
        this.versionFile = root.resolve("version.txt");
        this.minecraftVersion = detectMinecraftVersion();
        this.api = api;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public void refreshAsync()
    {
        refreshFuture = refreshExecutor.submit(() ->
        {
            try
            {
                refreshIfNeeded();
                ready = true;
            }
            catch (InterruptedException ignored)
            {
                Thread.currentThread().interrupt();
            }
            catch (Exception e)
            {
                api.logging().error("Unable to download Minecraft assets for HTTPD inventory view: {0}", e.getMessage());
            }
            finally
            {
                refreshExecutor.shutdown();
            }
        });
    }

    public void shutdown()
    {
        Future<?> future = refreshFuture;
        if (future != null) future.cancel(true);
        refreshExecutor.shutdownNow();
    }

    public Path resolve(String category, String relativePath)
    {
        if (!ready)
        {
            return null;
        }
        Path file = root.resolve(category).resolve(relativePath).normalize();
        Path categoryRoot = root.resolve(category).normalize();
        if (!file.startsWith(categoryRoot) || !Files.isRegularFile(file))
        {
            return null;
        }
        return file;
    }

    private void refreshIfNeeded() throws IOException, InterruptedException
    {
        Files.createDirectories(root);
        String cachedVersion = Files.exists(versionFile) ? Files.readString(versionFile).trim() : "";
        if (minecraftVersion.equals(cachedVersion) && hasAssets())
        {
            api.logging().debug("HTTPD Minecraft assets are already cached for {0}", minecraftVersion);
            return;
        }

        if (!cachedVersion.isEmpty() && !minecraftVersion.equals(cachedVersion))
        {
            api.logging().info("Minecraft version changed from " + cachedVersion + " to " + minecraftVersion + "; recreating HTTPD asset cache");
        }
        recreateCache();
    }

    private boolean hasAssets()
    {
        return Files.isDirectory(root.resolve("textures"))
                && Files.isDirectory(root.resolve("models"))
                && Files.isDirectory(root.resolve("items"))
                && Files.isRegularFile(root.resolve("textures/entity/shield/shield_base_nopattern.png"));
    }

    private void recreateCache() throws IOException, InterruptedException
    {
        deleteDirectory(root);
        Files.createDirectories(root);

        api.logging().info("Downloading Minecraft " + minecraftVersion + " client assets for HTTPD inventory view");
        JSONObject version = findVersionJson();
        String clientUrl = version.getJSONObject("downloads").getJSONObject("client").getString("url");

        HttpRequest request = HttpRequest.newBuilder(URI.create(clientUrl))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2)
        {
            throw new IOException("client jar download returned HTTP " + response.statusCode());
        }

        try (InputStream in = response.body(); ZipInputStream zip = new ZipInputStream(in))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                if (!entry.isDirectory())
                {
                    copyAsset(entry.getName(), zip);
                }
                zip.closeEntry();
            }
        }

        Files.writeString(versionFile, minecraftVersion + System.lineSeparator());
        api.logging().info("HTTPD Minecraft assets cached for " + minecraftVersion);
    }

    private JSONObject findVersionJson() throws IOException, InterruptedException
    {
        JSONObject manifest = getJson(VERSION_MANIFEST);
        JSONArray versions = manifest.getJSONArray("versions");
        for (int i = 0; i < versions.length(); i++)
        {
            JSONObject version = versions.getJSONObject(i);
            if (minecraftVersion.equals(version.getString("id")))
            {
                return getJson(URI.create(version.getString("url")));
            }
        }
        throw new IOException("Minecraft version " + minecraftVersion + " was not found in Mojang's manifest");
    }

    private JSONObject getJson(URI uri) throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2)
        {
            throw new IOException(uri + " returned HTTP " + response.statusCode());
        }
        return new JSONObject(response.body());
    }

    private void copyAsset(String name, ZipInputStream zip) throws IOException
    {
        String prefix = "assets/minecraft/";
        if (!name.startsWith(prefix))
        {
            return;
        }
        String path = name.substring(prefix.length());
        Path target = null;

        if (path.startsWith("textures/item/") || path.startsWith("textures/block/") || path.equals("textures/entity/shield/shield_base_nopattern.png"))
        {
            target = root.resolve(path);
        }
        else if (path.startsWith("models/item/") || path.startsWith("models/block/"))
        {
            target = root.resolve(path);
        }
        else if (path.startsWith("items/"))
        {
            target = root.resolve(path);
        }

        if (target == null)
        {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String detectMinecraftVersion()
    {
        return Bukkit.getMinecraftVersion();
    }

    private static void deleteDirectory(Path path) throws IOException
    {
        if (!Files.exists(path))
        {
            return;
        }
        try (var stream = Files.walk(path))
        {
            for (Path file : stream.sorted(Comparator.reverseOrder()).toList())
            {
                Files.deleteIfExists(file);
            }
        }
    }
}
