package dev.plex.request;

import com.google.common.collect.Lists;
import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.authentication.OAuth2Provider;
import dev.plex.logging.Log;
import dev.plex.api.player.PlexPlayerView;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

public class AbstractServlet extends HttpServlet
{
    private static final int PLAYER_LOOKUP_TIMEOUT_SECONDS = 10;
    private final List<Mapping> GET_MAPPINGS = Lists.newArrayList();
    private final AtomicLong suppressedHandlerErrors = new AtomicLong();
    private final AtomicLong nextHandlerErrorLogMillis = new AtomicLong();
    protected final HTTPDModule module;

    public AbstractServlet(HTTPDModule module)
    {
        this.module = module;
        for (Method declaredMethod : this.getClass().getDeclaredMethods())
        {
            declaredMethod.setAccessible(true);
            if (declaredMethod.isAnnotationPresent(GetMapping.class))
            {
                GetMapping getMapping = declaredMethod.getAnnotation(GetMapping.class);
                Mapping mapping = new Mapping(declaredMethod, getMapping);
                if (declaredMethod.isAnnotationPresent(MappingHeaders.class))
                {
                    mapping.setHeaders(declaredMethod.getAnnotation(MappingHeaders.class));
                }
                GET_MAPPINGS.add(mapping);
                ServletHolder holder = new ServletHolder(this);
                String endpoint = getMapping.endpoint();
                String pattern = endpoint.endsWith("/") ? endpoint + "*" : endpoint;
                module.getContext().addServlet(holder, pattern);
            }
        }
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        String ipAddress = req.getRemoteAddr();
        if (ipAddress.equals("127.0.0.1"))
        {
            ipAddress = req.getHeader("X-FORWARDED-FOR");
        }

        String requestPath = getRequestPath(req);
        if (!isHighVolumeAssetPath(requestPath))
        {
            module.getAccessLog().log(ipAddress + " visited endpoint " + requestPath);
        }

        GET_MAPPINGS.stream().filter(mapping -> endpointMatchesRequest(mapping.getMapping().endpoint(), requestPath)).forEach(mapping ->
        {
            resp.setCharacterEncoding("UTF-8");
            if (mapping.headers != null)
            {
                for (String headers : mapping.headers.headers())
                {
                    String[] parts = headers.split(";", 2);
                    if (parts.length == 2)
                    {
                        resp.addHeader(parts[0], parts[1]);
                    }
                }
            }
            if (resp.getContentType() == null)
            {
                resp.setContentType("text/html; charset=UTF-8");
            }
            try
            {
                Object object = mapping.method.invoke(this, req, resp);
                if (object != null)
                {
                    resp.getWriter().println(object.toString());
                }
            }
            catch (IOException | IllegalAccessException | InvocationTargetException e)
            {
                handleFailure(resp, requestPath, mapping, e);
            }
        });
    }

    private void handleFailure(HttpServletResponse response, String requestPath, Mapping mapping, Exception error)
    {
        Throwable cause = error instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : error;
        long suppressed = suppressedHandlerErrors.incrementAndGet();
        long now = System.currentTimeMillis();
        long next = nextHandlerErrorLogMillis.get();
        if (now >= next && nextHandlerErrorLogMillis.compareAndSet(next, now + 10_000L))
        {
            module.api().logging().error("HTTPD handler {0} failed for {1} ({2}); {3} failure(s) since the previous report",
                    mapping.method.getName(), requestPath, cause.getClass().getSimpleName(), suppressedHandlerErrors.getAndSet(0L));
        }
        if (response.isCommitted()) return;
        try
        {
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"ok\":false,\"error\":\"Internal Server Error\"}");
        }
        catch (IOException ignored)
        {
        }
    }

    private static boolean isHighVolumeAssetPath(String requestPath)
    {
        return requestPath.startsWith("/app/assets/") || requestPath.startsWith("/assets/");
    }

    private static boolean endpointMatchesRequest(String endpoint, String requestPath)
    {
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        if (normalizedEndpoint.equals("/"))
        {
            return requestPath.equals("/");
        }
        String endpointPrefix = normalizedEndpoint + "/";
        return requestPath.equalsIgnoreCase(normalizedEndpoint) || requestPath.regionMatches(true, 0, endpointPrefix, 0, endpointPrefix.length());
    }

    private static String normalizeEndpoint(String endpoint)
    {
        if (endpoint.equals("//"))
        {
            return "/";
        }
        if (endpoint.length() > 1 && endpoint.endsWith("/"))
        {
            return endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }

    private static String getRequestPath(HttpServletRequest req)
    {
        String requestPath = req.getRequestURI();
        String contextPath = req.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/") && requestPath.startsWith(contextPath))
        {
            requestPath = requestPath.substring(contextPath.length());
        }
        return requestPath.isEmpty() ? "/" : requestPath;
    }

    public static AuthenticatedUser currentUser(HTTPDModule module, HttpServletRequest request)
    {
        OAuth2Provider provider = module.getAuthenticationProvider();
        if (provider == null) return null;
        return provider.lookup(request);
    }

    public static AuthenticatedUser currentStaff(HTTPDModule module, HttpServletRequest request)
    {
        AuthenticatedUser user = currentUser(module, request);
        return (user != null && user.staff()) ? user : null;
    }

    public static PlexPlayerView lookupPlayer(HTTPDModule module, String query)
    {
        CompletableFuture<Optional<PlexPlayerView>> lookup;
        try
        {
            lookup = module.api().players().player(UUID.fromString(query));
        }
        catch (IllegalArgumentException ignored)
        {
            lookup = module.api().players().byName(query);
        }
        return lookup.orTimeout(PLAYER_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join().orElse(null);
    }

    protected String signInPrompt(HttpServletRequest request, String action)
    {
        String href = "/oauth2/login";
        if (request != null)
        {
            String path = getRequestPath(request);
            String query = request.getQueryString();
            String returnTo = query == null || query.isEmpty() ? path : path + "?" + query;
            href = href + "?return_to=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
        }
        return "You must <a class=\"text-primary underline\" href=\"" + href + "\">sign in</a> as staff " + action + ".";
    }

    public static String readFileReal(InputStream filename)
    {
        StringBuilder contentBuilder = new StringBuilder();
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(Objects.requireNonNull(filename), StandardCharsets.UTF_8));
            String str;
            while ((str = in.readLine()) != null)
            {
                contentBuilder.append(str).append("\n");
            }
            in.close();
        }
        catch (IOException ignored)
        {
        }
        return contentBuilder.toString();
    }

    // Code from https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
    public static String formattedSize(long bytes)
    {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024)
        {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10)
        {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }

    @Data
    public static class Mapping
    {
        private final Method method;
        private final GetMapping mapping;
        private MappingHeaders headers;
    }
}
