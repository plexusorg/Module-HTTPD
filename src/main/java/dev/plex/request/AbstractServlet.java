package dev.plex.request;

import com.google.common.collect.Lists;
import dev.plex.HTTPDModule;
import dev.plex.util.PlexLog;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
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

        PlexLog.debug("Context Path: " + req.getHttpServletMapping().getMatchValue());

        String ipAddress = req.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null)
        {
            ipAddress = req.getRemoteAddr();
        }
        PlexLog.debug("HTTP Remote IP: " + ipAddress);
        PlexLog.debug("HTTP Local IP: " + req.getLocalAddr());

        /*Enumeration<String> headerz = req.getHeaderNames();
        while (headerz.hasMoreElements()) {
            String header = headerz.nextElement();
            PlexLog.debug("Header: {0} Value {1}", header, req.getHeader(header));
        }*/

        PlexLog.debug("-------------------------");
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


    @Data
    public class Mapping
    {
        private final Method method;
        private final GetMapping mapping;
        private MappingHeaders headers;
    }
}
