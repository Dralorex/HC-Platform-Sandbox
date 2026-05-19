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

public class WorldsAdminRootMenu extends MenuView {
    private final Inventory inv;

    public WorldsAdminRootMenu() {
        this.inv = Bukkit.createInventory(this, 27, Component.text("Worlds", NamedTextColor.AQUA));
        build();
    }

    private ItemStack button(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            if (lore != null) {
                im.lore(lore.stream().map(LegacyComponentSerializer.legacySection()::deserialize).collect(Collectors.toList()));
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private void build() {
        inv.clear();
        inv.setItem(11, button(Material.IRON_DOOR, "§6Hub (general)", List.of(
                "§7Lobby border radius + mob spawn toggles",
                "§7applied everywhere hub/limbo rules run.",
                "",
                "§eClick to edit"
        )));
        inv.setItem(13, button(Material.NAME_TAG, "§eTeam Name Database", List.of(
                "§7Preset names for auto-assigned teams.",
                "",
                "§eClick to manage"
        )));
        inv.setItem(15, button(Material.FILLED_MAP, "§bGame worlds", List.of(
                "§7Per-template match settings (border, freeze,",
                "§7craft/build, cannon, movement modes, …).",
                "",
                "§eClick to choose template"
        )));
        inv.setItem(22, button(Material.BOOK, "§7Note", List.of(
                "§7OP build/dig bypass stays under §fCore Settings§7."
        )));
        inv.setItem(18, button(Material.BARRIER, "§cBack", List.of("§7Core Settings")));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.isOp()) return;
        switch (e.getRawSlot()) {
            case 11 -> p.openInventory(new WorldsHubSettingsMenu().getInventory());
            case 13 -> p.openInventory(new TeamNameDatabaseAdminMenu(0).getInventory());
            case 15 -> p.openInventory(new GameWorldsListMenu(0).getInventory());
            case 18 -> p.openInventory(new CoreSettingsAdminMenu().getInventory());
            default -> { }
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
