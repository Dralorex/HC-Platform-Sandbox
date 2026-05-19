package com.hollowcrown.hungergames.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class MenuView implements InventoryHolder {
    public abstract void onClick(InventoryClickEvent e);
    public abstract @NotNull Inventory getInventory();
}
