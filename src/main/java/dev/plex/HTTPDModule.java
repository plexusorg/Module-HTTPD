package dev.plex;

import dev.plex.assets.MinecraftAssetsManager;
import dev.plex.authentication.AuthenticationManager;
import dev.plex.cache.FileCache;
import dev.plex.config.ModuleConfig;
import dev.plex.logging.Log;
import dev.plex.module.PlexModule;
import dev.plex.ratelimit.RateLimitFilter;
import dev.plex.request.AbstractServlet;
import dev.plex.request.PlayerActionServlet;
import dev.plex.request.PlayerInventoryStreamServlet;
import dev.plex.request.PlayersStreamServlet;
import dev.plex.request.SchematicUploadServlet;
import dev.plex.request.StaffPlayersStreamServlet;
import dev.plex.request.StatsStreamServlet;
import dev.plex.request.impl.*;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.MultipartConfigElement;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.File;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

public class HTTPDModule extends PlexModule
{
    @Getter
    private ServletContextHandler context;
    private Thread serverThread;
    private final AtomicReference<Server> atomicServer = new AtomicReference<>();

    @Getter
    private ModuleConfig moduleConfig;

    @Getter
    private final FileCache fileCache = new FileCache();

    @Getter
    private final String template = AbstractServlet.readFileReal(HTTPDModule.class.getResourceAsStream("/httpd/template.html"));

    @Getter
    private AuthenticationManager authenticationManager;

    @Getter
    private File accessLogFile;

    @Getter
    private MinecraftAssetsManager minecraftAssetsManager;

    @Getter
    private StatsBroadcaster statsBroadcaster;

    @Getter
    private PlayersBroadcaster playersBroadcaster;

    @Getter
    private PlayerInventoryBroadcaster playerInventoryBroadcaster;

    @Override
    public void load()
    {
        // Move it from /httpd/config.yml to /plugins/Plex/modules/Plex-HTTPD/config.yml
        moduleConfig = new ModuleConfig(this, "httpd/config.yml", "config.yml");
    }

