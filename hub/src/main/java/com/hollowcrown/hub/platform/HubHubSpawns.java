package com.hollowcrown.hub.platform;

import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Resolves numbered hub spawn instances from {@code hub.instances.<n>} in config.
 * Hub {@code 1} (or unset) uses the primary {@code hub.spawn} from {@link DefaultGamePlatform}.
 */
public final class HubHubSpawns {

    private static final String CFG_INSTANCES = "hub.instances";

    private HubHubSpawns() {
    }

    public static Optional<HubSpawn> resolve(HollowCrownHubPlugin plugin, int hubNumber) {
        if (hubNumber <= 1) {
            return plugin.platform().getHubSpawn();
        }
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(CFG_INSTANCES + "." + hubNumber);
        if (sec == null) {
            return Optional.empty();
        }
        return parseSpawn(sec);
    }

    public static List<Integer> configuredNumbers(HollowCrownHubPlugin plugin) {
        TreeSet<Integer> ids = new TreeSet<>();
        if (plugin.platform().getHubSpawn().isPresent()) {
            ids.add(1);
        }
        ConfigurationSection root = plugin.getConfig().getConfigurationSection(CFG_INSTANCES);
        if (root == null) {
            return new ArrayList<>(ids);
        }
        for (String key : root.getKeys(false)) {
            try {
                int n = Integer.parseInt(key);
                if (n > 1 && resolve(plugin, n).isPresent()) {
                    ids.add(n);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }

    /** Writes hub #1 to {@code hub.spawn}; higher numbers to {@code hub.instances.<n>}. */
    public static void persist(HollowCrownHubPlugin plugin, int hubNumber, HubSpawn spawn) {
        if (hubNumber <= 1) {
            plugin.platform().setHubSpawn(spawn);
            return;
        }
        String base = CFG_INSTANCES + "." + hubNumber;
        var c = plugin.getConfig();
        c.set(base + ".world-uuid", spawn.worldUuid().toString());
        c.set(base + ".x", spawn.x());
        c.set(base + ".y", spawn.y());
        c.set(base + ".z", spawn.z());
        c.set(base + ".yaw", spawn.yaw());
        c.set(base + ".pitch", spawn.pitch());
        plugin.saveConfig();
    }

    /** Clears hub #1 ({@code hub.spawn}) or one numbered instance. */
    public static void clear(HollowCrownHubPlugin plugin, int hubNumber) {
        if (hubNumber <= 1) {
            plugin.platform().clearHubSpawn();
            return;
        }
        plugin.getConfig().set(CFG_INSTANCES + "." + hubNumber, null);
        plugin.saveConfig();
    }

    private static Optional<HubSpawn> parseSpawn(ConfigurationSection sec) {
        String uuidStr = sec.getString("world-uuid", "");
        if (uuidStr == null || uuidStr.isBlank() || !sec.isSet("x")) {
            return Optional.empty();
        }
        try {
            UUID w = UUID.fromString(uuidStr.trim());
            double x = sec.getDouble("x");
            double y = sec.getDouble("y");
            double z = sec.getDouble("z");
            float yaw = (float) sec.getDouble("yaw");
            float pitch = (float) sec.getDouble("pitch");
            return Optional.of(new HubSpawn(w, x, y, z, yaw, pitch));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
