package dev.rui.prueba.util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<String, String> TEXTS = new HashMap<>();
    private static String prefix = "";

    private Messages() {
    }

    public static void load(Plugin plugin) {
        plugin.saveResource("messages.yml", false);
        load(YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml")));
    }

    public static void load(YamlConfiguration source) {
        TEXTS.clear();
        for (String key : source.getKeys(true)) {
            if (source.isString(key)) {
                TEXTS.put(key, source.getString(key));
            }
        }
        prefix = TEXTS.getOrDefault("prefix", "");
    }

    public static String raw(String key, String... placeholders) {
        String text = TEXTS.get(key);
        if (text == null) {
            return "<red>?" + key + "?";
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return text;
    }

    public static Component component(String key, String... placeholders) {
        return MM.deserialize(raw(key, placeholders));
    }

    public static void send(CommandSender target, String key, String... placeholders) {
        target.sendMessage(MM.deserialize(prefix + raw(key, placeholders)));
    }

    public static Component mm(String text) {
        return MM.deserialize(text);
    }
}
