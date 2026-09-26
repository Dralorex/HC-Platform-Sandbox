package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TeamsMenu extends HubMenuView {
    private final Inventory inv;

    public TeamsMenu(World world, Player viewer) {
        inv = Bukkit.createInventory(this, 54, Component.text("Teams", NamedTextColor.GREEN));
        int i = 0;
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getInstance(world);
        //HungerGamesPlugin.getPlugin(HungerGamesPlugin.class).getLogger().info("Instance: " + teamManagerInstance);
        if(teamManagerInstance == null) return;
        //HungerGamesPlugin.getPlugin(HungerGamesPlugin.class).getLogger().info("identifier: " + teamManagerInstance.getIdentifier() + " teams: " + teamManagerInstance.teams);
        //for (Team t : HungerGamesPlugin.get().teams().getTeams()) {
        TeamManager.Team viewerTeam = teamManagerInstance.findPlayerTeam(viewer);
        for(TeamManager.Team t : Stream.concat(
                teamManagerInstance.teams.values().stream().filter(s -> s.equals(viewerTeam)),
                teamManagerInstance.teams.values().stream().filter(s -> !s.equals(viewerTeam))
        ).toList()) {
            if (i >= 54) break;
            ItemStack it = new ItemStack(Material.OAK_SIGN);
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                im.displayName(Component.text(t.getName(), TextColor.color(t.color.asRGB())));
                if(t == viewerTeam) {
                    im.lore(java.util.List.of(
                            Component.text("Your team.", NamedTextColor.DARK_GRAY),
                            Component.text("Click to view and manage members.", NamedTextColor.GREEN)
                    ));
                }
                else{
                    im.lore(java.util.List.of(
                            Component.text("A team in the arena.", NamedTextColor.DARK_GRAY),
                            Component.text("Members hidden in this view.", NamedTextColor.GRAY)
                    ));
                }
                it.setItemMeta(im);
            }
            inv.setItem(i++, it);
        }
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player player) || e.getRawSlot() != 0) return;
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getInstance(player.getWorld());
        if(teamManagerInstance == null) return;
        TeamManager.Team viewerTeam = teamManagerInstance.findPlayerTeam(player);
        if(viewerTeam != null){
            player.openInventory(new TeamMembersMenu(HollowCrownHubPlugin.get(), player, viewerTeam, 0).getInventory());
        }
    }

    @Override
    public Inventory getInventory() { return inv; }
}
