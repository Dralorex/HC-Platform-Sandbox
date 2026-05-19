package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.crates.CrateType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnvoyPackagesMenu extends MenuView {
    private final Inventory inv;
    private final CrateType type;
    private final int page;
    private final String templateKey;

    public EnvoyPackagesMenu(String templateKey, CrateType type, int page) {
        this.templateKey = templateKey;
        this.type = type;
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, 54, Component.text(
                type.name() + " · " + templateKey + " (p." + (this.page + 1) + ")", NamedTextColor.GOLD));
        build();
    }

    private FileConfiguration y() {
        return HungerGamesPlugin.get().envoys().yamlForTemplate(templateKey);
    }

    private void save(FileConfiguration y) {
        HungerGamesPlugin.get().envoys().saveYamlTemplate(templateKey, y);
    }

    private ItemStack pkgItem(int idx, List<Map<?, ?>> items, String tier) {
        ItemStack it = new ItemStack(type.displayBlock);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            FileConfiguration cfg = y();
            Component displayName = Component.text("Package #" + (idx + 1), NamedTextColor.YELLOW);
            if (tier != null && !tier.isEmpty()) {
                String tierName = cfg.getString("tiers." + tier + ".name", tier);
                displayName = displayName.append(Component.text(" [" + tierName + "]", NamedTextColor.AQUA));
            }
            im.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            if (tier != null && !tier.isEmpty()) {
                String tierName = cfg.getString("tiers." + tier + ".name", tier);
                lore.add(Component.text("Tier: ", NamedTextColor.GRAY)
                        .append(Component.text(tierName, NamedTextColor.AQUA)));
                lore.add(Component.empty());
            }
            for (int i = 0; i < Math.min(4, items.size()); i++) {
                Map<?, ?> m = items.get(i);
                lore.add(Component.text(m.get("material") + " x" + m.get("min") + "-" + m.get("max"), NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Left-click: edit", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Right-click: remove", NamedTextColor.DARK_GRAY));
            im.lore(lore);
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
        FileConfiguration y = y();
        List<?> pkgs = y.getList("types." + type.name() + ".packages");
        int start = page * 45;
        int slot = 0;
        if (pkgs != null) for (int i = start; i < Math.min(start + 45, pkgs.size()); i++) {
            Object o = pkgs.get(i);
            if (o instanceof Map) {
                Map<?, ?> pkgMap = (Map<?, ?>) o;
                Object itemsObj = pkgMap.get("items");
                List<Map<?, ?>> items = itemsObj instanceof List ? (List<Map<?, ?>>) itemsObj : List.of();
                String tier = pkgMap.containsKey("tier") ? String.valueOf(pkgMap.get("tier")) : null;
                inv.setItem(slot++, pkgItem(i, items, tier));
            }
        }
        inv.setItem(45, button(Material.SLIME_BALL, "§aAdd Package",
                List.of("§7Create new package", "", "§eLeft-click")));
        if (page > 0) inv.setItem(46, button(Material.ARROW, "§aPrevious", List.of("Page " + page)));
        inv.setItem(49, button(Material.BARRIER, "§cBack", null));
        if (pkgs != null && (page + 1) * 45 < pkgs.size()) inv.setItem(52, button(Material.ARROW, "§aNext", List.of("Page " + (page + 2))));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 49) {
            p.openInventory(new EnvoyTypesMenu(templateKey).getInventory());
            return;
        }
        if (slot == 46) {
            p.openInventory(new EnvoyPackagesMenu(templateKey, type, Math.max(0, page - 1)).getInventory());
            return;
        }
        if (slot == 52) {
            p.openInventory(new EnvoyPackagesMenu(templateKey, type, page + 1).getInventory());
            return;
        }
        if (slot == 45) {
            p.closeInventory();
            FileConfiguration y = y();
            List<String> allowedTiers = y.getStringList("tiers.allowed");
            if (allowedTiers.isEmpty()) allowedTiers = List.of("T1", "T2", "T3", "T4");
            String tierList = String.join(", ", allowedTiers);
            HungerGamesPlugin.get().chatInput().prompt(p, "Enter tier for new package (" + tierList + "):", input -> {
                String selectedTier = input.trim().toUpperCase();
                FileConfiguration y2 = y();
                List<String> validTiers = y2.getStringList("tiers.allowed");
                if (validTiers.isEmpty()) validTiers = List.of("T1", "T2", "T3", "T4");
                if (!validTiers.contains(selectedTier)) {
                    p.sendMessage(Component.text("Invalid tier: " + String.join(", ", validTiers), NamedTextColor.RED));
                    p.openInventory(new EnvoyPackagesMenu(templateKey, type, page).getInventory());
                    return;
                }
                List<?> pkgs = y2.getList("types." + type.name() + ".packages");
                ArrayList<Object> list = new ArrayList<>(pkgs == null ? List.of() : pkgs);
                Map<String, Object> newPkg = new java.util.LinkedHashMap<>();
                newPkg.put("items", new ArrayList<>());
                newPkg.put("tier", selectedTier);
                list.add(newPkg);
                y2.set("types." + type.name() + ".packages", list);
                save(y2);
                p.sendMessage(Component.text("Created package with tier " + selectedTier + ".", NamedTextColor.GREEN));
                p.openInventory(new EnvoyPackagesMenu(templateKey, type, page).getInventory());
            });
            return;
        }
        if (slot < 45) {
            ItemStack it = inv.getItem(slot);
            if (it == null || !it.hasItemMeta() || it.getItemMeta().displayName() == null) return;
            String name = PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName());
            if (!name.startsWith("Package #")) return;
            int idx = Integer.parseInt(name.substring("Package #".length()).split(" ")[0]) - 1;
            if (e.isRightClick()) {
                FileConfiguration y = y();
                List<?> pkgs = y.getList("types." + type.name() + ".packages");
                if (pkgs != null && idx >= 0 && idx < pkgs.size()) {
                    ArrayList<?> list = new ArrayList<>(pkgs);
                    list.remove(idx);
                    y.set("types." + type.name() + ".packages", list);
                    save(y);
                    p.openInventory(new EnvoyPackagesMenu(templateKey, type, page).getInventory());
                }
            } else {
                p.openInventory(new EnvoyPackageEditMenu(templateKey, type, idx, 0).getInventory());
            }
        }
    }

    @Override public Inventory getInventory() {
        return inv;
    }
}
