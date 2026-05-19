package com.hollowcrown.api.minigame;

import com.hollowcrown.api.HollowCrownApiVersion;
import com.hollowcrown.api.mechanic.MinigameMechanic;
import com.hollowcrown.api.platform.GamePlatform;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Shared enable/disable steps for minigames on the Hollow Crown stack.
 */
public final class MinigameBootstrap {

    private MinigameBootstrap() {
    }

    /**
     * Verifies API schema and that Foundation + Hub plugins from the key are loaded and enabled.
     */
    public static void assertRunnableStack(Plugin host, MinigamePluginKey key) {
        if (HollowCrownApiVersion.SCHEMA < key.minimumApiSchema()) {
            throw new IllegalStateException(host.getName() + " requires Hollow Crown API schema >= "
                    + key.minimumApiSchema() + " (runtime schema " + HollowCrownApiVersion.SCHEMA + ")");
        }
        requireEnabledPlugin(host, key.foundationPluginName());
        requireEnabledPlugin(host, key.hubPluginName());
    }

    /**
     * Registers the mechanic on {@link GamePlatform} after {@link #assertRunnableStack}.
     */
    public static void registerMechanic(Plugin host, GamePlatform platform, MinigamePluginKey key, MinigameMechanic mechanic) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mechanic, "mechanic");
        assertRunnableStack(host, key);
        platform.registerMechanic(key.mechanicId(), mechanic);
        host.getLogger().info("Hollow Crown mechanic registered: id=" + key.mechanicId().value()
                + " display=" + key.displayName());
    }

    public static void unregisterMechanic(Plugin host, GamePlatform platform, MinigamePluginKey key) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(key, "key");
        if (platform == null) {
            return;
        }
        platform.unregisterMechanic(key.mechanicId());
        host.getLogger().info("Hollow Crown mechanic unregistered: id=" + key.mechanicId().value());
    }

    private static void requireEnabledPlugin(Plugin host, String pluginName) {
        Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
        if (p == null || !p.isEnabled()) {
            throw new IllegalStateException(host.getName() + " requires plugin \"" + pluginName + "\" loaded and enabled.");
        }
    }
}
