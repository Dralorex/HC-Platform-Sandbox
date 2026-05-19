package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
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

public class ReplenishAdminMenu extends MenuView {
    private final Inventory inv;
    private final String templateKey;

    public ReplenishAdminMenu(String templateKey) {
        this.templateKey = templateKey;
        this.inv = Bukkit.createInventory(this, 27, Component.text(
                "Replenish · " + templateKey, NamedTextColor.DARK_GREEN));
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
        inv.setItem(10, button(Material.PAPER, "§aTrigger T1", null));
        inv.setItem(11, button(Material.PAPER, "§aTrigger T2", null));
        inv.setItem(12, button(Material.PAPER, "§aTrigger T3", null));
        inv.setItem(13, button(Material.PAPER, "§aTrigger T4", null));
        inv.setItem(14, button(Material.CLOCK, "§bStart Schedule", List.of(
                "§7Uses your current world + this map's replenish.yml",
                "§7(first refill delay + interval)")));
        inv.setItem(15, button(Material.BARRIER, "§cStop Schedule", null));
        inv.setItem(16, button(Material.CHEST, "§6Chests", List.of("§7View/remove tracked chests")));
        inv.setItem(22, button(Material.BARRIER, "§cBack", null));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 22) {
            p.openInventory(new EnvoyAdminMenu(templateKey).getInventory());
            return;
        }
        var repl = HungerGamesPlugin.get().replenish();
        switch (slot) {
            case 10 -> repl.replenishTier("T1", templateKey, p.getWorld());
            case 11 -> repl.replenishTier("T2", templateKey, p.getWorld());
            case 12 -> repl.replenishTier("T3", templateKey, p.getWorld());
            case 13 -> repl.replenishTier("T4", templateKey, p.getWorld());
            case 14 -> repl.startScheduleFromAdmin(p);
            case 15 -> repl.stopSchedule();
            case 16 -> p.openInventory(new ReplenishChestsMenu(templateKey, 0).getInventory());
            default -> {}
        }
    }

    @Override public Inventory getInventory() {
        return inv;
    }
}
