package com.hollowcrown.api.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads before Hub/minigames; exposes shaded {@code com.hollowcrown.api} to dependent plugins (HC-Foundation).
 */
public final class HollowCrownApiBootstrap extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("HC-Foundation loaded (hollowcrown-api schema=" + com.hollowcrown.api.HollowCrownApiVersion.SCHEMA + ").");
    }
}
