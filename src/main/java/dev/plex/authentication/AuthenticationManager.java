package dev.plex.authentication;

import dev.plex.HTTPDModule;
import dev.plex.authentication.impl.XenForoOAuth2Provider;

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

        HTTPDModule.plexApi().logging().info("[HTTPD] XenForo OAuth2 authentication is enabled");
        provider = new XenForoOAuth2Provider();
    }

    public OAuth2Provider provider()
    {
        return this.provider;
    }
}
