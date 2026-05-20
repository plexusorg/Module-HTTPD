package dev.plex.request;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.plex.util.adapter.ZonedDateTimeAdapter;
import jakarta.servlet.http.HttpServletResponse;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonResponse
{
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
            .create();

    private JsonResponse()
    {
    }

    public static String json(HttpServletResponse response, Object value)
    {
        response.setContentType("application/json; charset=UTF-8");
        return GSON.toJson(value);
    }

    public static String error(HttpServletResponse response, int status, String message)
    {
        response.setStatus(status);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", message);
        return json(response, body);
    }

    public static String ok(HttpServletResponse response, String message)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("message", message);
        return json(response, body);
    }
}
