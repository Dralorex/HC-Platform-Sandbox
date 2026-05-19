package com.hollowcrown.hub.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Same holder pattern as HCIronLegacy {@code MenuView}: top inventory clicks route to {@link #onClick}.
 */
public abstract class HubMenuView implements InventoryHolder {

    public abstract void onClick(InventoryClickEvent e);

    @Override
    public abstract @NotNull Inventory getInventory();
}
