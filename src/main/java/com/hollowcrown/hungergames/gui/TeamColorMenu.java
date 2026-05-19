package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.TeamColorRgb;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

public class TeamColorMenu extends MenuView {
    private final Inventory inv;
    private final String teamName;
    private final UUID owner;
    private final boolean returnToLimbo;

    private static final ChatColor[] ALLOWED = {
            ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
            ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
            ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
            ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
    };

    private static final int SLOT_CUSTOM_HEX = 49;

    public TeamColorMenu(String name, UUID owner) {
        this(name, owner, false);
    }

    public TeamColorMenu(String name, UUID owner, boolean returnToLimbo) {
        this.teamName = name;
        this.owner = owner;
        this.returnToLimbo = returnToLimbo;
        this.inv = Bukkit.createInventory(this, 54, Component.text("Choose Team Color", NamedTextColor.YELLOW));
        build();
    }

    private ItemStack swatch(Material m, ChatColor color) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(LegacyComponentSerializer.legacySection().deserialize(color + "Pick " + color.name()));
            it.setItemMeta(im);
        }
        return it;
    }

    private static ItemStack hexBook() {
        ItemStack it = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Component.text("Custom hex color", NamedTextColor.LIGHT_PURPLE));
            im.lore(List.of(
                    Component.text("Click, then type #RRGGBB in chat", NamedTextColor.GRAY),
                    Component.text("Example: #FF5533 or #F53", NamedTextColor.DARK_GRAY)));
            it.setItemMeta(im);
        }
        return it;
    }

    private void build() {
        var tm = HungerGamesPlugin.get().teams();
        int i = 0;
        for (ChatColor c : ALLOWED) {
            if (tm.colorTaken(c)) continue;
            Material wool = switch (c) {
                case BLACK -> Material.BLACK_WOOL;
                case DARK_BLUE -> Material.BLUE_WOOL;
                case DARK_GREEN -> Material.GREEN_WOOL;
                case DARK_AQUA -> Material.CYAN_WOOL;
                case DARK_RED -> Material.RED_WOOL;
                case DARK_PURPLE -> Material.PURPLE_WOOL;
                case GOLD -> Material.ORANGE_WOOL;
                case GRAY -> Material.LIGHT_GRAY_WOOL;
                case DARK_GRAY -> Material.GRAY_WOOL;
                case BLUE -> Material.BLUE_WOOL;
                case GREEN -> Material.GREEN_WOOL;
                case AQUA -> Material.CYAN_WOOL;
                case RED -> Material.RED_WOOL;
                case LIGHT_PURPLE -> Material.MAGENTA_WOOL;
                case YELLOW -> Material.YELLOW_WOOL;
                case WHITE -> Material.WHITE_WOOL;
                default -> Material.WHITE_WOOL;
            };
            inv.setItem(i++, swatch(wool, c));
        }
        inv.setItem(SLOT_CUSTOM_HEX, hexBook());
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        HungerGamesPlugin plug = HungerGamesPlugin.get();
        if (slot == SLOT_CUSTOM_HEX) {
            ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.getType() != Material.WRITABLE_BOOK) return;
            p.closeInventory();
            plug.chatInput().prompt(p,
                    "Enter team color as #RRGGBB (or #RGB):",
                    line -> Bukkit.getScheduler().runTask(plug, () -> finishWithHex(p, line)),
                    () -> Bukkit.getScheduler().runTask(plug, () ->
                            p.openInventory(new TeamColorMenu(teamName, owner, returnToLimbo).getInventory())));
            return;
        }
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta() || it.getItemMeta().displayName() == null) return;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(it.getItemMeta().displayName());
        ChatColor picked = null;
        for (ChatColor c : ALLOWED) {
            if (name.contains(c.name())) {
                picked = c;
                break;
            }
        }
        if (picked == null) return;
        if (!owner.equals(p.getUniqueId())) {
            p.sendMessage(Component.text("Only the team owner can confirm this menu.", NamedTextColor.RED));
            return;
        }
        var t = plug.teams().createTeam(teamName, picked, owner);
        p.sendMessage(Component.text("Created team ", NamedTextColor.GREEN)
                .append(LegacyComponentSerializer.legacySection().deserialize(picked + t.getName())));
        plug.game().applyTeamColors();
        openAfterCreate(p);
    }

    private void finishWithHex(Player p, String line) {
        HungerGamesPlugin plug = HungerGamesPlugin.get();
        if (!owner.equals(p.getUniqueId())) {
            p.sendMessage(Component.text("Only the team owner can create this team.", NamedTextColor.RED));
            p.openInventory(new TeamColorMenu(teamName, owner, returnToLimbo).getInventory());
            return;
        }
        OptionalInt rgbOpt = TeamColorRgb.parseHexRgb(line == null ? "" : line);
        if (rgbOpt.isEmpty()) {
            p.sendMessage(Component.text("Invalid hex. Use #RRGGBB or #RGB.", NamedTextColor.RED));
            p.openInventory(new TeamColorMenu(teamName, owner, returnToLimbo).getInventory());
            return;
        }
        int rgb = rgbOpt.getAsInt();
        ChatColor legacy = TeamColorRgb.nearestChatColor(rgb);
        if (plug.teams().colorTaken(legacy)) {
            for (ChatColor c : ALLOWED) {
                if (!plug.teams().colorTaken(c)) {
                    legacy = c;
                    break;
                }
            }
        }
        var t = plug.teams().createTeam(teamName, legacy, owner, rgb);
        p.sendMessage(Component.text("Created team ", NamedTextColor.GREEN)
                .append(Component.text(t.getName(), TextColor.color(0xFF000000 | (0xFFFFFF & rgb)))));
        plug.game().applyTeamColors();
        openAfterCreate(p);
    }

    private void openAfterCreate(Player p) {
        p.openInventory(new MainMenu(p).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
