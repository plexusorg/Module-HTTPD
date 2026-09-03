package dev.plex;

import dev.plex.assets.MinecraftAssetsManager;
import dev.plex.authentication.OAuth2Provider;
import dev.plex.authentication.impl.XenForoOAuth2Provider;
import dev.plex.api.config.ModuleConfiguration;
import dev.plex.logging.Log;
import dev.plex.module.PlexModule;
import dev.plex.ratelimit.RateLimitFilter;
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

public class HTTPDModule extends PlexModule
{
    @Getter
    private ServletContextHandler context;
    private Thread serverThread;
    private volatile Server server;

    @Getter
    private ModuleConfiguration moduleConfig;

    @Getter
    private OAuth2Provider authenticationProvider;

    @Getter
    private Log accessLog;

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
        moduleConfig = api().moduleConfigs().create(this, "config.yml");
    }

    @Override
    public void enable()
    {
        moduleConfig.load();
        api().logging().debug("HTTPD Module Port: {0}", moduleConfig.getInt("server.port"));

        accessLogFile = new File(getDataFolder(), moduleConfig.getString("server.logging.file-path", "httpd.log"));
        accessLog = new Log(moduleConfig, accessLogFile, getLogger());

        minecraftAssetsManager = new MinecraftAssetsManager(getDataFolder().toPath(), api());
        minecraftAssetsManager.refreshAsync();

        authenticationProvider = null;
        if (moduleConfig.getBoolean("authentication.enabled", false))
        {
            api().logging().info("[HTTPD] XenForo OAuth2 authentication is enabled");
            authenticationProvider = new XenForoOAuth2Provider(this);
        }
        else
        {
            api().logging().debug("Authentication is disabled or misconfigured");
        }

        playerInventoryBroadcaster = new PlayerInventoryBroadcaster(this);

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

            context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
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

            context.addFilter(new FilterHolder(new RateLimitFilter(moduleConfig, accessLog)), "/*", EnumSet.of(DispatcherType.REQUEST));

            statsBroadcaster = new StatsBroadcaster(this);
            playersBroadcaster = new PlayersBroadcaster(this);
            statsBroadcaster.start();
            playersBroadcaster.start();
            playerInventoryBroadcaster.start();

            new IndefBansEndpoint(this);
            new IndexEndpoint(this);
            new ListEndpoint(this);
            new PunishmentsEndpoint(this);
            new CommandsEndpoint(this);
            new SchematicDownloadEndpoint(this);
            new PlayerAdminEndpoint(this);
            new AssetsEndpoint(this);
            new AuthenticationEndpoint(this);
            new FrontendEndpoint(this);

            context.addServlet(new ServletHolder(new StatsStreamServlet(accessLog, statsBroadcaster)), "/api/stats/stream");
            context.addServlet(new ServletHolder(new PlayersStreamServlet(accessLog, playersBroadcaster)), "/api/players/stream");
            context.addServlet(new ServletHolder(new StaffPlayersStreamServlet(this, playersBroadcaster)), "/api/players/stream/staff");
            context.addServlet(new ServletHolder(new PlayerActionServlet(this)), "/api/admin/player-action");
            context.addServlet(new ServletHolder(new PlayerInventoryStreamServlet(this, playerInventoryBroadcaster)), "/api/player/inventory/stream");

            ServletHolder uploadHolder = new ServletHolder(new SchematicUploadServlet(this));
            context.addServlet(uploadHolder, "/api/schematics/upload");

            File uploadLoc = new File(System.getProperty("java.io.tmpdir"), "schematic-temp-dir");
            if (!uploadLoc.exists())
            {
                uploadLoc.mkdirs();
            }
            uploadHolder.getRegistration().setMultipartConfig(new MultipartConfigElement(uploadLoc.getAbsolutePath(), 1024 * 1024 * 5, 1024 * 1024 * 25, 1024 * 1024));

            server.setConnectors(new Connector[]{connector});
            int maxConnections = Math.max(32, moduleConfig.getInt("server.limits.max-connections", 256));
            NetworkConnectionLimit connectionLimit = new NetworkConnectionLimit(maxConnections, server);
            connectionLimit.setEndPointIdleTimeout(5_000L);
            server.addBean(connectionLimit);
            server.setHandler(context);

            this.server = server;
            try
            {
                server.start();
                server.join();
            }
            catch (Exception e)
            {
                getLogger().error("HTTP server stopped unexpectedly", e);
            }
            finally
            {
                this.server = null;
            }
        }, "Jetty-Server");
        serverThread.start();
        api().logging().info("Starting Jetty server on port " + moduleConfig.getInt("server.port"));
    }

    @Override
    public void disable()
    {
        api().logging().debug("Stopping Jetty server");
        if (minecraftAssetsManager != null)
        {
            minecraftAssetsManager.shutdown();
        }
        if (statsBroadcaster != null)
        {
            statsBroadcaster.shutdown();
        }
        if (playersBroadcaster != null)
        {
            playersBroadcaster.shutdown();
        }
        if (playerInventoryBroadcaster != null)
        {
            playerInventoryBroadcaster.shutdown();
        }
        try
        {
            Server server = this.server;
            this.server = null;
            if (server != null)
            {
                server.stop();
                server.destroy();
            }
        }
        catch (Exception e)
        {
            getLogger().error("Failed to stop HTTP server", e);
        }
        Thread thread = serverThread;
        if (thread != null && thread != Thread.currentThread())
        {
            try
            {
                thread.join(5_000L);
            }
            catch (InterruptedException ignored)
            {
                Thread.currentThread().interrupt();
            }
        }
        serverThread = null;
        if (accessLog != null)
        {
            accessLog.shutdown();
        }
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
