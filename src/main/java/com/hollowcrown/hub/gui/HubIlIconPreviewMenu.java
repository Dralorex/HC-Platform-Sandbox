package com.hollowcrown.hub.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/** Locked chest showing one icon stack (HubMenuListener cancels moves). */
public final class HubIlIconPreviewMenu extends HubMenuView {

    private final Inventory inv;

    public HubIlIconPreviewMenu(ItemStack center) {
        ItemStack c = center == null ? new ItemStack(Material.BARRIER) : center.clone();
        this.inv = Bukkit.createInventory(this, 27, Component.text("Icon preview", NamedTextColor.AQUA));
        inv.setItem(13, c);
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta im = close.getItemMeta();
        if (im != null) {
            im.displayName(Component.text("Close", NamedTextColor.RED));
            close.setItemMeta(im);
        }
        inv.setItem(22, close);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getRawSlot() == 22) {
            p.closeInventory();
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}
