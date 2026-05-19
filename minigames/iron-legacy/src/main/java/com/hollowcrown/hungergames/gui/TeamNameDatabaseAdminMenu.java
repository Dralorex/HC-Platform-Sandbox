package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Lists preset team names; OP bulk-add via chat. */
public class TeamNameDatabaseAdminMenu extends MenuView {
    private static final int SUB_ROWS = 4;
    private static final int SUB_SIZE = SUB_ROWS * 9;
    private static final int HEAD_SLOTS = 27;
    private static final int SLOT_BACK = 31;
    private static final int SLOT_PREV = 27;
    private static final int SLOT_NEXT = 35;
    private static final int SLOT_ADD_CHAT = 29;

    private final int page;
    private final Inventory inv;

    public TeamNameDatabaseAdminMenu(int page) {
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, SUB_SIZE, Component.text("Team name database", NamedTextColor.AQUA));
        build();
    }

    private static ItemStack tagIcon(String name) {
        ItemStack it = new ItemStack(Material.NAME_TAG);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Component.text(name, NamedTextColor.WHITE));
            im.lore(List.of(Component.text(name, NamedTextColor.GRAY)));
            it.setItemMeta(im);
        }
        return it;
    }

    private static ItemStack button(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(name);
            if (lore != null && !lore.isEmpty()) im.lore(lore);
            it.setItemMeta(im);
        }
        return it;
    }

    private void build() {
        inv.clear();
        List<String> names = HungerGamesPlugin.get().teamNames().namesAlphabetical();
        int start = page * HEAD_SLOTS;
        for (int i = 0; i < HEAD_SLOTS; i++) {
            int idx = start + i;
            if (idx >= names.size()) break;
            inv.setItem(i, tagIcon(names.get(idx)));
        }
        inv.setItem(SLOT_BACK, button(Material.BOOK, Component.text("Back", NamedTextColor.GOLD),
                List.of(Component.text("Return to Worlds menu", NamedTextColor.GRAY))));
        inv.setItem(SLOT_ADD_CHAT, button(Material.WRITABLE_BOOK,
                Component.text("Add names from chat", NamedTextColor.GREEN),
                List.of(
                        Component.text("Receive prompts for each name.", NamedTextColor.GRAY),
                        Component.text("Type §estop§7 when done.", NamedTextColor.GRAY),
                        Component.text("§7Close chat (ESC) to cancel.", NamedTextColor.DARK_GRAY))));
        if (page > 0) {
            inv.setItem(SLOT_PREV, button(Material.ARROW, Component.text("Page " + page, NamedTextColor.YELLOW), List.of()));
        }
        if (start + HEAD_SLOTS < names.size()) {
            inv.setItem(SLOT_NEXT, button(Material.ARROW, Component.text("Page " + (page + 2), NamedTextColor.YELLOW), List.of()));
        }
    }

    /** Recursive chat capture until §estop§r or ESC closes chat. */
    public static void promptNextBulkName(Player p) {
        HungerGamesPlugin plugin = HungerGamesPlugin.get();
        plugin.chatInput().prompt(p,
                "Enter next unique team name, or type stop when finished.",
                line -> Bukkit.getScheduler().runTask(plugin, () -> handleBulkLine(p, line)),
                () -> Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Component.text("Team name not entered,", NamedTextColor.RED));
                    p.sendMessage(Component.text("Team Creation Canceled.", NamedTextColor.RED));
                    p.openInventory(new TeamNameDatabaseAdminMenu(0).getInventory());
                }));
    }

    private static void handleBulkLine(Player p, String line) {
        HungerGamesPlugin plugin = HungerGamesPlugin.get();
        String s = line == null ? "" : line.trim();
        if (s.equalsIgnoreCase("stop")) {
            p.sendMessage(Component.text("Finished adding names.", NamedTextColor.GREEN));
            p.openInventory(new TeamNameDatabaseAdminMenu(0).getInventory());
            return;
        }
        if (s.isEmpty()) {
            promptNextBulkName(p);
            return;
        }
        if (!plugin.teamNames().tryAddName(s)) {
            p.sendMessage(Component.text("Name already in use,", NamedTextColor.RED));
            p.sendMessage(Component.text("Try Again", NamedTextColor.RED));
            promptNextBulkName(p);
            return;
        }
        p.sendMessage(Component.text("Added: " + s, NamedTextColor.GREEN));
        promptNextBulkName(p);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.isOp()) return;
        int slot = e.getRawSlot();
        List<String> names = HungerGamesPlugin.get().teamNames().namesAlphabetical();

        if (slot == SLOT_BACK) {
            p.openInventory(new WorldsAdminRootMenu().getInventory());
            return;
        }
        if (slot == SLOT_ADD_CHAT) {
            p.closeInventory();
            p.sendMessage(Component.text("Enter unique names one at a time, Press ESC to stop", NamedTextColor.YELLOW));
            p.sendMessage(Component.text("Press ESC while chat is open to cancel.", NamedTextColor.GRAY));
            promptNextBulkName(p);
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            p.openInventory(new TeamNameDatabaseAdminMenu(page - 1).getInventory());
            return;
        }
        if (slot == SLOT_NEXT && (page + 1) * HEAD_SLOTS < names.size()) {
            p.openInventory(new TeamNameDatabaseAdminMenu(page + 1).getInventory());
            return;
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}