    @Override
    public void enable()
    {
        moduleConfig.load();
        api().logging().debug("HTTPD Module Port: {0}", moduleConfig.getInt("server.port"));

        accessLogFile = new File(getDataFolder(), moduleConfig.getString("server.logging.file-path", "httpd.log"));
        Log.configure(moduleConfig, accessLogFile);

        minecraftAssetsManager = new MinecraftAssetsManager(getDataFolder().toPath(), api());
        minecraftAssetsManager.refreshAsync();

        authenticationManager = new AuthenticationManager(this);
        if (authenticationManager.provider() == null)
        {
            api().logging().debug("Authentication is disabled or misconfigured");
        }


        serverThread = new Thread(() ->
        {
            int maxThreads = moduleConfig.getInt("server.threads.max", 16);
            int minThreads = Math.min(moduleConfig.getInt("server.threads.min", 2), maxThreads);
            int idleTimeout = moduleConfig.getInt("server.threads.idle-timeout-ms", 30_000);
            QueuedThreadPool pool = new QueuedThreadPool(maxThreads, minThreads, idleTimeout);
            pool.setName("Plex-HTTPD");
            pool.setDaemon(true);

            Server server = new Server(pool);
            ServletHandler servletHandler = new ServletHandler();

            context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setHandler(servletHandler);
            context.setContextPath("/");
            HttpConfiguration configuration = new HttpConfiguration();
            configuration.addCustomizer(new ForwardedRequestCustomizer());
            configuration.setRequestHeaderSize(moduleConfig.getInt("server.limits.request-header-bytes", 8 * 1024));
            configuration.setSendServerVersion(false);
            HttpConnectionFactory factory = new HttpConnectionFactory(configuration);
            ServerConnector connector = new ServerConnector(server, factory);
            connector.setHost(moduleConfig.getString("server.bind-address"));
            connector.setPort(moduleConfig.getInt("server.port"));
            connector.setIdleTimeout(moduleConfig.getLong("server.limits.idle-timeout-ms", 15_000L));
            connector.setAcceptQueueSize(moduleConfig.getInt("server.limits.accept-queue", 32));

            context.addFilter(new FilterHolder(new RateLimitFilter(moduleConfig)), "/*", EnumSet.of(DispatcherType.REQUEST));

            statsBroadcaster = new StatsBroadcaster(this);
            playersBroadcaster = new PlayersBroadcaster(this);
            playerInventoryBroadcaster = new PlayerInventoryBroadcaster(this);
            statsBroadcaster.start();
            playersBroadcaster.start();
            playerInventoryBroadcaster.start();

            new IndefBansEndpoint(this);
            new IndexEndpoint(this);
            new ListEndpoint(this);
            new PunishmentsEndpoint(this);
            new CommandsEndpoint(this);
            new SchematicDownloadEndpoint(this);
            new SchematicUploadEndpoint(this);
            new PlayersEndpoint(this);
            new PlayerAdminEndpoint(this);
            new AssetsEndpoint(this);
            new PunishmentsUIEndpoint(this);
            new IndefBansUIEndpoint(this);
            new AuthenticationEndpoint(this);

            context.addServlet(new ServletHolder(new StatsStreamServlet(statsBroadcaster)), "/api/stats/stream");
            context.addServlet(new ServletHolder(new PlayersStreamServlet(playersBroadcaster)), "/api/players/stream");
            context.addServlet(new ServletHolder(new StaffPlayersStreamServlet(this, playersBroadcaster)), "/api/players/stream/staff");
            context.addServlet(new ServletHolder(new PlayerActionServlet(this)), "/api/admin/action");
            context.addServlet(new ServletHolder(new PlayerInventoryStreamServlet(this, playerInventoryBroadcaster)), "/api/player/inventory/stream");

            ServletHolder uploadHolder = new ServletHolder(new SchematicUploadServlet(this));
            context.addServlet(uploadHolder, "/api/schematics/uploading");

            File uploadLoc = new File(System.getProperty("java.io.tmpdir"), "schematic-temp-dir");
            if (!uploadLoc.exists())
            {
                uploadLoc.mkdirs();
            }
            uploadHolder.getRegistration().setMultipartConfig(new MultipartConfigElement(uploadLoc.getAbsolutePath(), 1024 * 1024 * 5, 1024 * 1024 * 25, 1024 * 1024));

            server.setConnectors(new Connector[]{connector});
            server.setHandler(context);

            atomicServer.set(server);
            try
            {
                server.start();
                server.join();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }, "Jetty-Server");
        serverThread.start();
        api().logging().info("Starting Jetty server on port " + moduleConfig.getInt("server.port"));
    }

    @Override
    public void disable()
    {
        api().logging().debug("Stopping Jetty server");
        try
        {
            if (statsBroadcaster != null)
            {
                statsBroadcaster.shutdown();
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        try
        {
            if (playersBroadcaster != null)
            {
                playersBroadcaster.shutdown();
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        try
        {
            if (playerInventoryBroadcaster != null)
            {
                playerInventoryBroadcaster.shutdown();
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        try
        {
            Server server = atomicServer.get();
            if (server != null)
            {
                server.stop();
                server.destroy();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        Log.shutdown();
    }

    public static File getWorldeditFolder()
    {
        if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit"))
        {
            return new File(Bukkit.getPluginManager().getPlugin("WorldEdit").getDataFolder() + "/schematics/");
        }
        else if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit"))
        {
            return new File(Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit").getDataFolder() + "/schematics/");
        }
        else
        {
            return null;
        }
    }

    private static boolean isFileSystemCaseSensitive = !new File("a").equals(new File("A"));

    public static boolean fileNameEquals(String filename1, String filename2)
    {
        if (isFileSystemCaseSensitive)
        {
            return filename1.equals(filename2);
        }
        else
        {
            return filename1.equalsIgnoreCase(filename2);
        }
    }
}
