package com.hollowcrown.hub.world;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/** Mutable hub-area settings (protection radius, pads, items) beside {@code config.yml} hub spawn. */
public final class HubWorldYaml {

    private final HollowCrownHubPlugin plugin;
    private final File file;
    private FileConfiguration cfg;

    public HubWorldYaml(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hub-world.yml");
        reload();
        ensureDefaults();
    }

    public FileConfiguration config() {
        return cfg;
    }

    public void reload() {
        if (!file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.getParentFile().mkdirs();
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create hub-world.yml", e);
            }
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save hub-world.yml", e);
        }
    }

    private void ensureDefaults() {
        if (!cfg.contains("protection.radiusBlocks")) {
            cfg.set("protection.radiusBlocks", 64.0);
        }
        if (!cfg.isList("pvpPads")) {
            cfg.set("pvpPads", new java.util.ArrayList<String>());
        }
        if (!cfg.contains("pvpSwordMaterial")) {
            cfg.set("pvpSwordMaterial", "IRON_SWORD");
        }
        if (!cfg.contains("compassEnabled")) {
            cfg.set("compassEnabled", true);
        }
        if (!cfg.contains("compassSlot")) {
            cfg.set("compassSlot", 5);
        }
        if (!cfg.contains("mobSpawns.hostile")) {
            cfg.set("mobSpawns.hostile", false);
        }
        if (!cfg.contains("mobSpawns.passive")) {
            cfg.set("mobSpawns.passive", false);
        }
        if (!cfg.contains("teleportNewPlayersToHub")) {
            cfg.set("teleportNewPlayersToHub", true);
        }
        setIfMissing(cfg, "items.hub.party.slot", 9);
        setIfMissing(cfg, "items.hub.party.material", "NETHER_STAR");
        setIfMissing(cfg, "items.hub.party.name", "Party");
        setIfMissing(cfg, "items.limbo.team.slot", 3);
        setIfMissing(cfg, "items.limbo.team.material", "NETHER_STAR");
        setIfMissing(cfg, "items.limbo.team.name", "Match Team");
        setIfMissing(cfg, "items.limbo.leave.slot", 9);
        setIfMissing(cfg, "items.limbo.leave.material", "BARRIER");
        setIfMissing(cfg, "items.limbo.leave.name", "Leave Lobby");
        setIfMissing(cfg, "items.locked", true);
        save();
    }

    private static void setIfMissing(FileConfiguration c, String path, Object value) {
        if (!c.contains(path)) {
            c.set(path, value);
        }
    }
}
