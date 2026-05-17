package dev.plex.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface OAuth2Provider
{
    String SESSION_COOKIE = "plex_session";

    String buildAuthorizeUrl(HttpServletRequest request);

    AuthenticatedUser handleCallback(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException;

    AuthenticatedUser lookup(String sessionId);

    AuthenticatedUser lookup(HttpServletRequest request);

    void logout(String sessionId);
}
