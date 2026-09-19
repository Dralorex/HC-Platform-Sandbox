package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hub.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.List;
import java.util.UUID;

public class TeamRecolorMenu extends MenuView {
    private final Inventory inv;
    private final TeamManager.Team team;

    public TeamRecolorMenu(TeamManager.Team team) {
        this.team = team;
        this.inv = Bukkit.createInventory(this, 54, Component.text("Choose Team Color", NamedTextColor.YELLOW));
        build();
    }

    private ItemStack swatch(Material m, Color color, String colorName) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            if(m == Material.GLASS_BOTTLE) {
                im.customName(Component.text("Taken!", TextColor.color(color.asRGB())));
                im.lore(List.of(Component.text("Choose another color.", TextColor.color(color.asRGB()))));
            }
            else if (m == Material.POTION) {
                im.customName(Component.text("Pick " + colorName, TextColor.color(color.asRGB())));
                if(im instanceof PotionMeta pm){
                    pm.setColor(color);
                    pm.clearCustomEffects();
                    pm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                    pm.lore(List.of(Component.text("[Example]", TextColor.color(color.asRGB()))));
                }
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private void build() {
        for (int i = 0; i < 54; i++) {
            if (team.getTeamManagerInstance().takenColors.contains(TeamManager.colorSwatch[i]))
                inv.setItem(i, swatch(Material.GLASS_BOTTLE, TeamManager.colorSwatch[i], TeamManager.colorNames[i]));
            else
                inv.setItem(i, swatch(Material.POTION, TeamManager.colorSwatch[i], TeamManager.colorNames[i]));
        }
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(p.getWorld());
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta() || it.getItemMeta().displayName() == null) return;
        Color picked = TeamManager.colorSwatch[e.getSlot()];
        if (picked == null) return;
        if(teamManagerInstance.takenColors.contains(picked)) {
            build();
            return;
        }
        if (!team.getLeader().equals(p.getUniqueId())) {
            p.sendMessage(Component.text("Only the team owner can confirm this menu.", NamedTextColor.RED));
            return;
        }
        if(!team.setColor(picked)){
            p.sendMessage(Component.text("The team could not be recolored.", NamedTextColor.RED));
            return;
        }

        team.sendFakeTeamPackets();
        TeamManager.sendFakeTeamPacketsTo(team);
        openAfterCreate(p);
    }

    private void openAfterCreate(Player p) {
        p.openInventory(new MainMenu(p).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
