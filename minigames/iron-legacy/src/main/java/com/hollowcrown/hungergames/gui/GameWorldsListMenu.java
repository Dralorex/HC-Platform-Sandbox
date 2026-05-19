package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.managers.TemplateWorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameWorldsListMenu extends MenuView {
    private static final int SIZE = 54;
    private static final int PAGE_SLOTS = 45;
    private final int page;
    private final Inventory inv;

    public GameWorldsListMenu(int page) {
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, SIZE, Component.text("Game worlds", NamedTextColor.GREEN));
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
        List<TemplateWorldManager.TemplateInfo> all = new ArrayList<>(HungerGamesPlugin.get().templates().listTemplateInfos());
        all.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        int start = page * PAGE_SLOTS;
        for (int i = 0; i < PAGE_SLOTS && start + i < all.size(); i++) {
            TemplateWorldManager.TemplateInfo t = all.get(start + i);
            inv.setItem(i, button(Material.PAPER, "§f" + t.displayName(), List.of(
                    "§7Key: §e" + t.key(),
                    "§7World: §7" + t.worldName(),
                    "§7Status: §f" + t.status(),
                    "",
                    "§eClick: edit match settings"
            )));
        }
        inv.setItem(45, button(Material.BARRIER, "§cBack", List.of()));
        if (page > 0) inv.setItem(48, button(Material.ARROW, "§ePrevious page", List.of()));
        if (start + PAGE_SLOTS < all.size()) inv.setItem(50, button(Material.ARROW, "§eNext page", List.of()));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.isOp()) return;
        int slot = e.getRawSlot();
        if (slot == 45) {
            p.openInventory(new WorldsAdminRootMenu().getInventory());
            return;
        }
        if (slot == 48 && page > 0) {
            p.openInventory(new GameWorldsListMenu(page - 1).getInventory());
            return;
        }
        List<TemplateWorldManager.TemplateInfo> all = new ArrayList<>(HungerGamesPlugin.get().templates().listTemplateInfos());
        all.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        if (slot == 50 && (page + 1) * PAGE_SLOTS < all.size()) {
            p.openInventory(new GameWorldsListMenu(page + 1).getInventory());
            return;
        }
        if (slot >= 0 && slot < PAGE_SLOTS) {
            int idx = page * PAGE_SLOTS + slot;
            if (idx >= 0 && idx < all.size()) {
                p.openInventory(new GameWorldSettingsMenu(all.get(idx).key()).getInventory());
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
