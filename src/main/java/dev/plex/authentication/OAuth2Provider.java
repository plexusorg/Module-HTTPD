package dev.plex.authentication;

import org.eclipse.jetty.server.Response;

import java.util.HashMap;

public interface OAuth2Provider
{
    HashMap<String, AuthenticatedUser> sessions();

    AuthenticatedUser login(Response response, UserType type);

    String[] roles(AuthenticatedUser user);

    String generateLogin();

}
