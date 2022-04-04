package dev.plex;

import dev.plex.config.ModuleConfig;
import dev.plex.module.PlexModule;
import dev.plex.request.impl.AdminsEndpoint;
import dev.plex.request.impl.IndefBansEndpoint;
import dev.plex.request.impl.ListEndpoint;
import dev.plex.request.impl.PunishmentsEndpoint;
import dev.plex.util.PlexLog;
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

public class HTTPDModule extends PlexModule
{
    public static ServletContextHandler context;
    private Thread serverThread;
    private AtomicReference<Server> atomicServer = new AtomicReference<>();

    @Getter
    private static Permission permissions = null;

    public static ModuleConfig moduleConfig;

    @Override
    public void load()
    {
        moduleConfig = new ModuleConfig(this, "settings.yml");
    }

    @Override
    public void enable()
    {
        moduleConfig.load();
        PlexLog.debug("HTTPD Module Port: {0}", moduleConfig.getInt("server.port"));
        if (!setupPermissions() && getPlex().getSystem().equalsIgnoreCase("permissions") && !Bukkit.getPluginManager().isPluginEnabled("Vault"))
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
            new ListEndpoint();
            new PunishmentsEndpoint();

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
}
