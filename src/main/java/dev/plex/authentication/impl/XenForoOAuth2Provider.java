package dev.plex.authentication.impl;

import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.authentication.AuthenticationException;
import dev.plex.authentication.OAuth2Provider;
import dev.plex.authentication.UserType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XenForoOAuth2Provider implements OAuth2Provider
{
    private static final String AUTHORIZE_PATH = "/oauth2/authorize";
    private static final String TOKEN_PATH = "/api/oauth2/token";
    private static final String REVOKE_PATH = "/api/oauth2/revoke";
    private static final String ME_PATH = "/api/me";
    private static final String SCOPE = "user:read";
    private static final Duration PENDING_TTL = Duration.ofMinutes(10);

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
    private final Map<String, PendingLogin> pending = new ConcurrentHashMap<>();
    private final Map<String, AuthenticatedUser> sessions = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String authorizeUrl;
    private final String tokenUrl;
    private final String revokeUrl;
    private final String meUrl;
    private final String referer;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final Duration sessionTtl;

    public XenForoOAuth2Provider()
    {
        String domain = HTTPDModule.moduleConfig.getString("authentication.provider.xenforo.domain", "");
        this.clientId = HTTPDModule.moduleConfig.getString("authentication.provider.xenforo.clientId", "");
        this.clientSecret = HTTPDModule.moduleConfig.getString("authentication.provider.xenforo.clientSecret", "");
        this.redirectUri = HTTPDModule.moduleConfig.getString("authentication.provider.redirectUri", "");
        long ttlMinutes = HTTPDModule.moduleConfig.getLong("authentication.provider.xenforo.sessionMinutes", 1440L);
        this.sessionTtl = Duration.ofMinutes(Math.max(ttlMinutes, 1L));

        if (domain.isEmpty() || clientId.isEmpty() || clientSecret.isEmpty() || redirectUri.isEmpty())
        {
            HTTPDModule.plexApi().logging().error("XenForo OAuth2 misconfigured: domain, clientId, clientSecret, redirectUri are all required.");
        }

        String base = "https://" + domain.replaceFirst("^https?://", "").replaceAll("/+$", "");
        this.authorizeUrl = base + AUTHORIZE_PATH;
        this.tokenUrl = base + TOKEN_PATH;
        this.revokeUrl = base + REVOKE_PATH;
        this.meUrl = base + ME_PATH;
        this.referer = refererOrigin(redirectUri, base);
    }

    private static String refererOrigin(String redirectUri, String fallback)
    {
        try
        {
            URI uri = URI.create(redirectUri);
            if (uri.getScheme() != null && uri.getHost() != null)
            {
                String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
                return uri.getScheme() + "://" + uri.getHost() + port + "/";
            }
        }
        catch (IllegalArgumentException ignored) {}
        return fallback + "/";
    }

    @Override
    public String buildAuthorizeUrl(HttpServletRequest request)
    {
        String state = randomToken(32);
        String verifier = randomToken(48);
        String challenge = pkceChallenge(verifier);
        pending.put(state, new PendingLogin(verifier, Instant.now().plus(PENDING_TTL)));
        purgeExpiredPending();

        return authorizeUrl
                + "?response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(challenge)
                + "&code_challenge_method=S256";
    }

    @Override
    public AuthenticatedUser handleCallback(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException
    {
        String error = request.getParameter("error");
        if (error != null && !error.isEmpty())
        {
            throw new AuthenticationException("XenForo returned error: " + error);
        }
        String code = request.getParameter("code");
        String state = request.getParameter("state");
        if (code == null || state == null)
        {
            throw new AuthenticationException("Missing code or state in callback.");
        }
        PendingLogin pendingLogin = pending.remove(state);
        if (pendingLogin == null || pendingLogin.expiresAt.isBefore(Instant.now()))
        {
            throw new AuthenticationException("Invalid or expired state parameter.");
        }

        TokenResponse token = exchangeCode(code, pendingLogin.verifier);
        JSONObject me = fetchMe(token.accessToken);
        if (!me.optBoolean("is_staff", false))
        {
            revokeToken(token.accessToken);
            throw new AuthenticationException("Account is not a staff member.");
        }

        Instant tokenExpiresAt = Instant.now().plusSeconds(Math.max(token.expiresIn, 60));
        AuthenticatedUser user = new AuthenticatedUser(
                me.optInt("user_id", 0),
                me.optString("username", "unknown"),
                true,
                UserType.XENFORO,
                token.accessToken,
                tokenExpiresAt,
                Instant.now());

        String sessionId = randomToken(32);
        sessions.put(sessionId, user);

        Cookie cookie = new Cookie(SESSION_COOKIE, sessionId);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) sessionTtl.getSeconds());
        cookie.setAttribute("SameSite", "Lax");
        if (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
        {
            cookie.setSecure(true);
        }
        response.addCookie(cookie);

        return user;
    }

    @Override
    public AuthenticatedUser lookup(String sessionId)
    {
        if (sessionId == null) return null;
        AuthenticatedUser user = sessions.get(sessionId);
        if (user == null) return null;
        if (user.authenticatedAt().plus(sessionTtl).isBefore(Instant.now()))
        {
            sessions.remove(sessionId);
            return null;
        }
        return user;
    }

    @Override
    public AuthenticatedUser lookup(HttpServletRequest request)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies)
        {
            if (SESSION_COOKIE.equals(cookie.getName()))
            {
                return lookup(cookie.getValue());
            }
        }
        return null;
    }

    @Override
    public void logout(String sessionId)
    {
        if (sessionId == null) return;
        AuthenticatedUser user = sessions.remove(sessionId);
        if (user != null && user.accessToken() != null)
        {
            revokeToken(user.accessToken());
        }
    }

    private TokenResponse exchangeCode(String code, String verifier) throws AuthenticationException
    {
        String body = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&code_verifier=" + enc(verifier);
        HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("Referer", referer)
                .header("User-Agent", "Plex-HTTPD")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp;
        try
        {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        }
        catch (Exception e)
        {
            throw new AuthenticationException("Failed to call XenForo token endpoint", e);
        }
        if (resp.statusCode() / 100 != 2)
        {
            throw new AuthenticationException("Token endpoint returned " + resp.statusCode() + ": " + resp.body());
        }
        JSONObject json = new JSONObject(resp.body());
        String accessToken = json.optString("access_token", "");
        if (accessToken.isEmpty())
        {
            throw new AuthenticationException("Token response missing access_token: " + resp.body());
        }
        return new TokenResponse(accessToken, json.optLong("expires_in", 3600L));
    }

    private JSONObject fetchMe(String accessToken) throws AuthenticationException
    {
        HttpRequest req = HttpRequest.newBuilder(URI.create(meUrl))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("Referer", referer)
                .header("User-Agent", "Plex-HTTPD")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp;
        try
        {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        }
        catch (Exception e)
        {
            throw new AuthenticationException("Failed to call XenForo /api/me", e);
        }
        if (resp.statusCode() / 100 != 2)
        {
            throw new AuthenticationException("/api/me returned " + resp.statusCode() + ": " + resp.body());
        }
        JSONObject json = new JSONObject(resp.body());
        JSONObject me = json.optJSONObject("me");
        return me == null ? json : me;
    }

    private void revokeToken(String accessToken)
    {
        String body = "token=" + enc(accessToken)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret);
        HttpRequest req = HttpRequest.newBuilder(URI.create(revokeUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", referer)
                .header("User-Agent", "Plex-HTTPD")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try
        {
            http.send(req, HttpResponse.BodyHandlers.discarding());
        }
        catch (Exception e)
        {
            HTTPDModule.plexApi().logging().debug("Failed to revoke XenForo token: {0}", e.getMessage());
        }
    }

    private void purgeExpiredPending()
    {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    private String randomToken(int bytes)
    {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return b64.encodeToString(buf);
    }

    private String pkceChallenge(String verifier)
    {
        try
        {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return b64.encodeToString(digest);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String enc(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record PendingLogin(String verifier, Instant expiresAt) {}

    private record TokenResponse(String accessToken, long expiresIn) {}
}
