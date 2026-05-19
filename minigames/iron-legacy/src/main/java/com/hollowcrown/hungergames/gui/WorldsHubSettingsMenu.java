package com.hollowcrown.hungergames.gui;

import com.hollowcrown.api.integration.HubWorldApi;
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

/** Hub protection disk + mob rules (HollowCrownHub {@link HubWorldApi}). */
public class WorldsHubSettingsMenu extends MenuView {
    private final Inventory inv;

    public WorldsHubSettingsMenu() {
        this.inv = Bukkit.createInventory(this, 27, Component.text("Hub — General", NamedTextColor.GOLD));
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

        inv.setItem(11, button(Material.BEACON, "§9Lobby Border Size", List.of(
                "§7Current: §f" + plugin.lobby().getLobbySize(),
                "§7X/Z radius from lobby center.",
                "",
                "§eLeft-click: +8",
                "§eRight-click: -8"
        )));
        inv.setItem(13, button(Material.ZOMBIE_HEAD, "§cLobby Hostile Mob Spawns", List.of(
                "§7Hub lobby + limbo worlds.",
                "",
                "§7Current: " + onOff(plugin.lobby().allowHostileMobSpawns()),
                "",
                "§eLeft/Right-click: Toggle"
        )));
        inv.setItem(15, button(Material.LEATHER, "§aLobby Passive Mob Spawns", List.of(
                "§7Hub lobby + limbo worlds.",
                "",
                "§7Current: " + onOff(plugin.lobby().allowPassiveMobSpawns()),
                "",
                "§eLeft/Right-click: Toggle"
        )));
        inv.setItem(18, button(Material.BARRIER, "§cBack", List.of()));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.isOp()) return;
        ClickType click = e.getClick();
        int slot = e.getRawSlot();
        var plugin = HungerGamesPlugin.get();

        switch (slot) {
            case 11 -> plugin.hubWorldApi().ifPresent(h -> {
                double current = h.protectionRadiusBlocks();
                double next = click.isRightClick() ? Math.max(8.0, current - 8.0) : Math.min(2048.0, current + 8.0);
                h.setProtectionRadiusBlocks(next);
                build();
                p.updateInventory();
                plugin.history().log(p.getName(), "admin_gui_hub_world", "hub_radius=" + next);
            });
            case 13 -> plugin.hubWorldApi().ifPresent(h -> {
                boolean next = !h.allowHostileMobSpawnsInHub();
                h.setAllowHostileMobSpawnsInHub(next);
                build();
                p.updateInventory();
                plugin.history().log(p.getName(), "admin_gui_hub_world", "hub_hostile_mobs=" + next);
            });
            case 15 -> plugin.hubWorldApi().ifPresent(h -> {
                boolean next = !h.allowPassiveMobSpawnsInHub();
                h.setAllowPassiveMobSpawnsInHub(next);
                build();
                p.updateInventory();
                plugin.history().log(p.getName(), "admin_gui_hub_world", "hub_passive_mobs=" + next);
            });
            case 18 -> p.openInventory(new WorldsAdminRootMenu().getInventory());
            default -> { }
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
