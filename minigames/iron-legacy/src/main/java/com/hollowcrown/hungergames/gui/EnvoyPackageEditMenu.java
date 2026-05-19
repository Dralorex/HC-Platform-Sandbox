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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnvoyPackageEditMenu extends MenuView {
    private final Inventory inv;
    private final CrateType type;
    private final int pkgIndex;
    private final int page;
    private final String templateKey;

    public EnvoyPackageEditMenu(String templateKey, CrateType type, int pkgIndex, int page) {
        this.templateKey = templateKey;
        this.type = type;
        this.pkgIndex = pkgIndex;
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, 54, Component.text(
                type.name() + " pkg#" + (pkgIndex + 1) + " · " + templateKey, NamedTextColor.GOLD));
        build();
    }

    private FileConfiguration y() {
        return HungerGamesPlugin.get().envoys().yamlForTemplate(templateKey);
    }

    private void save(FileConfiguration y) {
        HungerGamesPlugin.get().envoys().saveYamlTemplate(templateKey, y);
    }

    private ItemStack itemEntry(String mat, int min, int max) {
        Material m;
        try {
            m = Material.valueOf(mat.toUpperCase());
        } catch (Exception ex) {
            m = Material.PAPER;
        }
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Component.text(mat, NamedTextColor.YELLOW));
            im.lore(List.of(
                    Component.text("amount: " + min + "-" + max, NamedTextColor.GRAY),
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
        FileConfiguration y = y();
        List<?> pkgs = y.getList("types." + type.name() + ".packages");
        if (pkgs == null || pkgIndex < 0 || pkgIndex >= pkgs.size()) return;
        Object pick = pkgs.get(pkgIndex);
        if (!(pick instanceof Map)) return;
        Object itemsObj = ((Map<?, ?>) pick).get("items");
        List<?> items = itemsObj instanceof List ? (List<?>) itemsObj : List.of();
        int start = page * 45;
        int slot = 0;
        for (int i = start; i < Math.min(start + 45, items.size()); i++) {
            Object o = items.get(i);
            if (o instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) o;
                String mat = String.valueOf(m.get("material"));
                Object minO = m.get("min");
                Object maxO = m.get("max");
                int min = minO == null ? 1 : Integer.parseInt(String.valueOf(minO));
                int max = maxO == null ? 1 : Integer.parseInt(String.valueOf(maxO));
                inv.setItem(slot++, itemEntry(mat, min, max));
            }
        }
        Map<?, ?> pkgMap = (Map<?, ?>) pick;
        String currentTier = pkgMap.containsKey("tier") ? String.valueOf(pkgMap.get("tier")) : "None";
        String tierName = y.getString("tiers." + currentTier + ".name", currentTier);
        inv.setItem(4, button(Material.EXPERIENCE_BOTTLE, "§bTier: " + tierName,
                List.of("§7Click to change tier", "", "§eLeft-click")));
        inv.setItem(45, button(Material.SLIME_BALL, "§aAdd Item",
                List.of("§7Format: MATERIAL min max", "", "§eLeft-click")));
        if (page > 0) inv.setItem(46, button(Material.ARROW, "§aPrevious", List.of("Page " + page)));
        inv.setItem(49, button(Material.BARRIER, "§cBack", null));
        if ((page + 1) * 45 < items.size()) inv.setItem(52, button(Material.ARROW, "§aNext", List.of("Page " + (page + 2))));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 49) {
            p.openInventory(new EnvoyPackagesMenu(templateKey, type, 0).getInventory());
            return;
        }
        if (slot == 46) {
            p.openInventory(new EnvoyPackageEditMenu(templateKey, type, pkgIndex, Math.max(0, page - 1)).getInventory());
            return;
        }
        if (slot == 52) {
            p.openInventory(new EnvoyPackageEditMenu(templateKey, type, pkgIndex, page + 1).getInventory());
            return;
        }
        if (slot == 4) {
            p.closeInventory();
            FileConfiguration y = y();
            List<String> allowedTiers = y.getStringList("tiers.allowed");
            if (allowedTiers.isEmpty()) allowedTiers = List.of("T1", "T2", "T3", "T4");
            String tierList = String.join(", ", allowedTiers);
            HungerGamesPlugin.get().chatInput().prompt(p, "Enter new tier (" + tierList + "):", input -> {
                String newTier = input.trim().toUpperCase();
                FileConfiguration y2 = y();
                List<String> validTiers = y2.getStringList("tiers.allowed");
                if (validTiers.isEmpty()) validTiers = List.of("T1", "T2", "T3", "T4");
                if (!validTiers.contains(newTier)) {
                    p.sendMessage(Component.text("Invalid tier.", NamedTextColor.RED));
                    p.openInventory(new EnvoyPackageEditMenu(templateKey, type, pkgIndex, page).getInventory());
                    return;
                }
                List<?> pkgs = y2.getList("types." + type.name() + ".packages");
                if (pkgs != null && pkgIndex >= 0 && pkgIndex < pkgs.size()) {
                    java.util.ArrayList<?> pkgsMut = new java.util.ArrayList<>(pkgs);
                    Object pickObj = pkgsMut.get(pkgIndex);
                    if (pickObj instanceof Map) {
                        ((Map<String, Object>) pickObj).put("tier", newTier);
                        y2.set("types." + type.name() + ".packages", pkgsMut);
                        save(y2);
                        String tn = y2.getString("tiers." + newTier + ".name", newTier);
                        p.sendMessage(Component.text("Changed tier to " + tn + ".", NamedTextColor.GREEN));
                    }
                }
                p.openInventory(new EnvoyPackageEditMenu(templateKey, type, pkgIndex, page).getInventory());
            });
            return;
        }
        if (slot == 45) {
            HungerGamesPlugin.get().chatInput().prompt(p, "Enter MATERIAL min max", ans -> {
                try {
                    String[] parts = ans.trim().split("\\s+");
                    String mat = parts[0];
                    int min = Integer.parseInt(parts[1]);
                    int max = Integer.parseInt(parts[2]);
                    FileConfiguration y = y();
                    List<?> pkgs = y.getList("types." + type.name() + ".packages");
                    java.util.ArrayList<?> pkgsMut = new java.util.ArrayList<>(pkgs);
                    Object pickObj = pkgsMut.get(pkgIndex);
                    if (!(pickObj instanceof Map)) return;
                    Map<?, ?> pm = (Map<?, ?>) pickObj;
                    Object itemsObj = pm.get("items");
                    java.util.ArrayList<Object> itemList = new java.util.ArrayList<>(itemsObj instanceof List ? (List<?>) itemsObj : List.of());
                    java.util.Map<String, Object> add = new java.util.LinkedHashMap<>();
                    add.put("material", mat);
                    add.put("min", min);
                    add.put("max", max);
                    itemList.add(add);
                    ((Map) pm).put("items", itemList);
                    y.set("types." + type.name() + ".packages", pkgsMut);
                    save(y);
                    p.openInventory(new EnvoyPackageEditMenu(templateKey, type, pkgIndex, page).getInventory());
                } catch (Exception ignored) {}
            });
            p.closeInventory();
            return;
        }
        if (slot < 45) {
            ItemStack it = inv.getItem(slot);
            if (it == null || !it.hasItemMeta() || it.getItemMeta().displayName() == null) return;
            String mat = PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName());
            if (e.isRightClick()) {
                FileConfiguration y = y();
                List<?> pkgs = y.getList("types." + type.name() + ".packages");
                java.util.ArrayList<?> pkgsMut = new java.util.ArrayList<>(pkgs);
                Object pickObj = pkgsMut.get(pkgIndex);
                if (!(pickObj instanceof Map)) return;
                Map<?, ?> pm = (Map<?, ?>) pickObj;
                Object itemsObj = pm.get("items");
                java.util.ArrayList<?> itemList = new java.util.ArrayList<>(itemsObj instanceof List ? (List<?>) itemsObj : List.of());
                int removeIdx = -1;
                for (int i = 0; i < itemList.size(); i++) {
                    Object o = itemList.get(i);
                    if (o instanceof Map) {
                        if (mat.equalsIgnoreCase(String.valueOf(((Map<?, ?>) o).get("material")))) {
                            removeIdx = i;
                            break;
                        }
                    }
                }
                if (removeIdx >= 0) {
                    itemList.remove(removeIdx);
                    ((Map) pm).put("items", itemList);
                    y.set("types." + type.name() + ".packages", pkgsMut);
                    save(y);
                    p.openInventory(new EnvoyPackageEditMenu(templateKey, type, pkgIndex, page).getInventory());
                }
            }
        }
    }

    @Override public Inventory getInventory() {
        return inv;
    }
}
