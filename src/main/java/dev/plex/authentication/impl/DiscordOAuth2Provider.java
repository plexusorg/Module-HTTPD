package dev.plex.authentication.impl;

import com.google.common.collect.Maps;
import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.authentication.OAuth2Provider;
import dev.plex.authentication.UserType;
import dev.plex.util.PlexLog;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class DiscordOAuth2Provider implements OAuth2Provider
{
    private final HashMap<String, AuthenticatedUser> sessions = Maps.newHashMap();

    private final String token;
    private final String clientId;
    private final String redirectUri;

    public DiscordOAuth2Provider()
    {
        token = System.getenv("BOT_TOKEN").isEmpty() ? HTTPDModule.moduleConfig.getString("authentication.provider.discord.token", System.getProperty("BOT_TOKEN", "")) : System.getenv("BOT_TOKEN");
        clientId = HTTPDModule.moduleConfig.getString("authentication.provider.discord.clientId", "");
        redirectUri = URLEncoder.encode(HTTPDModule.moduleConfig.getString("authentication.provider.redirectUri", ""), StandardCharsets.UTF_8);

        PlexLog.debug("[HTTPD] Client ID: {0}, Redirect URL: {1}", clientId, redirectUri);

        if (redirectUri.isEmpty())
        {
            PlexLog.error("Provided authentication redirect url was empty for HTTPD!");
            return;
        }

        if (token.isEmpty())
        {
            PlexLog.error("Provided discord authentication token was empty for HTTPD!");
            return;
        }

        if (clientId.isEmpty())
        {
            PlexLog.error("Provided discord client ID was empty for HTTPD!");
        }

    }

    @Override
    public HashMap<String, AuthenticatedUser> sessions()
    {
        return sessions;
    }

    @Override
    public AuthenticatedUser login(Response response, UserType type)
    {
        return null;
    }

    @Override
    public String[] roles(AuthenticatedUser user)
    {
        return new String[0];
    }

    @Override
    public String generateLogin()
    {
        return String.format("https://discord.com/oauth2/authorize?client_id=%s&scope=%s&redirect_uri=%s",
                clientId,
                "identify%20guilds%20guilds.members.read",
                redirectUri);
    }
}
