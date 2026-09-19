package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hub.team.TeamManager;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TeamAllyAndRivalMenu extends MenuView {
    private final Inventory inv;
    TeamManager.TeamManagerInstance teamManagerInstance;
    Player viewer;
    TeamManager.Team team;

    public TeamAllyAndRivalMenu(Player viewer, TeamManager.Team team) {
        inv = Bukkit.createInventory(this, 54, Component.text("Teams", NamedTextColor.GREEN));
        int i = 0;
        teamManagerInstance = team.getTeamManagerInstance();
        this.team = team;
        this.viewer = viewer;
        if(teamManagerInstance == null) return;
        for(TeamManager.Team t : teamManagerInstance.teams.values()) {
            if (i >= 54) break;
            int relationship = team == t ? 0 : (team.rivals.contains(t) ? 1 : (team.allies.contains(t) ? 2 : (TeamManager.checkAllianceInvite(team, t) ? 3 : (TeamManager.checkAllianceInvite(t, team) ? 5 : 4))));
            ItemStack it = switch (relationship){
                case 0 -> new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                case 1 -> new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
                case 2 -> new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
                case 3, 5 -> new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
                default -> new ItemStack(Material.RED_STAINED_GLASS_PANE);
            };
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                im.displayName(Component.text(t.getName(), TextColor.color(t.color.asRGB())));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(switch (relationship){
                            case 0 -> "This team";
                            case 1 -> "Rivals";
                            case 2 -> "Allies";
                            case 3, 5 -> "Pending alliance";
                            default -> "Enemies";
                        }, NamedTextColor.DARK_GRAY));
                switch (relationship){
                    case 0 -> lore.add(Component.text("No action can be taken against your own team.", NamedTextColor.GRAY));
                    case 1 -> {
                        lore.add(Component.text(team.allies.size() < TeamManager.maxAllies ? "Click to offer alliance." : "Maximum alliances reached.", NamedTextColor.GRAY));
                        lore.add(Component.text("Right Click to dismiss rivalry.", NamedTextColor.GRAY));
                    }
                    case 2 -> {
                        lore.add(Component.text("Click to break alliance.", NamedTextColor.GRAY));
                        lore.add(Component.text(team.allies.size() < TeamManager.maxAllies ? "Right Click to declare rivalry." : "Maximum rivalries reached.", NamedTextColor.GRAY));
                    }
                    case 3 ->{
                        lore.add(Component.text("No action can be taken against this team", NamedTextColor.GRAY));
                        lore.add(Component.text("while an alliance is pending.", NamedTextColor.GRAY));
                    }
                    case 5 -> {
                        lore.add(Component.text("This team has offered an alliance.", NamedTextColor.GRAY));
                        lore.add(Component.text("Click to accept.", NamedTextColor.GRAY));
                    }
                    default -> {
                        lore.add(Component.text(team.allies.size() < TeamManager.maxAllies ? "Click to offer alliance." : "Maximum alliances reached.", NamedTextColor.GRAY));
                        lore.add(Component.text(team.allies.size() < TeamManager.maxAllies ? "Right Click to declare rivalry." : "Maximum rivalries reached.", NamedTextColor.GRAY));
                    }
                }
                im.lore(lore);
                it.setItemMeta(im);
            }
            inv.setItem(i++, it);
        }
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if(p != viewer)
            p.closeInventory(InventoryCloseEvent.Reason.CANT_USE);
        int slot = e.getRawSlot();
        ItemStack itemStack = null;
        try {
            itemStack = inv.getItem(slot);
        } catch (Exception ignored) {}
        if(itemStack == null){
            refresh();
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if(meta == null || !meta.hasCustomName()){
            refresh();
            return;
        }
        String desired = PlainTextComponentSerializer.plainText().serialize(meta.customName());
        Bukkit.getLogger().info("[HOLLOW CROWN DEBUG] Desired team: " + desired);
        TeamManager.Team t = teamManagerInstance.teams.get(desired.toLowerCase(Locale.ROOT));
        if(t == null){
            refresh();
            return;
        }
        int relationship = team == t ? 0 : (team.rivals.contains(t) ? 1 : (team.allies.contains(t) ? 2 : (TeamManager.checkAllianceInvite(team, t) ? 3 : (TeamManager.checkAllianceInvite(t, team) ? 5 : 4))));
        switch (relationship){
            case 1 -> {
                //lore.add(Component.text("Click to offer alliance.", NamedTextColor.GRAY));
                //lore.add(Component.text("Right Click to dismiss rivalry.", NamedTextColor.GRAY));
                if(e.isLeftClick()){
                    if(!TeamManager.allianceAddCommand(viewer, desired))
                        p.closeInventory(InventoryCloseEvent.Reason.CANT_USE);
                    else
                        refresh();
                }
                else{
                    if(!TeamManager.rivalRemoveCommand(viewer, desired))
                        p.closeInventory(InventoryCloseEvent.Reason.CANT_USE);
                    else
                        refresh();
                }
            }
            case 2 -> {
                //lore.add(Component.text("Click to break alliance.", NamedTextColor.GRAY));
                //lore.add(Component.text("Right Click to declare rivalry.", NamedTextColor.GRAY));
                if(e.isLeftClick()){
                    if(!TeamManager.allianceRemoveCommand(viewer, desired))
                        p.closeInventory(InventoryCloseEvent.Reason.CANT_USE);
                    else
                        refresh();
                }
                else{
                    if(!TeamManager.rivalAddCommand(viewer, desired))
                        p.closeInventory(InventoryCloseEvent.Reason.CANT_USE);
                    else
                        refresh();
                }
            }
            case 4 -> {
                //Random enemy team
                if(e.isLeftClick()){
                    if(!TeamManager.allianceAddCommand(viewer, desired))
                        p.closeInventory(InventoryCloseEvent.Reason.CANT_USE);
                    else
                        refresh();
                }
                else{
                    if(!TeamManager.rivalAddCommand(viewer, desired))
                        p.closeInventory(InventoryCloseEvent.Reason.CANT_USE);
                    else
                        refresh();
                }
            }
            case 5 -> {
                //This team is offering an alliance to your team
                if(e.isLeftClick()){
                    if(!TeamManager.allianceAddCommand(viewer, desired))
                        p.closeInventory(InventoryCloseEvent.Reason.CANT_USE);
                    else
                        refresh();
                }
            }
            default -> refresh();
        }
    }

    private void refresh(){
        viewer.openInventory(new TeamAllyAndRivalMenu(viewer, team).getInventory());
    }

    @Override
    public Inventory getInventory() { return inv; }
}
