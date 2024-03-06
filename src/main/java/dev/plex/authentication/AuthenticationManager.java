package dev.plex.authentication;

import dev.plex.HTTPDModule;
import dev.plex.authentication.impl.DiscordOAuth2Provider;
import dev.plex.util.PlexLog;
import org.apache.commons.lang3.NotImplementedException;

/**
 * @author Taah
 * @since 7:08 PM [03-05-2024]
 */
public class AuthenticationManager
{
    private final OAuth2Provider provider;

    public AuthenticationManager()
    {
        final boolean enabled = HTTPDModule.moduleConfig.getBoolean("authentication.enabled", false);
        if (!enabled)
        {
            provider = null;
            return;
        }

        PlexLog.debug("[HTTPD] Auth is enabled");

        final String providerName = HTTPDModule.moduleConfig.getString("authentication.provider.name", "");
        if (providerName.isEmpty())
        {
            PlexLog.error("OAuth2 Authentication is enabled but no provider was given!");
            provider = null;
            return;
        }

        PlexLog.debug("[HTTPD] Provider name is {0}", providerName);

        switch (providerName.toLowerCase())
        {
            case "discord" -> {
                provider = new DiscordOAuth2Provider();
            }
            case "xenforo" -> {
                throw new NotImplementedException("XenForo OAuth2 is not implemented yet!");
            }
            default -> {
                provider = null;
            }
        }

        PlexLog.log("Using {0} provider for authentication", providerName);
    }

    public OAuth2Provider provider()
    {
        return this.provider;
    }
}
