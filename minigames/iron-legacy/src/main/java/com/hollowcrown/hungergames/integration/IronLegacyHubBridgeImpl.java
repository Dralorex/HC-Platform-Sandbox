package com.hollowcrown.hungergames.integration;

import com.hollowcrown.api.integration.IronLegacyHubBridge;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.entity.Player;

public record IronLegacyHubBridgeImpl(HungerGamesPlugin plugin) implements IronLegacyHubBridge {

    @Override
    public void recordHubProtectedKill(Player killer, Player victim) {
        plugin.leaderboard().addKill(killer.getUniqueId());
    }

    @Override
    public boolean isPlayerInActiveMatchWorld(Player player) {
        return plugin.game().isPlayerInActiveMatchWorld(player);
    }
}
