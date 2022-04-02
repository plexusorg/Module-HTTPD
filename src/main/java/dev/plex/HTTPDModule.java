package dev.plex;

import dev.plex.config.Config;
import dev.plex.config.ModuleConfig;
import dev.plex.module.PlexModule;
import dev.plex.request.impl.GetEndpoints;
import dev.plex.util.PlexLog;
import lombok.Getter;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;

import java.util.concurrent.atomic.AtomicReference;

public class HTTPDModule extends PlexModule {

    public static ServletContextHandler context;
    private Thread serverThread;
    private AtomicReference<Server> atomicServer = new AtomicReference<>();

    @Getter
    private static Permission permissions = null;

    private ModuleConfig config;

    @Override
    public void enable() {
        config =  new ModuleConfig(this, "settings.yml");
        config.load();
        PlexLog.debug("HTTPD Module Port: {0}", config.getInt("server.port"));
        if (!setupPermissions() && getPlex().getSystem().equalsIgnoreCase("permissions") && !Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            throw new RuntimeException("Plex-HTTPD requires the 'Vault' plugin as well as a Permissions plugin that hooks into 'Vault.' We recommend LuckPerms!");
        }
        serverThread = new Thread(() -> {
            Server server = new Server();
            ServletHandler servletHandler = new ServletHandler();

            context = new ServletContextHandler(servletHandler, "/", ServletContextHandler.SESSIONS);
            HttpConfiguration configuration = new HttpConfiguration();
            configuration.addCustomizer(new ForwardedRequestCustomizer());
            HttpConnectionFactory factory = new HttpConnectionFactory(configuration);
            ServerConnector connector = new ServerConnector(server, factory);
            connector.setPort(config.getInt("server.port"));
            connector.setHost("0.0.0.0");

            new GetEndpoints();

            server.setConnectors(new Connector[]{connector});
            server.setHandler(context);

            atomicServer.set(server);
            PlexLog.debug("Set atomicServer value? {0}", atomicServer.get() != null);
            try {
                server.start();
                server.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "jetty-server");
        serverThread.start();
    }

    @Override
    public void disable() {
        PlexLog.debug("Stopping jetty server");
        try {
            atomicServer.get().stop();
            atomicServer.get().destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
        permissions = rsp.getProvider();
        return permissions != null;
    }
}
