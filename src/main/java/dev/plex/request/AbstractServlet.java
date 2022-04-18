package dev.plex.request;

import com.google.common.collect.Lists;
import dev.plex.HTTPDModule;
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
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import org.eclipse.jetty.servlet.ServletHolder;

public class AbstractServlet extends HttpServlet
{
    private final List<Mapping> GET_MAPPINGS = Lists.newArrayList();

    public AbstractServlet()
    {
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
                HTTPDModule.context.addServlet(holder, getMapping.endpoint() + "*");
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

        Log.log(ipAddress + " visited endpoint " + req.getHttpServletMapping().getMatchValue());

        /*Enumeration<String> headerz = req.getHeaderNames();
        while (headerz.hasMoreElements()) {
            String header = headerz.nextElement();
            PlexLog.debug("Header: {0} Value {1}", header, req.getHeader(header));
        }*/
        GET_MAPPINGS.stream().filter(mapping -> mapping.getMapping().endpoint().substring(1, mapping.getMapping().endpoint().length() - 1).equalsIgnoreCase(req.getHttpServletMapping().getMatchValue())).forEach(mapping ->
        {
            if (mapping.headers != null)
            {
                for (String headers : mapping.headers.headers())
                {
                    String header = headers.split(";")[0];
                    String value = headers.split(";")[1];
                    resp.addHeader(header, value);
                }
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

    public static String readFile(InputStream filename)
    {
        String base = HTTPDModule.template;
        String page = readFileReal(filename);
        String[] info = page.split("\n", 3);
        System.out.println(Arrays.toString(info));
        base = base.replace("${TITLE}", info[0]);
        base = base.replace("${ACTIVE_" + info[1] + "}", "active\" aria-current=\"page");
        base = base.replace("${ACTIVE_HOME}", "");
        base = base.replace("${ACTIVE_ADMINS}", "");
        base = base.replace("${ACTIVE_INDEFBANS}", "");
        base = base.replace("${ACTIVE_LIST}", "");
        base = base.replace("${ACTIVE_PUNISHMENTS}", "");
        base = base.replace("${ACTIVE_SCHEMATICS}", "");
        base = base.replace("${CONTENT}", info[2]);
        return base;
    }

    public static String readFileReal(InputStream filename)
    {
        StringBuilder contentBuilder = new StringBuilder();
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(Objects.requireNonNull(filename)));
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
