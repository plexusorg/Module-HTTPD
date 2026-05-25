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
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.regex.Pattern;

public class SchematicUploadServlet extends HttpServlet
{
    private static final Pattern schemNameMatcher = Pattern.compile("^[a-z0-9'!,_ -]{1,30}\\.schem(atic)?$", Pattern.CASE_INSENSITIVE);
    private final HTTPDModule module;

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
        File[] schematics = worldeditFolder.listFiles();
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
        String filename = uploadPart.getSubmittedFileName().replaceAll("[^a-zA-Z0-9'!,_ .-]", "_");
        if (!schemNameMatcher.matcher(filename).matches())
        {
            response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "That is not a valid schematic filename."));
            return;
        }
        boolean alreadyExists = schematics != null && Arrays.stream(schematics).anyMatch(file -> HTTPDModule.fileNameEquals(file.getName(), filename));
        if (alreadyExists)
        {
            response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_CONFLICT, "A schematic with the name " + filename + " already exists."));
            return;
        }
        InputStream inputStream = uploadPart.getInputStream();
        File schematicFile = new File(worldeditFolder, filename);
        FileUtils.copyInputStreamToFile(inputStream, schematicFile);
        ClipboardFormat schematicFormat = ClipboardFormats.findByFile(schematicFile);
        if (schematicFormat == null)
        {
            module.api().logging().info(user.username() + " FAILED to upload schematic with filename: " + filename);
            Log.log("{0} (xf:{1}) FAILED to upload schematic {2}", user.username(), user.userId(), filename);
            response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "Schematic is not a valid format."));
            FileUtils.deleteQuietly(schematicFile);
            return;
        }
        try
        {
            schematicFormat.getReader(new FileInputStream(schematicFile));
        }
        catch (IOException e)
        {
            module.api().logging().info(user.username() + " FAILED to upload schematic with filename: " + filename);
            Log.log("{0} (xf:{1}) FAILED to upload schematic {2}", user.username(), user.userId(), filename);
            response.getWriter().println(JsonResponse.error(response, HttpServletResponse.SC_BAD_REQUEST, "Schematic is not a valid format."));
            FileUtils.deleteQuietly(schematicFile);
            return;
        }
        inputStream.close();
        response.getWriter().println(JsonResponse.ok(response, "Successfully uploaded " + filename + "."));
        module.api().logging().info(user.username() + " uploaded schematic with filename: " + filename);
        Log.log("{0} (xf:{1}) uploaded schematic {2}", user.username(), user.userId(), filename);
    }
}
