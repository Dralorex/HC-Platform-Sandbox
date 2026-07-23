package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link HubTeamMenu} — kept as a thin delegate for existing call sites.
 */
public final class HubLimboTeamMenu extends HubMenuView {

    private final HubTeamMenu delegate;

    private HubLimboTeamMenu(HollowCrownHubPlugin plugin, Player viewer) {
        this.delegate = new HubTeamMenu(plugin, viewer);
    }

    /** Opens the match team menu in limbo. */
    public static void open(HollowCrownHubPlugin plugin, Player p) {
        HubTeamMenu.open(plugin, p);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        delegate.onClick(e);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return delegate.getInventory();
    }
}
