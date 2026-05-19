package com.hollowcrown.hungergames.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

public class EnvoyAdminMenu extends MenuView {
    private final Inventory inv;
    private final String templateKey;

    public EnvoyAdminMenu(String templateKey) {
        this.templateKey = templateKey;
        this.inv = Bukkit.createInventory(this, 27, Component.text(
                "Envoys · " + templateKey, NamedTextColor.DARK_AQUA));
        build();
    }

    private ItemStack button(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            if (lore != null) {
                im.lore(lore.stream()
                        .map(LegacyComponentSerializer.legacySection()::deserialize)
                        .collect(Collectors.toList()));
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private void build() {
        inv.clear();
        // Row 2: evenly spaced tabs (slots 10, 12, 14, 16)
        inv.setItem(10, button(Material.EMERALD_BLOCK, "§aSpawns", List.of(
                "§7Drop points for this map only",
                "§7(world-envoys/" + templateKey + "/envoys.yml)",
                "",
                "§eClick: manage spawns")));
        inv.setItem(12, button(Material.CHEST, "§6Types & Packages", List.of(
                "§7Loot tables per crate type",
                "",
                "§eClick: open types")));
        inv.setItem(14, button(Material.BOOK, "§2Replenish", List.of(
                "§7Tracked chests & schedule",
                "§7(world-envoys/" + templateKey + "/replenish.yml)",
                "",
                "§eClick: replenish admin")));
        inv.setItem(16, button(Material.REDSTONE_TORCH, "§eGeneral settings", List.of(
                "§7Wave timing, visuals, tiers",
                "§7Replenish timing knobs",
                "",
                "§eClick: edit")));
        inv.setItem(22, button(Material.BARRIER, "§cBack", List.of("§7Return to admin control center")));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 10) {
            p.openInventory(new EnvoySpawnsMenu(templateKey, 0).getInventory());
            return;
        }
        if (slot == 22) {
            p.openInventory(new AdminControlCenterMenu().getInventory());
            return;
        }
        if (slot == 12) {
            p.openInventory(new EnvoyTypesMenu(templateKey).getInventory());
            return;
        }
        if (slot == 14) {
            p.openInventory(new ReplenishAdminMenu(templateKey).getInventory());
            return;
        }
        if (slot == 16) {
            p.openInventory(new EnvoyGeneralSettingsMenu(templateKey).getInventory());
            return;
        }
    }

    @Override public Inventory getInventory() {
        return inv;
    }
}
