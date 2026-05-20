package dev.plex.authentication;

import dev.plex.HTTPDModule;
import dev.plex.authentication.impl.XenForoOAuth2Provider;

public class AuthenticationManager
{
    private final OAuth2Provider provider;

    public AuthenticationManager(HTTPDModule module)
    {
        final boolean enabled = module.getModuleConfig().getBoolean("authentication.enabled", false);
        if (!enabled)
        {
            provider = null;
            return;
        }

        module.api().logging().info("[HTTPD] XenForo OAuth2 authentication is enabled");
        provider = new XenForoOAuth2Provider(module);
    }

    public OAuth2Provider provider()
    {
        return this.provider;
    }
}
