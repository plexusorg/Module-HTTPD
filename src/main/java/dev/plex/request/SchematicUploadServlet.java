package dev.plex.request;

import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import dev.plex.HTTPDModule;
import dev.plex.cache.DataUtils;
import dev.plex.player.PlexPlayer;
import dev.plex.util.PlexLog;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.regex.Pattern;

public class SchematicUploadServlet extends HttpServlet
{
    private static final Pattern schemNameMatcher = Pattern.compile("^[a-z0-9'!,_ -]{1,30}\\.schem(atic)?$", Pattern.CASE_INSENSITIVE);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        if (request.getRemoteAddr() == null)
        {
            response.getWriter().println(schematicUploadBadHTML("Your IP address could not be detected. Please ensure you are using IPv4."));
            return;
        }
        PlexPlayer plexPlayer = DataUtils.getPlayerByIP(request.getRemoteAddr());
        if (plexPlayer == null)
        {
            response.getWriter().println(schematicUploadBadHTML("Couldn't load your IP Address: " + request.getRemoteAddr() + ". Have you joined the server before?"));
            return;
        }
        PlexLog.debug("Plex-HTTPD using permissions check");
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(plexPlayer.getUuid());
        if (!HTTPDModule.getPermissions().playerHas(null, offlinePlayer, "plex.httpd.schematics.upload"))
        {
            response.getWriter().println(schematicUploadBadHTML("You do not have permission to upload schematics."));
            return;
        }
        File worldeditFolder = HTTPDModule.getWorldeditFolder();
        if (worldeditFolder == null)
        {
            response.getWriter().println(schematicUploadBadHTML("Worldedit is not installed!"));
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
            response.getWriter().println(schematicUploadBadHTML("That schematic is too large!"));
            return;
        }
        String filename = uploadPart.getSubmittedFileName().replaceAll("[^a-zA-Z0-9'!,_ .-]", "_");
        if (!schemNameMatcher.matcher(filename).matches())
        {
            response.getWriter().println(schematicUploadBadHTML("That is not a valid schematic filename!"));
            return;
        }
        boolean alreadyExists = schematics != null && Arrays.stream(schematics).anyMatch(file -> HTTPDModule.fileNameEquals(file.getName(), filename));
        if (alreadyExists)
        {
            response.getWriter().println(schematicUploadBadHTML("A schematic with the name <b>" + filename + "</b> already exists!"));
            return;
        }
        InputStream inputStream = uploadPart.getInputStream();
        File schematicFile = new File(worldeditFolder, filename);
        FileUtils.copyInputStreamToFile(inputStream, schematicFile);
        ClipboardFormat schematicFormat = ClipboardFormats.findByFile(schematicFile);
        if (schematicFormat == null)
        {
            PlexLog.log("IP Address: " + request.getRemoteAddr() + " FAILED to upload schematic with filename: " + filename);
            response.getWriter().println(schematicUploadBadHTML("Schematic is not a valid format."));
            FileUtils.deleteQuietly(schematicFile);
            return;
        }
        try
        {
            schematicFormat.getReader(new FileInputStream(schematicFile));
        }
        catch (IOException e)
        {
            PlexLog.log("IP Address: " + request.getRemoteAddr() + " FAILED to upload schematic with filename: " + filename);
            response.getWriter().println(schematicUploadBadHTML("Schematic is not a valid format."));
            FileUtils.deleteQuietly(schematicFile);
            return;
        }
        // Files.copy(inputStream, schematic.toPath(), StandardCopyOption.REPLACE_EXISTING);
        inputStream.close();
        response.getWriter().println(schematicUploadGoodHTML("Successfully uploaded <b>" + filename + "</b>."));
        PlexLog.log("IP Address: " + request.getRemoteAddr() + " uploaded schematic with filename: " + filename);
    }

    private String schematicUploadBadHTML(String message)
    {
        String file = AbstractServlet.readFile(this.getClass().getResourceAsStream("/httpd/schematic_upload_bad.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }

    private String schematicUploadGoodHTML(String message)
    {
        String file = AbstractServlet.readFile(this.getClass().getResourceAsStream("/httpd/schematic_upload_good.html"));
        file = file.replace("${MESSAGE}", message);
        return file;
    }
}
