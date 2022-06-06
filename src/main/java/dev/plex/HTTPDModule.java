package dev.plex;

import dev.plex.cache.FileCache;
import dev.plex.config.ModuleConfig;
import dev.plex.module.PlexModule;
import dev.plex.request.AbstractServlet;
import dev.plex.request.SchematicUploadServlet;
import dev.plex.request.impl.AdminsEndpoint;
import dev.plex.request.impl.IndefBansEndpoint;
import dev.plex.request.impl.IndexEndpoint;
import dev.plex.request.impl.ListEndpoint;
import dev.plex.request.impl.PunishmentsEndpoint;
import dev.plex.request.impl.SchematicDownloadEndpoint;
import dev.plex.request.impl.SchematicUploadEndpoint;
import dev.plex.util.PlexLog;
import jakarta.servlet.MultipartConfigElement;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class HTTPDModule extends PlexModule
{
    public static ServletContextHandler context;
    private Thread serverThread;
    private AtomicReference<Server> atomicServer = new AtomicReference<>();

    @Getter
    private static Permission permissions = null;

    public static ModuleConfig moduleConfig;

    public static final FileCache fileCache = new FileCache();

    public static final String template = AbstractServlet.readFileReal(HTTPDModule.class.getResourceAsStream("/httpd/template.html"));

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
        if ((!Bukkit.getPluginManager().isPluginEnabled("Vault") || !setupPermissions()) && getPlex().getSystem().equalsIgnoreCase("permissions"))
        {
            throw new RuntimeException("Plex-HTTPD requires the 'Vault' plugin as well as a Permissions plugin that hooks into 'Vault'. We recommend LuckPerms!");
        }
        serverThread = new Thread(() ->
        {
            Server server = new Server();
            ServletHandler servletHandler = new ServletHandler();

            context = new ServletContextHandler(servletHandler, "/", ServletContextHandler.SESSIONS);
            HttpConfiguration configuration = new HttpConfiguration();
            configuration.addCustomizer(new ForwardedRequestCustomizer());
            HttpConnectionFactory factory = new HttpConnectionFactory(configuration);
            ServerConnector connector = new ServerConnector(server, factory);
            connector.setHost(moduleConfig.getString("server.bind-address"));
            connector.setPort(moduleConfig.getInt("server.port"));

            new AdminsEndpoint();
            new IndefBansEndpoint();
            new IndexEndpoint();
            new ListEndpoint();
            new PunishmentsEndpoint();
            new SchematicDownloadEndpoint();
            new SchematicUploadEndpoint();

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
            atomicServer.get().stop();
            atomicServer.get().destroy();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private boolean setupPermissions()
    {
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
        permissions = rsp.getProvider();
        return permissions != null;
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
