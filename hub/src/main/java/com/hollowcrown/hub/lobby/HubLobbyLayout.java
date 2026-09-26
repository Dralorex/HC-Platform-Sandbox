package com.hollowcrown.hub.lobby;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;

/**
 * Hub-owned lobby zone/spawn editing ({@code worlds.yml} under HCIronLegacy data folder).
 */
public final class HubLobbyLayout {

    private final HollowCrownHubPlugin plugin;
    private final HubLobbyWorldsYaml yaml;

    public HubLobbyLayout(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        this.yaml = new HubLobbyWorldsYaml(plugin);
        plugin.getServer().getPluginManager().registerEvents(new HubLobbyWandListener(plugin, yaml), plugin);
    }

    public HubLobbyWorldsYaml worldsYaml() {
        return yaml;
    }

    /**
     * Gives a universal lobby wand that resolves the template key dynamically from the player's current world.
     */
    public boolean giveLobbyWand(Player p) {
        if (p == null) {
            return false;
        }
        p.getInventory().addItem(HubLobbyWandListener.createWand(plugin));
        return true;
    }

    /** Player must stand inside saved lobby zone in the template world. */
    public boolean setLobbySpawnHere(Player p) {
        World w = p.getWorld();
        if (w == null) {
            return false;
        }
        Optional<String> tk = yaml.findTemplateKeyForWorldFolder(w.getName());
        String key = tk.orElse(w.getName().toLowerCase(Locale.ROOT));
        if (tk.isEmpty() && !HubLobbyWorldsYaml.SAFE_TEMPLATE_KEY.matcher(key).matches()) {
            return false;
        }
        Optional<HubLobbyWorldsYaml.LobbyZone> zone = yaml.lobbyZone(key);
        if (zone.isEmpty()) {
            return false;
        }
        if (!zone.get().contains(p.getLocation())) {
            return false;
        }
        return yaml.writeLobbySpawn(key, p.getLocation());
    }
}
