package com.hollowcrown.hub.integration;

import com.hollowcrown.api.integration.HubPlayerMenus;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.gui.HubPartyMenu;
import com.hollowcrown.hub.gui.LobbyJoinTemplateMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class DefaultHubPlayerMenus implements HubPlayerMenus {

    private final HollowCrownHubPlugin plugin;

    public DefaultHubPlayerMenus(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openJoinGameMenu(Player player) {
        if (!player.hasPermission("hollowcrownhub.join")) {
            player.sendMessage(Component.text("You cannot use join here.", NamedTextColor.RED));
            return;
        }
        LobbyJoinTemplateMenu.open(player, plugin, 0);
    }

    @Override
    public void openPartyMenu(Player player) {
        if (!player.hasPermission("hollowcrownhub.party")) {
            player.sendMessage(Component.text("You cannot use parties here.", NamedTextColor.RED));
            return;
        }
        player.openInventory(new HubPartyMenu(plugin, player).getInventory());
    }
}
