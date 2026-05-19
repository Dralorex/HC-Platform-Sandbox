package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

public class ReplenishChestsMenu extends MenuView {
    private final Inventory inv;
    private final int page;
    private final String templateKey;

    public ReplenishChestsMenu(String templateKey, int page) {
        this.templateKey = templateKey;
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, 54, Component.text(
                "Replenish Chests · " + templateKey + " (p." + (this.page + 1) + ")", NamedTextColor.DARK_GREEN));
        build();
    }

    private ItemStack entry(Location l) {
        ItemStack it = new ItemStack(Material.CHEST);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Component.text(l.getWorld().getName(), NamedTextColor.GOLD));
            im.lore(List.of(
                    Component.text(l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ(), NamedTextColor.GRAY),
                    Component.text("Right-click: remove", NamedTextColor.DARK_GRAY)
            ));
            it.setItemMeta(im);
        }
        return it;
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
        var list = HungerGamesPlugin.get().replenish().list(templateKey);
        int start = page * 45;
        for (int i = 0; i < 45; i++) {
            int idx = start + i;
            if (idx >= list.size()) break;
            inv.setItem(i, entry(list.get(idx)));
        }
        if (page > 0) inv.setItem(45, button(Material.ARROW, "§aPrevious", List.of("Page " + page)));
        if ((page + 1) * 45 < list.size()) inv.setItem(53, button(Material.ARROW, "§aNext", List.of("Page " + (page + 2))));
        inv.setItem(47, button(Material.SPYGLASS, "§bToggle Particles", null));
        inv.setItem(48, button(Material.CHEST, "§6Give Wand", null));
        inv.setItem(50, button(Material.LIME_DYE, "§aMode Start", null));
        inv.setItem(51, button(Material.GRAY_DYE, "§eMode Stop", null));
        inv.setItem(49, button(Material.BARRIER, "§cBack", null));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 49) {
            p.openInventory(new ReplenishAdminMenu(templateKey).getInventory());
            return;
        }
        if (slot == 45 && inv.getItem(45) != null && inv.getItem(45).getType() == Material.ARROW && page > 0) {
            p.openInventory(new ReplenishChestsMenu(templateKey, page - 1).getInventory());
            return;
        }
        if (slot == 53 && inv.getItem(53) != null && inv.getItem(53).getType() == Material.ARROW) {
            p.openInventory(new ReplenishChestsMenu(templateKey, page + 1).getInventory());
            return;
        }
        if (slot == 47) {
            HungerGamesPlugin.get().replenishMode().toggleParticles(p);
            return;
        }
        if (slot == 48) {
            HungerGamesPlugin.get().replenishMode().giveWand(p);
            return;
        }
        if (slot == 50) {
            HungerGamesPlugin.get().replenishMode().setEnabled(p, true, templateKey);
            return;
        }
        if (slot == 51) {
            HungerGamesPlugin.get().replenishMode().setEnabled(p, false);
            return;
        }
        if (slot < 45) {
            ItemStack it = inv.getItem(slot);
            if (it == null || !it.hasItemMeta() || it.getItemMeta().lore() == null) return;
            if (!e.isRightClick()) return;
            List<Component> lore = it.getItemMeta().lore();
            String worldName = PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName());
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            try {
                String loreText = PlainTextComponentSerializer.plainText().serialize(lore.get(0));
                String[] parts = loreText.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                boolean removed = HungerGamesPlugin.get().replenish().removeNearest(
                        new Location(world, x + 0.5, y + 0.5, z + 0.5), templateKey);
                if (removed) {
                    p.sendMessage(Component.text("Removed chest.", NamedTextColor.YELLOW));
                    p.openInventory(new ReplenishChestsMenu(templateKey, page).getInventory());
                }
            } catch (Exception ignored) {}
        }
    }

    @Override public Inventory getInventory() {
        return inv;
    }
}
