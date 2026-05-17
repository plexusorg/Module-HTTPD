package dev.plex.request.impl;

import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.authentication.AuthenticationException;
import dev.plex.authentication.OAuth2Provider;
import dev.plex.request.AbstractServlet;
import dev.plex.request.GetMapping;
import dev.plex.request.MappingHeaders;
import dev.plex.util.PlexLog;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

import java.io.IOException;

public class AuthenticationEndpoint extends AbstractServlet
{
    @GetMapping(endpoint = "/oauth2/login")
    public String login(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OAuth2Provider provider = HTTPDModule.getAuthenticationManager().provider();
        if (provider == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Authentication is not enabled.");
            return null;
        }
        response.sendRedirect(provider.buildAuthorizeUrl(request));
        return null;
    }

    @GetMapping(endpoint = "/oauth2/callback")
    public String callback(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OAuth2Provider provider = HTTPDModule.getAuthenticationManager().provider();
        if (provider == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Authentication is not enabled.");
            return null;
        }
        try
        {
            provider.handleCallback(request, response);
        }
        catch (AuthenticationException e)
        {
            PlexLog.error("OAuth2 callback failed: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("text/html; charset=UTF-8");
            return "<!doctype html><meta charset=utf-8><title>Sign-in failed</title>"
                    + "<body style=\"font-family:system-ui;padding:2rem;max-width:30rem;margin:auto\">"
                    + "<h1 style=\"font-size:1.25rem\">Sign-in failed</h1>"
                    + "<p>" + escape(e.getMessage()) + "</p>"
                    + "<p><a href=\"/oauth2/login\">Try again</a></p>";
        }
        response.sendRedirect("/");
        return null;
    }

    @GetMapping(endpoint = "/oauth2/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OAuth2Provider provider = HTTPDModule.getAuthenticationManager().provider();
        if (provider == null)
        {
            response.sendRedirect("/");
            return null;
        }
        String sessionId = readSessionCookie(request);
        provider.logout(sessionId);

        Cookie clear = new Cookie(OAuth2Provider.SESSION_COOKIE, "");
        clear.setHttpOnly(true);
        clear.setPath("/");
        clear.setMaxAge(0);
        response.addCookie(clear);
        response.sendRedirect("/");
        return null;
    }

    @GetMapping(endpoint = "/oauth2/me")
    @MappingHeaders(headers = "content-type;application/json")
    public String me(HttpServletRequest request, HttpServletResponse response)
    {
        OAuth2Provider provider = HTTPDModule.getAuthenticationManager().provider();
        if (provider == null)
        {
            return "{\"authenticated\":false,\"reason\":\"disabled\"}";
        }
        AuthenticatedUser user = provider.lookup(request);
        if (user == null)
        {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return "{\"authenticated\":false}";
        }
        return new JSONObject()
                .put("authenticated", true)
                .put("user_id", user.userId())
                .put("username", user.username())
                .put("is_staff", user.staff())
                .toString();
    }

    private static String readSessionCookie(HttpServletRequest request)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies)
        {
            if (OAuth2Provider.SESSION_COOKIE.equals(cookie.getName()))
            {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String escape(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
