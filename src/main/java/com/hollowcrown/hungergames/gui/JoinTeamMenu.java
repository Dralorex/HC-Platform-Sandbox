package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JoinTeamMenu extends MenuView {
    private final Inventory inv;
    private final UUID viewer;

    public JoinTeamMenu(UUID viewer) {
        this.viewer = viewer;
        inv = Bukkit.createInventory(this, 54, Component.text("Join Team", NamedTextColor.AQUA));
        var tm = HungerGamesPlugin.get().teams();
        int i = 0;
        for (String name : tm.getInvites(viewer)) {
            Team t = tm.getTeamByName(name).orElse(null);
            if (t == null) continue;
            ItemStack it = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta im = (SkullMeta) it.getItemMeta();
            if (im != null) {
                im.setOwningPlayer(Bukkit.getOfflinePlayer(t.getOwner()));
                im.displayName(LegacyComponentSerializer.legacySection().deserialize(t.getColor() + t.getName()));
                im.lore(List.of(Component.text("Click to join", NamedTextColor.GRAY)));
                it.setItemMeta(im);
            }
            inv.setItem(i++, it);
            if (i >= 54) break;
        }
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta() || it.getItemMeta().displayName() == null) return;
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName());
        var tm = HungerGamesPlugin.get().teams();
        var opt = tm.getTeamByName(plain);
        if (opt.isEmpty()) return;
        try {
            if (!tm.acceptTeamInvite(p, opt.get(), -1)) {
                p.sendMessage(Component.text("Could not accept that invite.", NamedTextColor.RED));
                return;
            }
            p.sendMessage(Component.text("Joined team ", NamedTextColor.GREEN)
                .append(LegacyComponentSerializer.legacySection().deserialize(opt.get().getColor() + opt.get().getName())));
            HungerGamesPlugin.get().game().applyTeamColors();
            p.closeInventory();
        } catch (IllegalStateException ex) {
            p.sendMessage(Component.text(ex.getMessage() != null ? ex.getMessage() : "Could not join team.", NamedTextColor.RED));
        }
    }

    @Override
    public Inventory getInventory() { return inv; }
}
