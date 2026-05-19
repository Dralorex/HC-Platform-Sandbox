package com.hollowcrown.hub.template;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Persists template → mechanic bindings under {@code plugins/HollowCrownHub/templates.yml}.
 */
public final class HubTemplateRegistry {

    private static final int FILE_VERSION = 1;
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");
    /**
     * World folder path under the server container (case preserved). Allows nested Hollow Crown managed folders,
     * e.g. {@code hch-worlds/templates/my_map}.
     */
    private static final Pattern SOURCE_FOLDER_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}(?:/[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}){0,8}$");

    private final HollowCrownHubPlugin plugin;
    private final File file;
    private final Map<String, HubTemplateEntry> entries = new LinkedHashMap<>();

    public HubTemplateRegistry(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "templates.yml");
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        loadFromDisk();
    }

    public List<HubTemplateEntry> list() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public Optional<HubTemplateEntry> get(String templateKey) {
        if (templateKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(normalizeKey(templateKey)));
    }

    public void upsert(String templateKey, String mechanicId, String displayName, String sourceWorldName) throws IllegalArgumentException {
        String k = validateKey(templateKey);
        String m = validateKey(mechanicId);
        String rawSrc = sourceWorldName == null ? "" : sourceWorldName.trim();
        HubTemplateEntry cur = entries.get(k);
        int mu = cur != null ? cur.queueMaxUnitSize() : 8;
        boolean so = cur != null && cur.queueSoloOnly();
        boolean sa = cur != null && cur.queueSoloAutoMerge();
        boolean jq = cur != null && cur.joinQueueFirst();
        final String src;
        if (rawSrc.isEmpty()) {
            src = cur != null ? cur.sourceWorldName() : "";
        } else {
            src = validateSourceFolder(rawSrc);
        }
        HubTemplateEntry entry = new HubTemplateEntry(k, m, displayName == null ? "" : displayName, src, mu, so, sa, jq);
        entries.put(k, entry);
        saveToDisk();
        HubLog.info(plugin, "action=template_upsert", "templateKey=" + k, "mechanicId=" + m);
    }

    public void setSourceWorld(String templateKey, String sourceFolder) throws IllegalArgumentException {
        String k = validateKey(templateKey);
        HubTemplateEntry cur = entries.get(k);
        if (cur == null) {
            throw new IllegalArgumentException("Unknown template: " + templateKey);
        }
        String src = sourceFolder == null ? "" : validateSourceFolder(sourceFolder);
        entries.put(k, cur.withSourceWorldName(src));
        saveToDisk();
        HubLog.info(plugin, "action=template_setsource", "templateKey=" + k, "sourceWorldName=" + src);
    }

    static String validateSourceFolder(String raw) throws IllegalArgumentException {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Source folder empty");
        }
        String t = raw.trim().replace('\\', '/');
        while (t.startsWith("/")) {
            t = t.substring(1);
        }
        if (!SOURCE_FOLDER_PATTERN.matcher(t).matches()) {
            throw new IllegalArgumentException("Invalid source folder path: " + raw);
        }
        return t;
    }

    public boolean remove(String templateKey) {
        if (templateKey == null) {
            return false;
        }
        String k = normalizeKey(templateKey);
        if (entries.remove(k) != null) {
            saveToDisk();
            HubLog.info(plugin, "action=template_remove", "templateKey=" + k);
            return true;
        }
        return false;
    }

    /**
     * Updates queue policy fields; pass {@code null} to leave a field unchanged.
     */
    public void updateQueuePolicy(
            String templateKey,
            Integer maxUnitSize,
            Boolean soloOnly,
            Boolean soloAutoMerge,
            Boolean joinQueueFirst
    ) throws IllegalArgumentException {
        String k = validateKey(templateKey);
        HubTemplateEntry cur = entries.get(k);
        if (cur == null) {
            throw new IllegalArgumentException("Unknown template: " + templateKey);
        }
        int mu = maxUnitSize != null ? maxUnitSize : cur.queueMaxUnitSize();
        boolean so = soloOnly != null ? soloOnly : cur.queueSoloOnly();
        boolean sa = soloAutoMerge != null ? soloAutoMerge : cur.queueSoloAutoMerge();
        boolean jq = joinQueueFirst != null ? joinQueueFirst : cur.joinQueueFirst();
        entries.put(k, new HubTemplateEntry(
                cur.templateKey(), cur.mechanicId(), cur.displayName(), cur.sourceWorldName(),
                mu, so, sa, jq));
        saveToDisk();
        HubLog.info(plugin, "action=template_setqueue", "templateKey=" + k,
                "maxUnit=" + mu, "soloOnly=" + so, "soloMerge=" + sa, "joinQueueFirst=" + jq);
    }

    private static String normalizeKey(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Same validation as YAML keys; exposed for CLI/bootstrap helpers. */
    public static String validateTemplateKeySyntax(String raw) throws IllegalArgumentException {
        String k = normalizeKey(raw);
        if (!KEY_PATTERN.matcher(k).matches()) {
            throw new IllegalArgumentException(
                    "Invalid key (use letters, numbers, underscore, hyphen; max 64 chars): " + raw);
        }
        return k;
    }

    private static String validateKey(String raw) {
        return validateTemplateKeySyntax(raw);
    }

    private void loadFromDisk() {
        entries.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int ver = yaml.getInt("version", 0);
        if (ver > FILE_VERSION) {
            plugin.getLogger().warning("templates.yml version " + ver + " is newer than supported " + FILE_VERSION);
        }
        ConfigurationSection sec = yaml.getConfigurationSection("templates");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection row = sec.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            String mechanicId = row.getString("mechanicId", "");
            String displayName = row.getString("displayName", "");
            String sourceWorld = row.getString("sourceWorldName", "");
            int mu = row.getInt("queueMaxUnitSize", 8);
            if (mu < 1) {
                mu = 8;
            }
            boolean so = row.getBoolean("queueSoloOnly", false);
            boolean sa = row.getBoolean("queueSoloAutoMerge", false);
            boolean jq = row.getBoolean("joinQueueFirst", false);
            try {
                String nk = normalizeKey(key);
                entries.put(nk, new HubTemplateEntry(nk, mechanicId, displayName, sourceWorld, mu, so, sa, jq));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping bad template row '" + key + "': " + ex.getMessage());
            }
        }
    }

    private void saveToDisk() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", FILE_VERSION);
        for (HubTemplateEntry e : entries.values()) {
            String path = "templates." + e.templateKey();
            yaml.set(path + ".mechanicId", e.mechanicId());
            yaml.set(path + ".displayName", e.displayName());
            yaml.set(path + ".sourceWorldName", e.sourceWorldName());
            yaml.set(path + ".queueMaxUnitSize", e.queueMaxUnitSize());
            yaml.set(path + ".queueSoloOnly", e.queueSoloOnly());
            yaml.set(path + ".queueSoloAutoMerge", e.queueSoloAutoMerge());
            yaml.set(path + ".joinQueueFirst", e.joinQueueFirst());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save templates.yml", ex);
        }
    }
}
