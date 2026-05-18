package dev.plex.request;

import dev.plex.logging.Log;
import dev.plex.request.impl.PlayerInventoryBroadcaster;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;

public class PlayerInventoryStreamServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        if (AbstractServlet.currentStaff(request) == null)
        {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String uuidStr = request.getParameter("uuid");
        if (uuidStr == null)
        {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        final UUID uuid;
        try
        {
            uuid = UUID.fromString(uuidStr);
        }
        catch (IllegalArgumentException e)
        {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String ipAddress = request.getRemoteAddr();
        if ("127.0.0.1".equals(ipAddress))
        {
            String forwarded = request.getHeader("X-FORWARDED-FOR");
            if (forwarded != null) ipAddress = forwarded;
        }
        Log.log(ipAddress + " opened inventory stream for " + uuid);

        PlayerInventoryBroadcaster broadcaster = PlayerInventoryBroadcaster.get();
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
        response.setHeader("X-Accel-Buffering", "no");

        final AsyncContext ctx = request.startAsync();
        ctx.setTimeout(0L);
        ctx.addListener(new AsyncListener()
        {
            @Override public void onComplete(AsyncEvent event) { broadcaster.removeSubscriber(uuid, ctx); }
            @Override public void onTimeout(AsyncEvent event)  { broadcaster.removeSubscriber(uuid, ctx); }
            @Override public void onError(AsyncEvent event)    { broadcaster.removeSubscriber(uuid, ctx); }
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

        if (!broadcaster.addSubscriber(uuid, ctx, writer))
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            ctx.complete();
            return;
        }

        try
        {
            writer.write("retry: 5000\n\n");
            writer.write("data: ");
            writer.write(broadcaster.currentPayload(uuid));
            writer.write("\n\n");
            writer.flush();
            if (writer.checkError())
            {
                broadcaster.removeSubscriber(uuid, ctx);
                ctx.complete();
            }
        }
        catch (Throwable t)
        {
            broadcaster.removeSubscriber(uuid, ctx);
            try { ctx.complete(); } catch (Throwable ignored) {}
        }
    }
}
