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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class AuthenticationEndpoint extends AbstractServlet
{
    private static final String RETURN_TO_COOKIE = "plex_return_to";

    @GetMapping(endpoint = "/oauth2/login")
    public String login(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        OAuth2Provider provider = HTTPDModule.getAuthenticationManager().provider();
        if (provider == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Authentication is not enabled.");
            return null;
        }

        String returnTo = sanitizeReturnTo(request.getParameter("return_to"));
        String cookieValue = returnTo == null ? "" : URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
        Cookie returnCookie = new Cookie(RETURN_TO_COOKIE, cookieValue);
        returnCookie.setHttpOnly(true);
        returnCookie.setPath("/");
        returnCookie.setMaxAge(returnTo == null ? 0 : 600);
        returnCookie.setAttribute("SameSite", "Lax");
        if (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
        {
            returnCookie.setSecure(true);
        }
        response.addCookie(returnCookie);

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

        String raw = readCookie(request, RETURN_TO_COOKIE);
        String decoded = raw == null || raw.isEmpty() ? null : URLDecoder.decode(raw, StandardCharsets.UTF_8);
        String target = sanitizeReturnTo(decoded);
        clearReturnToCookie(request, response);
        response.sendRedirect(target == null ? "/" : target);
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
        return readCookie(request, OAuth2Provider.SESSION_COOKIE);
    }

    private static String readCookie(HttpServletRequest request, String name)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies)
        {
            if (name.equals(cookie.getName()))
            {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void clearReturnToCookie(HttpServletRequest request, HttpServletResponse response)
    {
        Cookie clear = new Cookie(RETURN_TO_COOKIE, "");
        clear.setHttpOnly(true);
        clear.setPath("/");
        clear.setMaxAge(0);
        clear.setAttribute("SameSite", "Lax");
        if (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
        {
            clear.setSecure(true);
        }
        response.addCookie(clear);
    }

    private static String sanitizeReturnTo(String value)
    {
        if (value == null || value.isEmpty()) return null;
        if (!value.startsWith("/")) return null;
        if (value.startsWith("//") || value.startsWith("/\\")) return null;
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r' || c == '\\') return null;
        }
        return value;
    }

    private static String escape(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
