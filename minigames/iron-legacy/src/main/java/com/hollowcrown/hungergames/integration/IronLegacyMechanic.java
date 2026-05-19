package com.hollowcrown.hungergames.integration;

import com.hollowcrown.api.mechanic.MechanicContext;
import com.hollowcrown.api.mechanic.MinigameMechanic;
import com.hollowcrown.hungergames.HungerGamesPlugin;

/**
 * Hollow Crown Hub SPI: records Hub-created lobby worlds via {@link MechanicContext} so IL resolves
 * template keys and UI without maintaining parallel lobby world creation.
 */
public final class IronLegacyMechanic implements MinigameMechanic {

    private final HungerGamesPlugin plugin;

    public IronLegacyMechanic(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onLobbyInstanceCreated(MechanicContext ctx) {
        plugin.hubManagedLobbies().register(ctx);
        plugin.getLogger().info("Hub lobby instance ready: template=" + ctx.templateKey().value()
                + " worldUuid=" + ctx.lobbyWorldUuid());
    }

    @Override
    public void onLobbyInstanceReleased(MechanicContext ctx) {
        plugin.hubManagedLobbies().unregister(ctx);
        EnvironmentalHazardsBridge.onHubLobbyReleased(ctx.lobbyWorldUuid());
        plugin.getLogger().fine("Hub lobby instance released: template=" + ctx.templateKey().value());
    }
}
