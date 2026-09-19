package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hub.team.TeamManager;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class JoinTeamMenu extends MenuView {
    private final Inventory inv;
    private final UUID viewer;

    public JoinTeamMenu(UUID viewer) {
        this.viewer = viewer;
        inv = Bukkit.createInventory(this, 54, Component.text("Join Team", NamedTextColor.AQUA));
        var tm = HungerGamesPlugin.get().teams();
        int i = 0;
        for (TeamManager.TeamJoinInvite invite : TeamManager.getTeamJoinInvites(viewer)) {
            TeamManager.Team t = invite.offeringTeam;
            if (t == null) continue;
            ItemStack it = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta im = (SkullMeta) it.getItemMeta();
            if (im != null) {
                im.setOwningPlayer(Bukkit.getOfflinePlayer(t.getLeader()));
                im.customName(Component.text(t.getName(), TextColor.color(t.color.asRGB())));
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
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(p.getWorld());
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName());
        if (teamManagerInstance.teams.containsKey(plain.toLowerCase(Locale.ROOT))) return;
        TeamManager.Team team = teamManagerInstance.teams.get(plain);
        try {
            if (team.members.size() >= TeamManager.playersPerTeam || !TeamManager.checkTeamJoinInvite(team, p.getUniqueId())) {
                p.sendMessage(Component.text("Could not accept that invite.", NamedTextColor.RED));
                return;
            }
            p.sendMessage(Component.text("Joined team ", NamedTextColor.GREEN)
                .append(Component.text(team.getName(), TextColor.color(team.color.asRGB()))));
            //HungerGamesPlugin.get().game().applyTeamColors();
            TeamManager.sendFakeTeamPackets(team);
            team.members.add(p.getUniqueId());
            p.closeInventory();
        } catch (IllegalStateException ex) {
            p.sendMessage(Component.text(ex.getMessage() != null ? ex.getMessage() : "Could not join team.", NamedTextColor.RED));
        }
    }

    @Override
    public Inventory getInventory() { return inv; }
}
