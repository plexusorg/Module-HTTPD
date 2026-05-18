package dev.plex;

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
import dev.plex.util.PlexLog;
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
    public static ServletContextHandler context;
    private Thread serverThread;
    private AtomicReference<Server> atomicServer = new AtomicReference<>();

    public static ModuleConfig moduleConfig;

    public static final FileCache fileCache = new FileCache();

    public static final String template = AbstractServlet.readFileReal(HTTPDModule.class.getResourceAsStream("/httpd/template.html"));

    @Getter
    private static AuthenticationManager authenticationManager;

    @Getter
    private static File accessLogFile;

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
        PlexLog.debug("HTTPD Module Port: {0}", moduleConfig.getInt("server.port"));

        accessLogFile = new File(getDataFolder(), moduleConfig.getString("server.logging.file-path", "httpd.log"));

        authenticationManager = new AuthenticationManager();
        if (authenticationManager.provider() == null)
        {
            PlexLog.debug("Authentication is disabled or misconfigured");
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

            context.addFilter(new FilterHolder(new RateLimitFilter()), "/*", EnumSet.of(DispatcherType.REQUEST));

            StatsBroadcaster.get().start();
            PlayersBroadcaster.get().start();
            PlayerInventoryBroadcaster.get().start();

            new IndefBansEndpoint();
            new IndexEndpoint();
            new ListEndpoint();
            new PunishmentsEndpoint();
            new CommandsEndpoint();
            new SchematicDownloadEndpoint();
            new SchematicUploadEndpoint();
            new PlayersEndpoint();
            new PlayerAdminEndpoint();
            new AssetsEndpoint();
            new PunishmentsUIEndpoint();
            new IndefBansUIEndpoint();
            new AuthenticationEndpoint();

            HTTPDModule.context.addServlet(StatsStreamServlet.class, "/api/stats/stream");
            HTTPDModule.context.addServlet(PlayersStreamServlet.class, "/api/players/stream");
            HTTPDModule.context.addServlet(StaffPlayersStreamServlet.class, "/api/players/stream/staff");
            HTTPDModule.context.addServlet(PlayerActionServlet.class, "/api/admin/action");
            HTTPDModule.context.addServlet(PlayerInventoryStreamServlet.class, "/api/player/inventory/stream");

            ServletHolder uploadHolder = HTTPDModule.context.addServlet(SchematicUploadServlet.class, "/api/schematics/uploading");

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
        PlexLog.log("Starting Jetty server on port " + moduleConfig.getInt("server.port"));
    }

    @Override
    public void disable()
    {
        PlexLog.debug("Stopping Jetty server");
        try
        {
            StatsBroadcaster.get().shutdown();
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        try
        {
            PlayersBroadcaster.get().shutdown();
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        try
        {
            PlayerInventoryBroadcaster.get().shutdown();
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        try
        {
            atomicServer.get().stop();
            atomicServer.get().destroy();
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
