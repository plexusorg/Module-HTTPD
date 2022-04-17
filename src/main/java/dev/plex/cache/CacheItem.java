package dev.plex.cache;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class CacheItem
{
    public String path;
    public byte[] file;
    public long timestamp;

    public CacheItem(File f) throws IOException
    {
        this.path = f.getPath();
        this.file = f.length() >= 1048576 ? null : Files.readAllBytes(f.toPath());
        this.timestamp = System.currentTimeMillis();
    }
}
