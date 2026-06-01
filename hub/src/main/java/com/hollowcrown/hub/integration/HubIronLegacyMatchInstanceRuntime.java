package com.hollowcrown.hub.integration;

import com.hollowcrown.api.integration.IronLegacyMatchInstanceRuntime;
import com.hollowcrown.api.integration.IronLegacyWorldsCatalog;
import com.hollowcrown.api.platform.GamePlatform;
import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.util.WorldFolderCopier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Hub implementation for Iron Legacy match world create/destroy. */
public final class HubIronLegacyMatchInstanceRuntime implements IronLegacyMatchInstanceRuntime {

    private final HollowCrownHubPlugin plugin;
    private final GamePlatform platform;
    private final IronLegacyWorldsCatalog worldsCatalog;

    public HubIronLegacyMatchInstanceRuntime(
            HollowCrownHubPlugin plugin,
            GamePlatform platform,
            IronLegacyWorldsCatalog worldsCatalog
    ) {
        this.plugin = plugin;
        this.platform = platform;
        this.worldsCatalog = worldsCatalog;
    }

    @Override
    public Optional<MatchInstance> createMatchInstance(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) return Optional.empty();
        String key = templateKey.toLowerCase(Locale.ROOT);
        var info = worldsCatalog.templateByKeyOrWorld(key).orElse(null);
        if (info == null) return Optional.empty();
        var reservation = platform.createMatchWorld(key).orElse(null);
        if (reservation == null) return Optional.empty();
        return Optional.of(new MatchInstance(
                UUID.randomUUID().toString().substring(0, 8),
                key,
                info.worldName(),
                reservation.worldName(),
                System.currentTimeMillis()
        ));
    }

    @Override
    public boolean destroyMatchInstanceByWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return false;
        World w = Bukkit.getWorld(worldName);

        if (w != null && !w.getPlayers().isEmpty()) {
            evacuateStragglersToHubSpawn(w);
            if (!w.getPlayers().isEmpty()) {
                return false;
            }
        }

        if (w != null) {
            try {
                Bukkit.unloadWorld(w, true);
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to unload match world '" + worldName + "': " + t.getMessage());
                return false;
            }
        }

        Path root = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path folder = root.resolve(worldName).normalize();
        if (!folder.startsWith(root)) return false;
        if (!Files.exists(folder)) {
            unmarkPolicyMatchWorld(worldName);
            return true;
        }

        boolean deleted = WorldFolderCopier.deleteDirectoryBestEffort(folder, 20, 250L);
        if (deleted) {
            unmarkPolicyMatchWorld(worldName);
            return true;
        }
        plugin.getLogger().warning("Failed to delete match world folder after retries: " + worldName);
        return false;
    }

    private void evacuateStragglersToHubSpawn(World world) {
        Optional<HubSpawn> spawnOpt = platform.getHubSpawn();
        Location fallback = null;
        if (spawnOpt.isPresent()) {
            HubSpawn s = spawnOpt.get();
            World hub = Bukkit.getWorld(s.worldUuid());
            if (hub != null) {
                fallback = new Location(hub, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
            }
        }
        for (Player p : new ArrayList<>(world.getPlayers())) {
            boolean routed = false;
            try {
                routed = plugin.sendPlayerToHubSpawnSync(p);
            } catch (Throwable ignored) {
            }
            if (!routed && fallback != null) {
                try {
                    p.teleport(fallback);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void unmarkPolicyMatchWorld(String worldName) {
        try {
            if (plugin.pluginPolicies() != null) {
                plugin.pluginPolicies().unmarkWorldAsMatch(worldName);
            }
        } catch (Throwable ignored) {
        }
    }
}
