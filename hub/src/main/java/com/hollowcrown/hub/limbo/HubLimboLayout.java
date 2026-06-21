package com.hollowcrown.hub.limbo;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;

/**
 * Hub-owned limbo zone/spawn editing ({@code worlds.yml} under HCIronLegacy data folder).
 */
public final class HubLimboLayout {

    private final HollowCrownHubPlugin plugin;
    private final HubLimboWorldsYaml yaml;

    public HubLimboLayout(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        this.yaml = new HubLimboWorldsYaml(plugin);
        plugin.getServer().getPluginManager().registerEvents(new HubLimboWandListener(plugin, yaml), plugin);
    }

    public HubLimboWorldsYaml worldsYaml() {
        return yaml;
    }

    /**
     * Gives a universal limbo wand that resolves the template key dynamically from the player's current world.
     */
    public boolean giveLimboWand(Player p) {
        if (p == null) {
            return false;
        }
        p.getInventory().addItem(HubLimboWandListener.createWand(plugin));
        return true;
    }

    /** Player must stand inside saved limbo zone in the template world. */
    public boolean setLimboSpawnHere(Player p) {
        World w = p.getWorld();
        if (w == null) {
            return false;
        }
        Optional<String> tk = yaml.findTemplateKeyForWorldFolder(w.getName());
        String key = tk.orElse(w.getName().toLowerCase(Locale.ROOT));
        if (tk.isEmpty() && !HubLimboWorldsYaml.SAFE_TEMPLATE_KEY.matcher(key).matches()) {
            return false;
        }
        Optional<HubLimboWorldsYaml.LimboZone> zone = yaml.limboZone(key);
        if (zone.isEmpty()) {
            return false;
        }
        if (!zone.get().contains(p.getLocation())) {
            return false;
        }
        return yaml.writeLimboSpawn(key, p.getLocation());
    }
}
