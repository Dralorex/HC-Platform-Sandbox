package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.Yaml;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent kill leaderboard storage. */
public class KillLeaderboardManager {
    private static final String FILE = "kills.yml";
    private static final String JOURNAL_FILE = "kills.log";

    private final HungerGamesPlugin plugin;
    private FileConfiguration cfg;

    public KillLeaderboardManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        cfg = Yaml.loadOrCreate(plugin, FILE);
        if (!cfg.contains("schemaVersion")) cfg.set("schemaVersion", 1);
        if (!cfg.isConfigurationSection("kills")) cfg.createSection("kills");
        rebuildFromJournal();
        save();
    }

    public synchronized void save() {
        Yaml.save(plugin, FILE, cfg);
    }

    public synchronized void addKill(UUID playerId) {
        appendJournal(playerId);
        String path = "kills." + playerId;
        int current = cfg.getInt(path, 0);
        cfg.set(path, current + 1);
        save();
    }

    public synchronized int getKills(UUID playerId) {
        return cfg.getInt("kills." + playerId, 0);
    }

    public List<Entry> top(int limit) {
        var sec = cfg.getConfigurationSection("kills");
        if (sec == null) return List.of();
        List<Entry> out = new ArrayList<>();
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                int kills = cfg.getInt("kills." + key, 0);
                OfflinePlayer off = Bukkit.getOfflinePlayer(id);
                String name = off.getName() == null ? key : off.getName();
                out.add(new Entry(id, name, kills));
            } catch (Exception ignored) {}
        }
        out.sort(Comparator.comparingInt(Entry::kills).reversed().thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        if (out.size() <= limit) return out;
        return out.subList(0, limit);
    }

    public record Entry(UUID id, String name, int kills) {}

    private void rebuildFromJournal() {
        File log = new File(plugin.getDataFolder(), JOURNAL_FILE);
        if (!log.exists()) return;
        Map<UUID, Integer> totals = new HashMap<>();
        try {
            for (String line : Files.readAllLines(log.toPath(), StandardCharsets.UTF_8)) {
                String raw = line == null ? "" : line.trim();
                if (raw.isEmpty()) continue;
                UUID id = UUID.fromString(raw);
                totals.put(id, totals.getOrDefault(id, 0) + 1);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed reading kills journal; keeping current kills.yml values. " + ex.getMessage());
            return;
        }

        cfg.set("kills", null);
        cfg.createSection("kills");
        for (Map.Entry<UUID, Integer> e : totals.entrySet()) {
            cfg.set("kills." + e.getKey(), e.getValue());
        }
    }

    private void appendJournal(UUID playerId) {
        File log = new File(plugin.getDataFolder(), JOURNAL_FILE);
        try {
            plugin.getDataFolder().mkdirs();
            if (!log.exists()) log.createNewFile();
            try (BufferedWriter out = Files.newBufferedWriter(
                    log.toPath(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE)) {
                out.write(playerId.toString());
                out.newLine();
                out.flush();
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to append kill journal: " + ex.getMessage());
        }
    }
}

