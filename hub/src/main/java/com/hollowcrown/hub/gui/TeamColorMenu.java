package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.List;
import java.util.UUID;

public class TeamColorMenu extends HubMenuView {
    private final Inventory inv;
    private final String teamName;
    private final UUID owner;
    private final boolean returnToLimbo;

    //private static final int SLOT_CUSTOM_HEX = 49;

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

    private ItemStack swatch(Material m, Color color, String colorName) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            if(m == Material.GLASS_BOTTLE) {
                im.customName(Component.text("Taken!", TextColor.color(color.asRGB())));
                im.lore(List.of(Component.text("Choose another color.", TextColor.color(color.asRGB()))));
            }
            else if (m == Material.POTION) {
                im.customName(Component.text("Pick " + colorName, TextColor.color(color.asRGB())));
                if(im instanceof PotionMeta pm){
                    pm.setColor(color);
                    pm.clearCustomEffects();
                    pm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                    pm.lore(List.of(Component.text("[Example]", TextColor.color(color.asRGB()))));
                }
            }
            it.setItemMeta(im);
        }
        return it;
    }

    /*private static ItemStack hexBook() {
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
    }*/

    private void build() {
        //var tm = HungerGamesPlugin.get().teams();
        Player player = Bukkit.getServer().getPlayer(owner);
        TeamManager.TeamManagerInstance teamManagerInstance;
        if(player == null)
            teamManagerInstance = TeamManager.getOrCreateWorldInstance(Bukkit.getWorlds().getFirst());
        else
            teamManagerInstance = TeamManager.getOrCreateWorldInstance(player.getWorld());

        //HungerGamesPlugin.getPlugin(HungerGamesPlugin.class).getLogger().info("Instance: " + teamManagerInstance);
        if(teamManagerInstance == null) return;
        //HungerGamesPlugin.getPlugin(HungerGamesPlugin.class).getLogger().info("identifier: " + teamManagerInstance.getIdentifier() + " teams: " + teamManagerInstance.teams);
        for (int i = 0; i < 54; i++) {
            if (teamManagerInstance.takenColors.contains(TeamManager.colorSwatch[i]))
                inv.setItem(i, swatch(Material.GLASS_BOTTLE, TeamManager.colorSwatch[i], TeamManager.colorNames[i]));
            else
                inv.setItem(i, swatch(Material.POTION, TeamManager.colorSwatch[i], TeamManager.colorNames[i]));
        }
        //inv.setItem(SLOT_CUSTOM_HEX, hexBook());
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        //int slot = e.getRawSlot();
        //HungerGamesPlugin plug = HungerGamesPlugin.get();
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(p.getWorld());
        /*if (slot == SLOT_CUSTOM_HEX) {
            ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.getType() != Material.WRITABLE_BOOK) return;
            p.closeInventory();
            plug.chatInput().prompt(p,
                    "Enter team color as #RRGGBB (or #RGB):",
                    line -> Bukkit.getScheduler().runTask(plug, () -> finishWithHex(p, line)),
                    () -> Bukkit.getScheduler().runTask(plug, () ->
                            p.openInventory(new TeamColorMenu(teamName, owner, returnToLimbo).getInventory())));
            return;
        }*/
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta() || it.getItemMeta().displayName() == null) return;
        //String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        //        .serialize(it.getItemMeta().displayName());
        Color picked = TeamManager.colorSwatch[e.getSlot()];

        //ChatColor picked = null;
        //for (ChatColor c : ALLOWED) {
        //    if (name.contains(c.name())) {
        //        picked = c;
        //        break;
        //    }
        //}
        if (picked == null) return;
        if(teamManagerInstance.takenColors.contains(picked)) {
            build();
            return;
        }
        if (!owner.equals(p.getUniqueId())) {
            p.sendMessage(Component.text("Only the team owner can confirm this menu.", NamedTextColor.RED));
            return;
        }
        //var t = plug.teams().createTeam(teamName, picked, owner);
        TeamManager.Team t = new TeamManager.Team(teamName, picked, teamManagerInstance);
        t.members.add(owner);
        p.sendMessage(Component.text("Created team ", NamedTextColor.GREEN)
                .append(Component.text(teamName, TextColor.color(picked.asRGB()))));
        //plug.game().applyTeamColors();
        t.sendFakeTeamPackets();
        TeamManager.sendFakeTeamPacketsTo(t);
        openAfterCreate(p);
    }

    /*private void finishWithHex(Player p, String line) {
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
    }*/

    private void openAfterCreate(Player p) {
        p.openInventory(new TeamMainMenu(p).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
