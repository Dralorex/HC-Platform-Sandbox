package com.hollowcrown.minigame.yourgame;

import com.hollowcrown.api.minigame.MinigameBootstrap;
import com.hollowcrown.api.minigame.MinigamePluginKey;
import com.hollowcrown.api.platform.GamePlatform;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Starter minigame plugin for Hollow Crown Hub.
 * Copy this package and rename {@code yourgame} / {@code YourGame} to your game id.
 */
public final class YourGamePlugin extends JavaPlugin {

  /** Must match Hub {@code templates.yml} {@code mechanicId} for your maps. */
  public static final MinigamePluginKey HOLLOW_CROWN_KEY =
      MinigamePluginKey.hollowCrown("YOUR_GAME_ID", "Your Game");

  private static YourGamePlugin instance;
  private GamePlatform gamePlatform;

  public static YourGamePlugin get() {
    return instance;
  }

  @Override
  public void onEnable() {
    if (!requireHollowCrownStack()) {
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    instance = this;
    saveDefaultConfig();

    if (!registerWithHub()) {
      instance = null;
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    getLogger().info("HCYourGame enabled (mechanic id=" + HOLLOW_CROWN_KEY.mechanicId().value() + ").");
  }

  @Override
  public void onDisable() {
    unregisterFromHub();
    instance = null;
    getLogger().info("HCYourGame disabled.");
  }

  private boolean requireHollowCrownStack() {
    if (!isPluginReady("HC-Foundation") || !isPluginReady("HollowCrownHub")) {
      getLogger().severe("Requires HC-Foundation and HollowCrownHub.");
      return false;
    }
    return true;
  }

  private static boolean isPluginReady(String name) {
    var p = Bukkit.getPluginManager().getPlugin(name);
    return p != null && p.isEnabled();
  }

  private boolean registerWithHub() {
    RegisteredServiceProvider<GamePlatform> reg =
        Bukkit.getServicesManager().getRegistration(GamePlatform.class);
    if (reg == null) {
      getLogger().severe("HollowCrownHub did not register GamePlatform.");
      return false;
    }
    this.gamePlatform = reg.getProvider();
    MinigameBootstrap.registerMechanic(
        this, gamePlatform, HOLLOW_CROWN_KEY, new YourGameMechanic(this));
    return true;
  }

  private void unregisterFromHub() {
    if (gamePlatform != null) {
      try {
        MinigameBootstrap.unregisterMechanic(this, gamePlatform, HOLLOW_CROWN_KEY);
      } catch (Throwable ignored) {
      }
      gamePlatform = null;
    }
  }
}
