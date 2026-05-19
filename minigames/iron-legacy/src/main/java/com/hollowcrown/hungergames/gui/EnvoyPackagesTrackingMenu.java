package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.managers.EnvoyManager;
import com.hollowcrown.hungergames.crates.CrateType;
import com.hollowcrown.hungergames.managers.GiftDropManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class EnvoyPackagesTrackingMenu extends MenuView {
    private final Inventory inv;
    private final int page;
    private final String templateKey;
    private final Map<Integer, Location> slotToLocation = new HashMap<>(); // Track which slot has which package
    private static final int ITEMS_PER_PAGE = 28; // 7 rows x 4 columns, excluding border

    public EnvoyPackagesTrackingMenu(String templateKey, int page) {
        this.templateKey = templateKey;
        this.page = page;
        this.inv = Bukkit.createInventory(this, 54, Component.text(
                "Packages · " + templateKey + " p." + (page + 1), NamedTextColor.GOLD));
        build();
    }

    private boolean matchesTemplate(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return HungerGamesPlugin.get().templates()
                .resolveTemplateKeyForWorld(loc.getWorld().getName())
                .filter(k -> k.equalsIgnoreCase(templateKey))
                .isPresent();
    }

    private ItemStack button(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            if (lore != null && !lore.isEmpty()) {
                im.lore(lore.stream()
                    .map(LegacyComponentSerializer.legacySection()::deserialize)
                    .collect(Collectors.toList()));
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private ItemStack packageItem(Location loc, CrateType type, String tier, String name) {
        ItemStack it = new ItemStack(type.displayBlock);
        ItemMeta im = it.getItemMeta();
        
        if (im != null) {
            Component displayName = Component.text(type.name(), NamedTextColor.YELLOW);
            if (tier != null && !tier.isEmpty()) {
                String tierName = HungerGamesPlugin.get().envoys().getTierName(tier, loc);
                displayName = displayName.append(Component.text(" " + tierName, NamedTextColor.AQUA));
            }
            if (name != null && !name.isEmpty()) {
                displayName = displayName.append(Component.text(" (" + name + ")", NamedTextColor.GRAY));
            }
            im.displayName(displayName);
            
            List<Component> lore = new ArrayList<>();
            if (loc.getWorld() != null) {
                lore.add(Component.text("World: ", NamedTextColor.GRAY)
                    .append(Component.text(loc.getWorld().getName(), NamedTextColor.WHITE)));
            }
            lore.add(Component.text("X: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(loc.getBlockX()), NamedTextColor.WHITE)));
            lore.add(Component.text("Y: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(loc.getBlockY()), NamedTextColor.WHITE)));
            lore.add(Component.text("Z: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(loc.getBlockZ()), NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Right-click to remove", NamedTextColor.RED));
            
            im.lore(lore);
            it.setItemMeta(im);
        }
        return it;
    }

    private void build() {
        inv.clear();
        
        // Collect all active packages
        List<PackageInfo> packages = new ArrayList<>();
        
        // Add envoy packages
        for (Location loc : HungerGamesPlugin.get().envoys().getActivePackages()) {
            if (!matchesTemplate(loc)) continue;
            EnvoyManager.PackageData data = HungerGamesPlugin.get().envoys().getPackageData(loc);
            if (data != null) {
                packages.add(new PackageInfo(loc, data.type, data.tier, "Forager Crate"));
            }
        }

        for (Location loc : HungerGamesPlugin.get().giftDrops().getActiveDrops()) {
            if (!matchesTemplate(loc)) continue;
            GiftDropManager.GiftDropData data = HungerGamesPlugin.get().giftDrops().getData(loc);
            if (data != null) {
                packages.add(new PackageInfo(loc, data.type, data.tier, "Gift Drop"));
            }
        }
        
        // Sort by distance from spawn (optional) or just keep order
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, packages.size());
        
        // Fill package items (slots 10-43 in a 54-slot inventory, 6 rows)
        slotToLocation.clear();
        int slot = 10;
        for (int i = start; i < end; i++) {
            if (slot >= 44) break; // Don't overflow into navigation row
            PackageInfo pkg = packages.get(i);
            inv.setItem(slot, packageItem(pkg.loc, pkg.type, pkg.tier, pkg.name));
            slotToLocation.put(slot, pkg.loc); // Track location for removal
            slot++;
            if ((slot - 10) % 9 == 8) slot += 1; // Skip edge slots
        }
        
        // Navigation buttons
        if (page > 0) {
            inv.setItem(45, button(Material.ARROW, "§ePrevious Page", 
                    List.of("§7Go to page " + page)));
        }
        
        if (end < packages.size()) {
            inv.setItem(53, button(Material.ARROW, "§eNext Page", 
                    List.of("§7Go to page " + (page + 2))));
        }
        
        inv.setItem(49, button(Material.BARRIER, "§cBack", null));
        
        // Info display
        inv.setItem(4, button(Material.BOOK, "§bActive Packages: " + packages.size(), 
                List.of("§7Total packages in world", 
                        "§7Right-click any package", 
                        "§7to remove it")));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        
        if (slot == 49) {
            p.openInventory(new EnvoyTypesMenu(templateKey).getInventory());
            return;
        }

        if (slot == 45 && page > 0) {
            p.openInventory(new EnvoyPackagesTrackingMenu(templateKey, page - 1).getInventory());
            return;
        }

        if (slot == 53) {
            p.openInventory(new EnvoyPackagesTrackingMenu(templateKey, page + 1).getInventory());
            return;
        }
        
        // Check if clicking a package (slots 10-43)
        if (slot >= 10 && slot < 44) {
            Location pkgLoc = slotToLocation.get(slot);
            if (pkgLoc != null) {
                if (e.getClick() == org.bukkit.event.inventory.ClickType.RIGHT) {
                    e.setCancelled(true);
                    // Try to remove from envoy packages first
                    boolean removed = HungerGamesPlugin.get().envoys().removePackage(pkgLoc);
                    if (!removed) {
                        // Try gift drops
                        removed = HungerGamesPlugin.get().giftDrops().removeDrop(pkgLoc);
                    }
                    if (removed) {
                        p.sendMessage(Component.text("Package removed.", NamedTextColor.GREEN));
                        // Refresh the menu
                        p.openInventory(new EnvoyPackagesTrackingMenu(templateKey, page).getInventory());
                    } else {
                        p.sendMessage(Component.text("Could not remove package. It may have already been collected.", NamedTextColor.RED));
                    }
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
    
    private static class PackageInfo {
        final Location loc;
        final CrateType type;
        final String tier;
        final String name;
        
        PackageInfo(Location loc, CrateType type, String tier, String name) {
            this.loc = loc;
            this.type = type;
            this.tier = tier;
            this.name = name;
        }
    }
}

