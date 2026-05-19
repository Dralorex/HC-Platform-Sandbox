package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.crates.CrateType;
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

public class EnvoyTypesMenu extends MenuView {
    private final Inventory inv;
    private final String templateKey;

    public EnvoyTypesMenu(String templateKey) {
        this.templateKey = templateKey;
        this.inv = Bukkit.createInventory(this, 27, Component.text(
                "Envoy Types · " + templateKey, NamedTextColor.GOLD));
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
        int i = 10;
        for (CrateType t : CrateType.values()) {
            inv.setItem(i++, button(t.displayBlock, "§e" + t.name(),
                    List.of("§7Click to view/edit packages",
                            "§7for this crate type")));
            if (i > 16) break;
        }
        inv.setItem(17, button(Material.COMPASS, "§bActive Packages",
                List.of("§7Packages currently in the world",
                        "§7(map-scoped list)",
                        "",
                        "§eClick: tracking")));
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
        if (slot == 17) {
            p.openInventory(new EnvoyPackagesTrackingMenu(templateKey, 0).getInventory());
            return;
        }
        if (slot >= 10 && slot < 10 + CrateType.values().length) {
            CrateType type = CrateType.values()[slot - 10];
            p.openInventory(new EnvoyPackagesMenu(templateKey, type, 0).getInventory());
        }
    }

    @Override public Inventory getInventory() {
        return inv;
    }
}
