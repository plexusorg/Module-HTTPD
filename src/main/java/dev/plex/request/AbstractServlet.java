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
        if (ipAddress == null)
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
                Object object = mapping.method.invoke(this, req);
                resp.getWriter().println(object.toString());
            }
            catch (IOException | IllegalAccessException | InvocationTargetException e)
            {
                e.printStackTrace();
            }
        });
    }

    public String readFile(InputStream filename)
    {
        StringBuilder contentBuilder = new StringBuilder();
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(Objects.requireNonNull(filename)));
            String str;
            while ((str = in.readLine()) != null)
            {
                contentBuilder.append(str);
            }
            in.close();
        }
        catch (IOException ignored)
        {
        }
        return contentBuilder.toString();
    }

    @Data
    public static class Mapping
    {
        private final Method method;
        private final GetMapping mapping;
        private MappingHeaders headers;
    }
}
