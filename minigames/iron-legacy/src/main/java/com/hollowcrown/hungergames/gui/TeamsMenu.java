package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TeamsMenu extends MenuView {
    private final Inventory inv;

    public TeamsMenu() {
        inv = Bukkit.createInventory(this, 54, Component.text("Teams", NamedTextColor.GREEN));
        int i = 0;
        for (Team t : HungerGamesPlugin.get().teams().getTeams()) {
            if (i >= 54) break;
            ItemStack it = new ItemStack(Material.OAK_SIGN);
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                im.displayName(LegacyComponentSerializer.legacySection().deserialize(t.getColor() + t.getName()));
                im.lore(java.util.List.of(
                        Component.text("A team in the arena.", NamedTextColor.DARK_GRAY),
                        Component.text("Members hidden in this view.", NamedTextColor.GRAY)
                ));
                it.setItemMeta(im);
            }
            inv.setItem(i++, it);
        }
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        // Read-only listing; no click action.
    }

    @Override
    public Inventory getInventory() { return inv; }
}
