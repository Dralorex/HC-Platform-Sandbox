package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.Yaml;
import org.bukkit.configuration.file.FileConfiguration;

/** Centralized custom file configs (separate from config.yml). */
public class ConfigFilesManager {
    private static final int LOBBY_SCHEMA = 1;
    private static final int GAMEFLOW_SCHEMA = 1;
    private static final int RESOURCEPACK_SCHEMA = 1;

    private final HungerGamesPlugin plugin;
    private FileConfiguration lobbyCfg;
    private FileConfiguration gameflowCfg;
    private FileConfiguration resourcepackCfg;

    public ConfigFilesManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        reloadAll();
    }

    public void reloadAll() {
        lobbyCfg = Yaml.loadOrCreate(plugin, "lobby.yml");
        gameflowCfg = Yaml.loadOrCreate(plugin, "gameflow.yml");
        resourcepackCfg = Yaml.loadOrCreate(plugin, "resourcepack.yml");
        ensureDefaults();
    }

    public FileConfiguration lobby() { return lobbyCfg; }
    public FileConfiguration gameflow() { return gameflowCfg; }
    public FileConfiguration resourcepack() { return resourcepackCfg; }

    public void saveLobby() { Yaml.save(plugin, "lobby.yml", lobbyCfg); }
    public void saveGameflow() { Yaml.save(plugin, "gameflow.yml", gameflowCfg); }
    public void saveResourcepack() { Yaml.save(plugin, "resourcepack.yml", resourcepackCfg); }

    private void ensureDefaults() {
        migrateIfNeeded();

        // lobby.yml
        if (!lobbyCfg.contains("lobby.size")) lobbyCfg.set("lobby.size", 64.0);
        if (!lobbyCfg.contains("lobby.pvpSwordMaterial")) lobbyCfg.set("lobby.pvpSwordMaterial", "IRON_SWORD");
        if (!lobbyCfg.contains("lobby.compassEnabled")) lobbyCfg.set("lobby.compassEnabled", true);
        if (!lobbyCfg.contains("lobby.compassSlot")) lobbyCfg.set("lobby.compassSlot", 5);
        if (!lobbyCfg.contains("lobby.mobSpawns.hostile")) lobbyCfg.set("lobby.mobSpawns.hostile", false);
        if (!lobbyCfg.contains("lobby.mobSpawns.passive")) lobbyCfg.set("lobby.mobSpawns.passive", false);
        saveLobby();

        // gameflow.yml
        if (!gameflowCfg.contains("caps.countedPlayers")) gameflowCfg.set("caps.countedPlayers", 100);
        if (!gameflowCfg.contains("caps.lobbyShardPlayers")) gameflowCfg.set("caps.lobbyShardPlayers", 20);
        if (!gameflowCfg.contains("lobbies.defaultShards")) gameflowCfg.set("lobbies.defaultShards", 6);
        if (!gameflowCfg.contains("lobbies.autoscaleEnabled")) gameflowCfg.set("lobbies.autoscaleEnabled", true);
        if (!gameflowCfg.contains("lobbies.maxShards")) gameflowCfg.set("lobbies.maxShards", 24);
        if (!gameflowCfg.contains("lobbies.shardSpacing")) gameflowCfg.set("lobbies.shardSpacing", 48.0);
        if (!gameflowCfg.contains("lobby.prewarmSeconds")) gameflowCfg.set("lobby.prewarmSeconds", 25);
        if (!gameflowCfg.contains("teams.randomizeDefault")) gameflowCfg.set("teams.randomizeDefault", false);
        if (!gameflowCfg.contains("teams.defaultSize")) gameflowCfg.set("teams.defaultSize", 5);
        if (!gameflowCfg.contains("parties.clearOnMatchEnd")) gameflowCfg.set("parties.clearOnMatchEnd", true);
        if (!gameflowCfg.contains("server.role")) gameflowCfg.set("server.role", "standalone");
        if (!gameflowCfg.contains("velocity.enabled")) gameflowCfg.set("velocity.enabled", false);
        if (!gameflowCfg.contains("velocity.serverId")) gameflowCfg.set("velocity.serverId", "local");
        if (!gameflowCfg.contains("sharedState.backend")) gameflowCfg.set("sharedState.backend", "REDIS");
        if (!gameflowCfg.contains("sharedState.redis.host")) gameflowCfg.set("sharedState.redis.host", "127.0.0.1");
        if (!gameflowCfg.contains("sharedState.redis.port")) gameflowCfg.set("sharedState.redis.port", 6379);
        if (!gameflowCfg.contains("sharedState.redis.password")) gameflowCfg.set("sharedState.redis.password", "");
        if (!gameflowCfg.contains("sharedState.redis.timeoutMs")) gameflowCfg.set("sharedState.redis.timeoutMs", 2000);
        saveGameflow();

        // resourcepack.yml
        if (!resourcepackCfg.contains("resourcepack.enabled")) resourcepackCfg.set("resourcepack.enabled", false);
        if (!resourcepackCfg.contains("resourcepack.url")) resourcepackCfg.set("resourcepack.url", "");
        if (!resourcepackCfg.contains("resourcepack.sha1")) resourcepackCfg.set("resourcepack.sha1", "");
        if (!resourcepackCfg.contains("resourcepack.enforce")) resourcepackCfg.set("resourcepack.enforce", false);
        if (!resourcepackCfg.contains("resourcepack.prompt")) resourcepackCfg.set("resourcepack.prompt", "This server uses a resource pack.");
        saveResourcepack();
    }

    private void migrateIfNeeded() {
        int lobbyVer = lobbyCfg.getInt("schemaVersion", 0);
        if (lobbyVer < LOBBY_SCHEMA) {
            // future migrations can branch here
            lobbyCfg.set("schemaVersion", LOBBY_SCHEMA);
        }

        int gameflowVer = gameflowCfg.getInt("schemaVersion", 0);
        if (gameflowVer < GAMEFLOW_SCHEMA) {
            gameflowCfg.set("schemaVersion", GAMEFLOW_SCHEMA);
        }

        int resourcepackVer = resourcepackCfg.getInt("schemaVersion", 0);
        if (resourcepackVer < RESOURCEPACK_SCHEMA) {
            resourcepackCfg.set("schemaVersion", RESOURCEPACK_SCHEMA);
        }
    }
}

