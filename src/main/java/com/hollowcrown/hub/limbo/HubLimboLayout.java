package com.hollowcrown.hub.limbo;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.command.HubIlWorldInput;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
     * CLI resolve order: Iron Legacy row (when loaded), else lowercased raw token if it matches a safe YAML key segment.
     */
    public boolean giveLimboWand(Player p, String templateKeyRaw) {
        if (p == null || templateKeyRaw == null || templateKeyRaw.isBlank()) {
            return false;
        }
        String trimmed = templateKeyRaw.trim();
        Optional<String> canon = HubIronLegacySupport.ilAdmin().flatMap(a -> HubIlWorldInput.resolveCanonicalKey(a, trimmed));
        String wandKey = canon.orElse(trimmed.toLowerCase(Locale.ROOT));
        if (canon.isEmpty() && !HubLimboWorldsYaml.SAFE_TEMPLATE_KEY.matcher(wandKey).matches()) {
            p.sendMessage(Component.text(
                    "Invalid limbo key — letters, numbers, underscore or hyphen (max 64). Example: test",
                    NamedTextColor.RED));
            return false;
        }
        p.getInventory().addItem(HubLimboWandListener.createWand(plugin, wandKey));
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
