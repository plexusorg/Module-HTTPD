package dev.plex.request;

import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import dev.plex.HTTPDModule;
import dev.plex.authentication.AuthenticatedUser;
import dev.plex.logging.Log;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

public class SchematicUploadServlet extends HttpServlet
{
    private static final Pattern schemNameMatcher = Pattern.compile("^[a-z0-9'!,_ -]{1,30}\\.schem(atic)?$", Pattern.CASE_INSENSITIVE);
    private final HTTPDModule module;
    private final Object publishLock = new Object();

    public SchematicUploadServlet(HTTPDModule module)
    {
        this.module = module;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        AuthenticatedUser user = AbstractServlet.currentStaff(module, request);
        if (user == null)
        {
            response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_FORBIDDEN, "You must sign in as staff to upload schematics."));
            return;
        }
        File worldeditFolder = HTTPDModule.getWorldeditFolder();
        if (worldeditFolder == null)
        {
            response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "WorldEdit is not installed."));
            return;
        }
        Part uploadPart;
        try
        {
            uploadPart = request.getPart("file");
        }
        catch (IllegalStateException e)
        {
            response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "That schematic is too large."));
            return;
        }
        if (uploadPart == null || uploadPart.getSubmittedFileName() == null)
        {
            response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "Missing schematic file."));
            return;
        }
        String filename = uploadPart.getSubmittedFileName().replaceAll("[^a-zA-Z0-9'!,_ .-]", "_");
        if (!schemNameMatcher.matcher(filename).matches())
        {
            response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "That is not a valid schematic filename."));
            return;
        }
        Path target = worldeditFolder.toPath().resolve(filename);
        if (schematicExists(worldeditFolder, filename))
        {
            response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_CONFLICT, "A schematic with the name " + filename + " already exists."));
            return;
        }
        Path temporary = Files.createTempFile(worldeditFolder.toPath(), ".httpd-upload-", "-" + filename);
        boolean published = false;
        try
        {
            try (var inputStream = uploadPart.getInputStream())
            {
                Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
            }

            ClipboardFormat schematicFormat = ClipboardFormats.findByFile(temporary.toFile());
            if (schematicFormat == null)
            {
                rejectInvalid(response, user, filename);
                return;
            }
            try (var reader = schematicFormat.getReader(new FileInputStream(temporary.toFile())))
            {
                reader.read();
            }
            catch (IOException | RuntimeException e)
            {
                rejectInvalid(response, user, filename);
                return;
            }

            synchronized (publishLock)
            {
                if (schematicExists(worldeditFolder, filename))
                {
                    response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_CONFLICT, "A schematic with the name " + filename + " already exists."));
                    return;
                }
                try
                {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                }
                catch (AtomicMoveNotSupportedException ignored)
                {
                    Files.move(temporary, target);
                }
                published = true;
            }
        }
        finally
        {
            if (!published) Files.deleteIfExists(temporary);
        }

        response.getWriter().println(JsonResponse.ok(response, "Successfully uploaded " + filename + "."));
        module.api().logging().info(user.username() + " uploaded schematic with filename: " + filename);
        Log.log("{0} (xf:{1}) uploaded schematic {2}", user.username(), user.userId(), filename);
    }

    private static boolean schematicExists(File folder, String filename)
    {
        File[] schematics = folder.listFiles();
        if (schematics == null) return false;
        for (File file : schematics)
        {
            if (HTTPDModule.fileNameEquals(file.getName(), filename)) return true;
        }
        return false;
    }

    private void rejectInvalid(HttpServletResponse response, AuthenticatedUser user, String filename) throws IOException
    {
        module.api().logging().info(user.username() + " FAILED to upload schematic with filename: " + filename);
        Log.log("{0} (xf:{1}) FAILED to upload schematic {2}", user.username(), user.userId(), filename);
        response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "Schematic is not a valid format."));
    }
}
