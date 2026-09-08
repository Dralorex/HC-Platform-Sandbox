package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hub.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TeamsMenu extends MenuView {
    private final Inventory inv;

    public TeamsMenu(World world) {
        inv = Bukkit.createInventory(this, 54, Component.text("Teams", NamedTextColor.GREEN));
        int i = 0;
        //for (Team t : HungerGamesPlugin.get().teams().getTeams()) {
        for(TeamManager.Team t : TeamManager.getOrCreateWorldInstance(world).teams.values()) {
            if (i >= 54) break;
            ItemStack it = new ItemStack(Material.OAK_SIGN);
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                im.displayName(Component.text(t.getName(), TextColor.color(t.color.asRGB())));
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
