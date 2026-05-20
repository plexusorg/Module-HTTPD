package dev.plex.request;

import dev.plex.logging.Log;
import dev.plex.request.impl.StatsBroadcaster;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

public class StatsStreamServlet extends HttpServlet
{
    private final StatsBroadcaster broadcaster;

    public StatsStreamServlet(StatsBroadcaster broadcaster)
    {
        this.broadcaster = broadcaster;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        String ipAddress = request.getRemoteAddr();
        if ("127.0.0.1".equals(ipAddress))
        {
            String forwarded = request.getHeader("X-FORWARDED-FOR");
            if (forwarded != null) ipAddress = forwarded;
        }
        Log.log(ipAddress + " opened SSE stream /api/stats/stream");

        if (broadcaster.atCapacity())
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "30");
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        // Disable proxy buffering (nginx and friends) so frames reach the client promptly.
        response.setHeader("X-Accel-Buffering", "no");

        final AsyncContext ctx = request.startAsync();
        ctx.setTimeout(0L);
        ctx.addListener(new AsyncListener()
        {
            @Override public void onComplete(AsyncEvent event) { broadcaster.removeSubscriber(ctx); }
            @Override public void onTimeout(AsyncEvent event)  { broadcaster.removeSubscriber(ctx); }
            @Override public void onError(AsyncEvent event)    { broadcaster.removeSubscriber(ctx); }
            @Override public void onStartAsync(AsyncEvent event) {}
        });

        PrintWriter writer;
        try
        {
            writer = response.getWriter();
        }
        catch (IOException e)
        {
            ctx.complete();
            return;
        }

        if (!broadcaster.addSubscriber(ctx, writer))
        {
            // Lost the capacity race that the atCapacity check above tried to avoid.
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            ctx.complete();
            return;
        }

        try
        {
            // Hint to the browser: if the connection drops, wait this long before reconnecting.
            writer.write("retry: 5000\n\n");
            writer.write("data: ");
            writer.write(broadcaster.currentPayload());
            writer.write("\n\n");
            writer.flush();
            if (writer.checkError())
            {
                broadcaster.removeSubscriber(ctx);
                ctx.complete();
            }
        }
        catch (Throwable t)
        {
            broadcaster.removeSubscriber(ctx);
            try { ctx.complete(); } catch (Throwable ignored) {}
        }
    }
}
