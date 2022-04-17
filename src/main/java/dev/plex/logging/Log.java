package dev.plex.logging;

import dev.plex.HTTPDModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public class Log
{
    public static void log(String message, Object... strings)
    {
        for (int i = 0; i < strings.length; i++)
        {
            if (message.contains("{" + i + "}"))
            {
                message = message.replace("{" + i + "}", strings[i].toString());
            }
        }

        if (HTTPDModule.moduleConfig.getBoolean("server.logging"))
        {
            Bukkit.getConsoleSender().sendMessage(String.format(ChatColor.DARK_AQUA + "[Plex HTTPD] " + ChatColor.GRAY + "%s", message));
        }
    }
}
