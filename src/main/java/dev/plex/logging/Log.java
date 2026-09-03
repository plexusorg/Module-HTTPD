package dev.plex.logging;

import dev.plex.api.config.ModuleConfiguration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.Logger;

public class Log
{
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z");

    private final BooleanSupplier consoleLoggingEnabled;
    private final BooleanSupplier fileLoggingEnabled;
    private final File accessLogFile;
    private final Logger logging;
    private BufferedWriter writer;
    private File writerTarget;
    private long writerBytes;
    private final long maxBytes;
    private final int retainedFiles;
    private final long flushIntervalMillis;
    private long lastFlushMillis;
    private long fileUnavailableUntilMillis;
    private volatile boolean active = true;

    public Log(ModuleConfiguration moduleConfig, File target, Logger loggingApi)
    {
        consoleLoggingEnabled = () -> moduleConfig.getBoolean("server.logging.console", false);
        fileLoggingEnabled = () -> moduleConfig.getBoolean("server.logging.file", true);
        accessLogFile = target;
        maxBytes = Math.max(1024L, moduleConfig.getLong("server.logging.max-bytes", 10L * 1024L * 1024L));
        retainedFiles = Math.max(1, moduleConfig.getInt("server.logging.retained-files", 5));
        flushIntervalMillis = Math.max(100L, moduleConfig.getLong("server.logging.flush-interval-ms", 1000L));
        logging = loggingApi;
    }

    public void log(String message, Object... strings)
    {
        if (!active)
        {
            return;
        }
        String formatted = format(message, strings);
        writeFile(formatted);
        if (consoleLoggingEnabled.getAsBoolean())
        {
            logging.info("[HTTPD] {}", formatted);
        }
    }

    public synchronized void shutdown()
    {
        active = false;
        if (writer != null)
        {
            try
            {
                writer.flush();
                writer.close();
            }
            catch (IOException exception)
            {
                logging.warn("[HTTPD] Failed to close access log", exception);
            }
            writer = null;
            writerTarget = null;
            writerBytes = 0L;
        }
        fileUnavailableUntilMillis = 0L;
    }

    private String format(String message, Object... strings)
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

    private synchronized void writeFile(String formatted)
    {
        if (!active) return;
        if (!fileLoggingEnabled.getAsBoolean()) return;
        if (System.currentTimeMillis() < fileUnavailableUntilMillis) return;
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
                writerBytes = target.length();
                lastFlushMillis = System.currentTimeMillis();
            }
            catch (IOException e)
            {
                suspendFileLogging("open access log " + target, e);
                return;
            }
        }
        try
        {
            String line = STAMP.format(ZonedDateTime.now()) + " " + formatted;
            long lineBytes = line.getBytes(StandardCharsets.UTF_8).length + System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
            if (writerBytes > 0L && writerBytes + lineBytes > maxBytes)
            {
                rotate(target);
            }
            writer.write(line);
            writer.newLine();
            writerBytes += lineBytes;
            long now = System.currentTimeMillis();
            if (now - lastFlushMillis >= flushIntervalMillis)
            {
                writer.flush();
                lastFlushMillis = now;
            }
        }
        catch (IOException e)
        {
            suspendFileLogging("write access log", e);
        }
    }

    private void suspendFileLogging(String operation, IOException error)
    {
        try
        {
            if (writer != null) writer.close();
        }
        catch (IOException closeFailure)
        {
            error.addSuppressed(closeFailure);
        }
        writer = null;
        writerTarget = null;
        writerBytes = 0L;
        fileUnavailableUntilMillis = System.currentTimeMillis() + 30_000L;
        logging.warn("[HTTPD] Failed to {}; pausing file logging for 30 seconds", operation, error);
    }

    private void rotate(File target) throws IOException
    {
        if (writer != null)
        {
            writer.flush();
            writer.close();
            writer = null;
        }

        for (int i = retainedFiles; i >= 1; i--)
        {
            File source = i == 1 ? target : new File(target.getPath() + "." + (i - 1));
            if (!source.exists()) continue;
            File destination = new File(target.getPath() + "." + i);
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        writer = new BufferedWriter(new FileWriter(target, false));
        writerTarget = target;
        writerBytes = 0L;
        lastFlushMillis = System.currentTimeMillis();
    }
}
