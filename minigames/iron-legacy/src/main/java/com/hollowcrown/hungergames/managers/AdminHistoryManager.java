package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.Yaml;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Lightweight admin change history log. */
public class AdminHistoryManager {
    private static final String FILE = "admin-history.yml";
    private static final int MAX_ENTRIES = 500;

    private final HungerGamesPlugin plugin;
    private FileConfiguration cfg;

    public AdminHistoryManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cfg = Yaml.loadOrCreate(plugin, FILE);
        if (!cfg.contains("schemaVersion")) cfg.set("schemaVersion", 1);
        if (!cfg.isList("entries")) cfg.set("entries", new ArrayList<String>());
        save();
    }

    public void save() {
        Yaml.save(plugin, FILE, cfg);
    }

    public void log(String actor, String action, String details) {
        List<String> entries = cfg.getStringList("entries");
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        String line = ts + " | " + safe(actor) + " | " + safe(action) + " | " + safe(details);
        entries.add(line);
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(entries.size() - MAX_ENTRIES, entries.size()));
        }
        cfg.set("entries", entries);
        save();
    }

    public List<String> recent(int n) {
        List<String> entries = cfg.getStringList("entries");
        if (entries.isEmpty()) return List.of();
        int count = Math.max(1, n);
        int from = Math.max(0, entries.size() - count);
        return entries.subList(from, entries.size());
    }

    private String safe(String raw) {
        if (raw == null || raw.isBlank()) return "-";
        return raw.replace('\n', ' ').replace('\r', ' ').trim();
    }
}

