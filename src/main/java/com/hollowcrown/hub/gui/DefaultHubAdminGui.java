package com.hollowcrown.hub.gui;

import com.hollowcrown.api.integration.HubAdminGui;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Registers as {@link HubAdminGui}; opens Hub-owned admin inventories. */
public final class DefaultHubAdminGui implements HubAdminGui {

    private final HollowCrownHubPlugin plugin;

    public DefaultHubAdminGui(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openShell(Player player) {
        player.openInventory(new HubAdminShellMenu(plugin).getInventory());
    }

    @Override
    public void openIronLegacyTemplates(Player player, int pageZero) {
        HubIronLegacySupport.ilAdmin().ifPresentOrElse(
                admin -> player.openInventory(new HubIlTemplateListMenu(plugin, admin, pageZero).getInventory()),
                () -> player.sendMessage(Component.text(
                        "Iron Legacy is not loaded — IL template GUI unavailable.",
                        NamedTextColor.RED)));
    }

    @Override
    public void openHubTemplatesBrowser(Player player) {
        player.openInventory(new HubHubTemplateBrowserMenu(plugin, 0).getInventory());
    }

    @Override
    public void openQueueOverview(Player player) {
        player.openInventory(new HubQueueOverviewMenu(plugin, 0).getInventory());
    }

    @Override
    public void openLobbyAdminShortcut(Player player) {
        LobbyAdminMenu.open(player, plugin, 0);
    }
}
