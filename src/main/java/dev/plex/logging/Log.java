package dev.plex.logging;

import dev.plex.HTTPDModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            Bukkit.getConsoleSender().sendMessage(Component.text("[Plex HTTPD] ").color(NamedTextColor.DARK_AQUA).append(Component.text(message).color(NamedTextColor.GRAY)));
        }
    }
}
