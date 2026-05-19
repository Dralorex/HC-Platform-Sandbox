package com.hollowcrown.hub.integration;

import com.hollowcrown.api.integration.IronLegacyMatchInstanceRuntime;
import com.hollowcrown.api.integration.IronLegacyWorldsCatalog;
import com.hollowcrown.api.platform.GamePlatform;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.util.WorldFolderCopier;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            return false;
        }
        if (w != null) {
            try {
                Bukkit.unloadWorld(w, false);
            } catch (Throwable ignored) {
            }
        }
        Path root = Bukkit.getWorldContainer().toPath();
        Path folder = root.resolve(worldName).normalize();
        if (!folder.startsWith(root)) return false;
        if (!Files.exists(folder)) return true;
        try {
            WorldFolderCopier.deleteDirectory(folder);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to delete match world folder '" + worldName + "': " + ex.getMessage());
            return false;
        }
    }
}

