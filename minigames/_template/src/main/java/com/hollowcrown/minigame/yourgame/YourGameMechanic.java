package com.hollowcrown.minigame.yourgame;

import com.hollowcrown.api.mechanic.MechanicContext;
import com.hollowcrown.api.mechanic.MinigameMechanic;

/**
 * Hub calls these when lobby instance worlds are created or destroyed for templates
 * whose {@code mechanicId} matches {@link YourGamePlugin#HOLLOW_CROWN_KEY}.
 */
public final class YourGameMechanic implements MinigameMechanic {

  private final YourGamePlugin plugin;

  public YourGameMechanic(YourGamePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void onLobbyInstanceCreated(MechanicContext ctx) {
    plugin.getLogger().info(
        "Hub lobby ready: template=" + ctx.templateKey().value()
            + " worldUuid=" + ctx.lobbyWorldUuid());
    // Track lobby world, warm caches, etc.
  }

  @Override
  public void onLobbyInstanceReleased(MechanicContext ctx) {
    plugin.getLogger().fine(
        "Hub lobby released: template=" + ctx.templateKey().value());
    // Tear down per-lobby state
  }
}
