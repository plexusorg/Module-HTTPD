package dev.plex.request;

import dev.plex.HTTPDModule;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.regex.Pattern;

public class SchematicUploadServlet extends HttpServlet
{
    private static final Pattern schemNameMatcher = Pattern.compile("^[a-z0-9'!,_ -]{1,30}\\.schem(atic)?$", Pattern.CASE_INSENSITIVE);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        File worldeditFolder = HTTPDModule.getWorldeditFolder();
        if (worldeditFolder == null)
        {
            resp.getWriter().println(schematicUploadBadHTML("Worldedit is not installed!"));
            return;
        }
        File[] schematics = worldeditFolder.listFiles();
        Part uploadPart;
        try
        {
            uploadPart = req.getPart("file");
        }
        catch (IllegalStateException e)
        {
            resp.getWriter().println(schematicUploadBadHTML("That schematic is too large!"));
            return;
        }
        String filename = uploadPart.getSubmittedFileName().replaceAll("[^a-zA-Z0-9'!,_ .-]", "_");
        if (!schemNameMatcher.matcher(filename).matches())
        {
            resp.getWriter().println(schematicUploadBadHTML("That is not a valid schematic filename!"));
            return;
        }
        boolean alreadyExists = schematics != null && Arrays.stream(schematics).anyMatch(file -> HTTPDModule.fileNameEquals(file.getName(), filename));
        if (alreadyExists)
        {
            resp.getWriter().println(schematicUploadBadHTML("A schematic with the name <b>" + filename + "</b> already exists!"));
            return;
        }
        InputStream inputStream = uploadPart.getInputStream();
        Files.copy(inputStream, new File(worldeditFolder, filename).toPath(), StandardCopyOption.REPLACE_EXISTING);
        inputStream.close();
        resp.getWriter().println(schematicUploadGoodHTML("Successfully uploaded <b>" + filename + "."));
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
