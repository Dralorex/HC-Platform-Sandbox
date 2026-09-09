package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.Yaml;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/** Per-template match settings stored in {@code world-game-settings.yml}. */
public class WorldSettingsManager {
    private static final String FILE = "world-game-settings.yml";

    private final HungerGamesPlugin plugin;
    private FileConfiguration cfg;

    public WorldSettingsManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cfg = Yaml.loadOrCreate(plugin, FILE);
        if (!cfg.isConfigurationSection("templates")) {
            cfg.createSection("templates");
        }
        save();
    }

    public void save() {
        Yaml.save(plugin, FILE, cfg);
    }

    public FileConfiguration config() {
        return cfg;
    }

    /** Null or unknown template key uses global {@code config.yml} defaults only. */
    public WorldGameSettings resolve(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            return new WorldGameSettings(plugin, null);
        }
        String path = "templates." + templateKey.toLowerCase(Locale.ROOT);
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        return new WorldGameSettings(plugin, sec);
    }

    public ConfigurationSection sectionForTemplate(String templateKey, boolean create) {
        String path = "templates." + templateKey.toLowerCase(Locale.ROOT);
        if (create && !cfg.isConfigurationSection(path)) {
            cfg.createSection(path);
            save();
        }
        return cfg.getConfigurationSection(path);
    }

    public void setBoolean(String templateKey, String field, boolean value) {
        ConfigurationSection sec = sectionForTemplate(templateKey, true);
        sec.set(field, value);
        save();
    }

    public void setInt(String templateKey, String field, int value) {
        ConfigurationSection sec = sectionForTemplate(templateKey, true);
        sec.set(field, value);
        save();
    }

    public void setLong(String templateKey, String field, long value) {
        ConfigurationSection sec = sectionForTemplate(templateKey, true);
        sec.set(field, value);
        save();
    }

    public void setDouble(String templateKey, String field, double value) {
        ConfigurationSection sec = sectionForTemplate(templateKey, true);
        sec.set(field, value);
        save();
    }

    public void setString(String templateKey, String field, String value) {
        ConfigurationSection sec = sectionForTemplate(templateKey, true);
        sec.set(field, value);
        save();
    }
       
    public void addBorderShrinkPhase(String templateKey, BorderShrinkPhase phase) {
        ConfigurationSection sec = sectionForTemplate(templateKey, true);
        if (!sec.isConfigurationSection("borderShrinkPhases")) {
            sec.createSection("borderShrinkPhases");
        }
        ConfigurationSection phases = sec.getConfigurationSection("borderShrinkPhases");
        String key = "phase_" + System.currentTimeMillis();
        phases.set(key + ".delaySeconds", phase.delaySeconds());
        phases.set(key + ".radiusStart", phase.radiusStart());
        phases.set(key + ".radiusEnd", phase.radiusEnd());
        phases.set(key + ".shrinkDurationSeconds", phase.shrinkDurationSeconds());
        save();
    }
    
    public void removeBorderShrinkPhase(String templateKey, int index) {
        ConfigurationSection sec = sectionForTemplate(templateKey, false);
        if (sec == null || !sec.isConfigurationSection("borderShrinkPhases")) return;
        ConfigurationSection phases = sec.getConfigurationSection("borderShrinkPhases");
        if (index < 0 || index >= phases.getKeys(false).size()) return;
        List<String> keys = new ArrayList<>(phases.getKeys(false));
        phases.set(keys.get(index), null);
        save();
    }
}
