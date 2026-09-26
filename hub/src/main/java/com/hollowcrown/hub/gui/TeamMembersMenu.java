package com.hollowcrown.hub.gui;

import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.team.TeamManager;
import net.hydraulicicecube.fakeTeamAPI.teams.FakeTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamMembersMenu extends HubMenuView {
    private static final int SLOT_LEAVE = 33;
    private static final int SUB_ROWS = 4;
    private static final int SUB_SIZE = SUB_ROWS * 9;
    private static final int HEAD_SLOTS = 27;
    private static final int SLOT_BACK = 31;
    private static final int SLOT_PREV = 27;
    private static final int SLOT_NEXT = 35;

    private final HollowCrownHubPlugin plugin;
    private final PartyService parties;
    private final TeamManager.Team team;
    private final int page;
    private final Inventory inv;

    public TeamMembersMenu(HollowCrownHubPlugin plugin, Player viewer, TeamManager.Team team, int page) {
        this.plugin = plugin;
        this.parties = plugin.partyService();
        this.team = team;
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, SUB_SIZE, Component.text("Team Members", NamedTextColor.YELLOW));
        build(viewer);
    }

    private void build(Player player) {
        inv.clear();
        List<UUID> members = new ArrayList<>(team.members);

        int start = page * HEAD_SLOTS;
        for (int i = 0; i < HEAD_SLOTS; i++) {
            int idx = start + i;
            if (idx >= members.size()) {
                break;
            }
            UUID id = members.get(idx);
            boolean leader = team.getLeader() == id;
            List<Component> lore = new ArrayList<>(3);
            if(leader){
                lore.add(Component.text("Team Leader", NamedTextColor.GOLD));
            }
            else{
                lore.add(Component.text("Member", NamedTextColor.BLUE));
                //plugin.getLogger().info("lore 0");
                if(team.getLeader().equals(player.getUniqueId())){
                    //plugin.getLogger().info("lore 1");
                    if(team.getTeamManagerInstance().gameNotRunning) //No kicking mid-game!
                        lore.add(Component.text("Left Click to kick from party.", NamedTextColor.GRAY));
                    lore.add(Component.text("Right Click to promote to leader.", NamedTextColor.GRAY));
                }
            }
            inv.setItem(i, skullHead(id, nameOf(id), lore));
        }

        inv.setItem(SLOT_BACK, button(Material.BOOK,
                Component.text("Back", NamedTextColor.GOLD),
                List.of(Component.text("Return to Team menu", NamedTextColor.GRAY))));
        inv.setItem(SLOT_LEAVE, button(Material.RED_BED,
                Component.text("Leave Team", NamedTextColor.RED),
                List.of(Component.text("Click to leave this team", NamedTextColor.GRAY))));

        if (page > 0) {
            inv.setItem(SLOT_PREV, button(Material.ARROW,
                    Component.text("Page " + page, NamedTextColor.YELLOW),
                    List.of()));
        }
        if (start + HEAD_SLOTS < members.size()) {
            inv.setItem(SLOT_NEXT, button(Material.ARROW,
                    Component.text("Page " + (page + 2), NamedTextColor.YELLOW),
                    List.of()));
        }
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot == SLOT_BACK) {
            p.openInventory(new TeamsMenu(p.getWorld(), p).getInventory());
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            p.openInventory(new TeamMembersMenu(plugin, p, team, page - 1).getInventory());
            return;
        }
        if (slot == SLOT_NEXT) {
            if ((page + 1) * HEAD_SLOTS < team.members.size()) {
                p.openInventory(new TeamMembersMenu(plugin, p, team, page + 1).getInventory());
            }
            return;
        }
        if (slot == SLOT_LEAVE) {
            if(team.getTeamManagerInstance().gameNotRunning) {
                if (parties.leave(p.getUniqueId())) {
                    p.sendMessage(Component.text("You left the team.", NamedTextColor.YELLOW));
                }
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.openInventory(new HubPartyMenu(plugin, p).getInventory()));
            }
            else{
                p.sendMessage(Component.text("You cannot leave a team mid game.", NamedTextColor.YELLOW));
            }
        }
        //plugin.getLogger().info("Player Head Clicked");
        if(slot <= 0 || !team.getLeader().equals(p.getUniqueId())) {
            //plugin.getLogger().info("Flag 1 (" + (slot <= 0) + ", " + (!team.getLeader().equals(p.getUniqueId())) + ") cause leader: " + team.getLeader() + ", " + p.getUniqueId());
            return;
        }
        ItemStack clicked = inv.getItem(slot);
        //plugin.getLogger().info("Item got");
        if(clicked == null) {
            //plugin.getLogger().info("Flag 2 item null");
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if(meta == null || !meta.hasCustomName()) {
            //plugin.getLogger().info("Flag 3 (" + (meta == null) + ", " + (meta != null && !meta.hasCustomName()) + ")");
            return;
        }
        String desired = PlainTextComponentSerializer.plainText().serialize(meta.customName());
        Player selected = Bukkit.getPlayer(desired);
        //plugin.getLogger().info("Custom name: " + desired + ", got player: " + selected);
        if(selected == null || selected == p) { //Can't take action against yourself!
            //plugin.getLogger().info("Flag 4 (" + (selected == null) + ", " + (selected == p) + ")");
            return;
        }
        if(team.members.contains(selected.getUniqueId())){
            //plugin.getLogger().info("selected player in team");
            if(e.isLeftClick()){
                //plugin.getLogger().info("left click");
                if(!team.getTeamManagerInstance().gameNotRunning) //No kicking mid-game!
                    return;
                removePlayerFromTeam(selected, team);
                p.openInventory(new TeamMembersMenu(plugin, p, team, page).getInventory());
            }
            else if (e.isRightClick()){
                //plugin.getLogger().info("right click");
                //Move the selected team member to the most senior position in the list, making them the leader. The previous leader is moved back to second place, making them the second most senior. This will make leadership revert to them if the new leader leaves for 5 minutes.
                team.members.remove(selected.getUniqueId());
                team.members.addFirst(selected.getUniqueId());
                p.sendMessage(Component.text("You have transferred team leadership to " + selected.getName()));
                selected.sendMessage(Component.text(p.getName() + " has promoted you to team leader."));
                p.openInventory(new TeamMembersMenu(plugin, p, team, page).getInventory());
            }
        }

    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    private static ItemStack skullHead(UUID ownerId, String displayName, List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerId));
            meta.displayName(Component.text(displayName, NamedTextColor.WHITE));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    private static String nameOf(UUID id) {
        if (id == null) {
            return "?";
        }
        var off = Bukkit.getOfflinePlayer(id);
        return off.getName() != null ? off.getName() : id.toString().substring(0, 8);
    }

    private static ItemStack button(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                im.lore(lore);
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private static void removePlayerFromTeam(Player player, TeamManager.Team team){
        player.sendMessage(Component.text("You have been removed from ").append(Component.text(team.getName(), TextColor.color(team.color.asRGB()))));
        team.members.remove(player.getUniqueId());
        FakeTeam fakeTeam = new FakeTeam(team.getName());
        for(Player otherPlayer : Bukkit.getOnlinePlayers())
            fakeTeam.sendPlayerRemovalTo(otherPlayer, player.getName());
        TeamManager.sendFakeTeamPackets(team);
        TeamManager.sendFakeTeamPacketsTo(player);
    }
}
