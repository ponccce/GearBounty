package com.gearblock.bounty.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads lang/<language>.yml from the plugin folder. Any key missing there falls back to the
 * English file bundled in the jar, so an outdated translation never breaks the plugin.
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final YamlConfiguration lang;
    private final YamlConfiguration fallback;

    private Messages(YamlConfiguration lang, YamlConfiguration fallback) {
        this.lang = lang;
        this.fallback = fallback;
    }

    public static Messages load(JavaPlugin plugin, String language) {
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Language file lang/" + language + ".yml not found, using English.");
            file = new File(plugin.getDataFolder(), "lang/en.yml");
        }
        YamlConfiguration fallback = new YamlConfiguration();
        try (InputStream in = plugin.getResource("lang/en.yml")) {
            if (in != null) {
                fallback = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the bundled English messages: " + e.getMessage());
        }
        return new Messages(YamlConfiguration.loadConfiguration(file), fallback);
    }

    /** A chat message with the prefix. */
    public Component chat(String key, TagResolver... resolvers) {
        return MM.deserialize(raw("prefix") + raw(key), resolvers);
    }

    /** A message without the prefix (menu titles and item names). */
    public Component plain(String key, TagResolver... resolvers) {
        return MM.deserialize(raw(key), resolvers);
    }

    /** A list of lines, for item lore. */
    public List<Component> lines(String key, TagResolver... resolvers) {
        List<String> list = lang.isList(key) ? lang.getStringList(key) : fallback.getStringList(key);
        return list.stream().map(line -> MM.deserialize(line, resolvers)).toList();
    }

    private String raw(String key) {
        String value = lang.getString(key);
        if (value == null) {
            value = fallback.getString(key, "<red>Missing message: " + key);
        }
        return value;
    }
}
