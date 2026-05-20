package dev.plex.logging;

import dev.plex.config.ModuleConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BooleanSupplier;

public class Log
{
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z");

    private static BooleanSupplier consoleLoggingEnabled = () -> false;
    private static BooleanSupplier fileLoggingEnabled = () -> false;
    private static File accessLogFile;
    private static BufferedWriter writer;
    private static File writerTarget;

    public static synchronized void configure(ModuleConfig moduleConfig, File target)
    {
        consoleLoggingEnabled = () -> moduleConfig.getBoolean("server.logging.console", false);
        fileLoggingEnabled = () -> moduleConfig.getBoolean("server.logging.file", true);
        accessLogFile = target;
    }

    public static void log(String message, Object... strings)
    {
        String formatted = format(message, strings);
        writeFile(formatted);
        if (consoleLoggingEnabled.getAsBoolean())
        {
            Bukkit.getConsoleSender().sendMessage(Component.text("[Plex HTTPD] ").color(NamedTextColor.DARK_AQUA).append(Component.text(formatted).color(NamedTextColor.GRAY)));
        }
    }

    public static synchronized void shutdown()
    {
        if (writer != null)
        {
            try
            {
                writer.flush();
                writer.close();
            }
            catch (IOException ignored) {}
            writer = null;
            writerTarget = null;
        }
        consoleLoggingEnabled = () -> false;
        fileLoggingEnabled = () -> false;
        accessLogFile = null;
    }

    private static String format(String message, Object... strings)
    {
        for (int i = 0; i < strings.length; i++)
        {
            String token = "{" + i + "}";
            if (message.contains(token))
            {
                message = message.replace(token, strings[i] == null ? "null" : strings[i].toString());
            }
        }
        return message;
    }

    private static synchronized void writeFile(String formatted)
    {
        if (!fileLoggingEnabled.getAsBoolean()) return;
        File target = accessLogFile;
        if (target == null) return;
        if (writer == null || !target.equals(writerTarget))
        {
            try
            {
                if (writer != null) writer.close();
                target.getParentFile().mkdirs();
                writer = new BufferedWriter(new FileWriter(target, true));
                writerTarget = target;
            }
            catch (IOException e)
            {
                Bukkit.getLogger().warning("[Plex HTTPD] Failed to open access log " + target + ": " + e.getMessage());
                writer = null;
                writerTarget = null;
                return;
            }
        }
        try
        {
            writer.write(STAMP.format(ZonedDateTime.now()));
            writer.write(' ');
            writer.write(formatted);
            writer.newLine();
            writer.flush();
        }
        catch (IOException e)
        {
            Bukkit.getLogger().warning("[Plex HTTPD] Failed to write access log: " + e.getMessage());
        }
    }
}
