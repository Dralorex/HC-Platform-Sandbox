package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

/** Global OP bypass only; per-match rules live under Worlds → Game worlds. */
public class CoreSettingsAdminMenu extends MenuView {
    private final Inventory inv;

    public CoreSettingsAdminMenu() {
        this.inv = Bukkit.createInventory(this, 54, Component.text("Core Settings", NamedTextColor.AQUA));
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

    private String onOff(boolean v) {
        return v ? "§aON" : "§cOFF";
    }

    private void build() {
        inv.clear();
        var plugin = HungerGamesPlugin.get();
        var cfg = plugin.getConfig();

        inv.setItem(13, button(Material.FILLED_MAP, "§bWorlds", List.of(
                "§7Hub-wide lobby options + per-game-template",
                "§7match settings (border, freeze, rules, …).",
                "",
                "§eClick: open Worlds menu"
        )));

        inv.setItem(22, button(Material.NAME_TAG, "§eTeam Name Database", List.of(
                "§7Manage preset names used when auto-forming teams.",
                "",
                "§eClick: open name list"
        )));

        inv.setItem(31, button(Material.DIAMOND_PICKAXE, "§aOP Build/Dig Bypass", List.of(
                "§7Current: " + onOff(cfg.getBoolean("rules.opsBypassBuildDig", true)),
                "§7ON: OPs always bypass build/dig rules everywhere.",
                "§7OFF: OPs follow normal build/dig checks.",
                "",
                "§eLeft/Right-click: Toggle"
        )));

        inv.setItem(49, button(Material.BARRIER, "§cBack", List.of("§7Return to admin control center")));
    }

    private void saveAndRefresh(Player p, String action) {
        HungerGamesPlugin.get().saveConfig();
        HungerGamesPlugin.get().history().log(p.getName(), "admin_gui_core_setting", action);
        build();
        p.updateInventory();
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getRawSlot() < 0 || e.getRawSlot() >= inv.getSize()) return;
        ClickType click = e.getClick();
        int slot = e.getRawSlot();
        var plugin = HungerGamesPlugin.get();
        var cfg = plugin.getConfig();

        switch (slot) {
            case 13 -> p.openInventory(new WorldsAdminRootMenu().getInventory());
            case 22 -> {
                if (!p.isOp()) return;
                p.openInventory(new TeamNameDatabaseAdminMenu(0).getInventory());
            }
            case 31 -> {
                boolean next = !cfg.getBoolean("rules.opsBypassBuildDig", true);
                cfg.set("rules.opsBypassBuildDig", next);
                saveAndRefresh(p, "ops_bypass_build_dig=" + next);
            }
            case 49 -> p.openInventory(new AdminControlCenterMenu().getInventory());
            default -> { }
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
