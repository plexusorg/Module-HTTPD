package dev.plex.cache;

import com.google.common.collect.EvictingQueue;

import java.io.File;
import java.io.IOException;
import java.util.Queue;

public class FileCache
{
    private final Queue<CacheItem> cache = EvictingQueue.create(15);

    public byte[] getFile(File file) throws IOException
    {
        return getFile(file.getPath());
    }

    public byte[] getFile(String path) throws IOException
    {
        CacheItem theItem = cache.stream().filter(cacheItem -> cacheItem.path.equals(path)).findFirst().orElse(null);
        if (theItem == null)
        {
            theItem = new CacheItem(new File(path));
            if (theItem.file.length < 1048576) cache.add(theItem);
        }
        if (System.currentTimeMillis() - theItem.timestamp > 3 * 60 * 1000) // 3 minutes
        {
            cache.remove(theItem);
            theItem = new CacheItem(new File(path));
            if (theItem.file.length < 1048576) cache.add(theItem);
        }
        return theItem.file;
    }
}
