package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.Yaml;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** File-backed icon resolver for GUIs (material or player-head owner). */
public class IconManager {
    private static final String FILE = "icons.yml";
    private static final Pattern TEXTURE_URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"");
    private final HungerGamesPlugin plugin;
    private FileConfiguration cfg;

    public IconManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cfg = Yaml.loadOrCreate(plugin, FILE);
        if (!cfg.isConfigurationSection("icons")) {
            cfg.createSection("icons.default");
            cfg.set("icons.default.type", "MATERIAL");
            cfg.set("icons.default.value", "PLAYER_HEAD");
            cfg.createSection("icons.nether");
            cfg.set("icons.nether.type", "MATERIAL");
            cfg.set("icons.nether.value", "NETHERRACK");
            cfg.createSection("icons.end");
            cfg.set("icons.end.type", "MATERIAL");
            cfg.set("icons.end.value", "END_STONE");
            cfg.createSection("icons.desert");
            cfg.set("icons.desert.type", "MATERIAL");
            cfg.set("icons.desert.value", "SAND");
            cfg.createSection("icons.snow");
            cfg.set("icons.snow.type", "MATERIAL");
            cfg.set("icons.snow.value", "SNOW_BLOCK");
            Yaml.save(plugin, FILE, cfg);
        }
    }

    public ItemStack buildIcon(String iconKey, String name, List<String> lore) {
        String key = (iconKey == null || iconKey.isBlank()) ? "default" : iconKey.toLowerCase(Locale.ROOT);
        ConfigurationSection sec = cfg.getConfigurationSection("icons." + key);
        if (sec == null) sec = cfg.getConfigurationSection("icons.default");

        String type = sec != null ? sec.getString("type", "MATERIAL") : "MATERIAL";
        String value = sec != null ? sec.getString("value", "PLAYER_HEAD") : "PLAYER_HEAD";
        ItemStack item = resolve(type, value);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            if (lore != null) {
                meta.lore(lore.stream()
                        .map(LegacyComponentSerializer.legacySection()::deserialize)
                        .collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public ValidationReport validateIcons() {
        ConfigurationSection root = cfg.getConfigurationSection("icons");
        if (root == null) {
            return new ValidationReport(0, 0, List.of("Missing icons section in icons.yml"));
        }

        int total = 0;
        int valid = 0;
        List<String> warnings = new ArrayList<>();

        for (String key : root.getKeys(false)) {
            total++;
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                warnings.add("icons." + key + " is not a section.");
                continue;
            }

            String type = sec.getString("type", "MATERIAL");
            String value = sec.getString("value", "");
            if ("HEAD".equalsIgnoreCase(type)) {
                if (value == null || value.isBlank()) {
                    warnings.add("icons." + key + " HEAD value is blank.");
                    continue;
                }
                // Valid if custom texture parse succeeds OR we can fall back to owner-name heads.
                if (parseTextureValue(value) == null && value.contains(" ")) {
                    warnings.add("icons." + key + " HEAD value looks invalid: " + value);
                    continue;
                }
                valid++;
                continue;
            }

            try {
                Material mat = Material.valueOf(value.toUpperCase(Locale.ROOT));
                if (!mat.isItem()) {
                    warnings.add("icons." + key + " MATERIAL is not an item: " + value);
                    continue;
                }
                valid++;
            } catch (Exception ignored) {
                warnings.add("icons." + key + " MATERIAL is invalid: " + value);
            }
        }

        return new ValidationReport(total, valid, Collections.unmodifiableList(warnings));
    }

    public List<String> listIconKeys() {
        ConfigurationSection root = cfg.getConfigurationSection("icons");
        if (root == null) return List.of("default");
        return Stream.concat(root.getKeys(false).stream(), Stream.of("default"))
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    private ItemStack resolve(String type, String value) {
        if ("HEAD".equalsIgnoreCase(type)) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta raw = head.getItemMeta();
            if (raw instanceof SkullMeta skull && value != null && !value.isBlank()) {
                if (!applyCustomHeadTexture(skull, value)) {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(value));
                }
                head.setItemMeta(skull);
            }
            return head;
        }

        try {
            Material m = Material.valueOf(value.toUpperCase(Locale.ROOT));
            return new ItemStack(m);
        } catch (Exception ignored) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }

    private boolean applyCustomHeadTexture(SkullMeta skull, String value) {
        String textureValue = parseTextureValue(value);
        if (textureValue == null) return false;

        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
            profile.setProperty(new ProfileProperty("textures", textureValue));
            skull.setPlayerProfile(profile);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String parseTextureValue(String value) {
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        // Direct URL support: https://textures.minecraft.net/texture/<id>
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            URL url = safeUrl(trimmed);
            if (url == null) return null;
            return encodeTextureUrl(url.toString());
        }

        // Common format: base64:<payload>
        if (trimmed.startsWith("base64:")) {
            String payload = trimmed.substring("base64:".length());
            return decodeBase64(payload) == null ? null : payload;
        }

        // Raw base64 payload support.
        if (decodeBase64(trimmed) != null) {
            return trimmed;
        }

        // Allow JSON payloads copied from skin tools.
        if (trimmed.startsWith("{") && trimmed.contains("textures")) {
            Matcher jsonMatcher = TEXTURE_URL_PATTERN.matcher(trimmed);
            if (!jsonMatcher.find()) return null;
            URL url = safeUrl(jsonMatcher.group(1));
            if (url == null) return null;
            return encodeTextureUrl(url.toString());
        }

        String base64Payload = trimmed;
        String decoded = decodeBase64(base64Payload);
        if (decoded == null) return null;

        Matcher matcher = TEXTURE_URL_PATTERN.matcher(decoded);
        if (!matcher.find()) return null;
        URL url = safeUrl(matcher.group(1));
        if (url == null) return null;
        return encodeTextureUrl(url.toString());
    }

    private String decodeBase64(String raw) {
        try {
            byte[] decoded = Base64.getDecoder().decode(raw);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private URL safeUrl(String raw) {
        try {
            return URI.create(raw).toURL();
        } catch (IllegalArgumentException | MalformedURLException ignored) {
            return null;
        }
    }

    private String encodeTextureUrl(String skinUrl) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public record ValidationReport(int total, int valid, List<String> warnings) {}
}

