package com.hollowcrown.hungergames.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MenuListener implements Listener {
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuView holder) {
            e.setCancelled(true);
            holder.onClick(e);
        }
    }
}
