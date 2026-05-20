package dev.plex.request;

import com.google.common.collect.Lists;
import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.authentication.AuthenticationManager;
import dev.plex.authentication.OAuth2Provider;
import dev.plex.logging.Log;
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
import lombok.Data;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

public class AbstractServlet extends HttpServlet
{
    private final List<Mapping> GET_MAPPINGS = Lists.newArrayList();
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
        Log.log(ipAddress + " visited endpoint " + requestPath);

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
            resp.setStatus(HttpServletResponse.SC_OK);
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
                e.printStackTrace();
            }
        });
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

    protected AuthenticatedUser currentUser(HttpServletRequest request)
    {
        return currentUser(module, request);
    }

    public static AuthenticatedUser currentUser(HTTPDModule module, HttpServletRequest request)
    {
        AuthenticationManager manager = module.getAuthenticationManager();
        if (manager == null) return null;
        OAuth2Provider provider = manager.provider();
        if (provider == null) return null;
        return provider.lookup(request);
    }

    protected AuthenticatedUser currentStaff(HttpServletRequest request)
    {
        return currentStaff(module, request);
    }

    public static AuthenticatedUser currentStaff(HTTPDModule module, HttpServletRequest request)
    {
        AuthenticatedUser user = currentUser(module, request);
        return (user != null && user.staff()) ? user : null;
    }

    protected String signInPrompt(String action)
    {
        return signInPrompt(null, action);
    }

    protected String signInPrompt(HttpServletRequest request, String action)
    {
        return signInPrompt(module, request, action);
    }

    public static String signInPrompt(HTTPDModule module, HttpServletRequest request, String action)
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

    protected String readFile(InputStream filename)
    {
        return readFile(module, filename);
    }

    public static String readFile(HTTPDModule module, InputStream filename)
    {
        String base = module.getTemplate();
        String page = readFileReal(filename);
        String[] info = page.split("\\r?\\n", 3);
        String title = info.length > 0 ? info[0] : "";
        String activeKey = info.length > 1 ? info[1] : "";
        String content = info.length > 2 ? info[2] : "";
        base = base.replace("${TITLE}", title);
        if (!activeKey.isEmpty())
        {
            base = base.replace("${ACTIVE_" + activeKey + "}", "active");
        }
        base = base.replace("${ACTIVE_HOME}", "");
        base = base.replace("${ACTIVE_PLAYERS}", "");
        base = base.replace("${ACTIVE_INDEFBANS}", "");
        base = base.replace("${ACTIVE_COMMANDS}", "");
        base = base.replace("${ACTIVE_PUNISHMENTS}", "");
        base = base.replace("${ACTIVE_SCHEMATICS}", "");
        base = base.replace("${CONTENT}", content);
        return base;
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
