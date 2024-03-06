package dev.plex.authentication;

import org.eclipse.jetty.server.Response;

import java.util.HashMap;

/**
 * @author Taah
 * @since 6:36 PM [03-05-2024]
 */
public interface OAuth2Provider
{
    HashMap<String, AuthenticatedUser> sessions();

    AuthenticatedUser login(Response response, UserType type);

    String[] roles(AuthenticatedUser user);

    String generateLogin();

}
