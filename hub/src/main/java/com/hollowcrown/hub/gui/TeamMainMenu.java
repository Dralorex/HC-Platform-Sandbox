package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.team.TeamManager;
import com.hollowcrown.hub.team.TeamNamingRules;
import net.hydraulicicecube.fakeTeamAPI.teams.FakeTeam;
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
import java.util.Locale;
import java.util.stream.Collectors;

public class TeamMainMenu extends HubMenuView {
    private final Inventory inv;

    public TeamMainMenu(Player viewer) {
        this.inv = Bukkit.createInventory(this, 27, Component.text("HCIronLegacy", NamedTextColor.DARK_GREEN));
        build(viewer);
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

    private void build(Player viewer) {
        inv.clear();
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(viewer.getWorld());
        TeamManager.Team t = teamManagerInstance.findPlayerTeam(viewer);
        boolean inTeam = t != null;
        
        // Always show Teams + Join Team
        inv.setItem(11, button(Material.OAK_SIGN, "§aTeams", List.of("View all teams")));
        if(teamManagerInstance.allowTeamCustomization)
            inv.setItem(15, button(Material.BELL, "§bJoin Team", List.of("Teams you've been invited to")));
        else if(inTeam && t.getLeader().equals(viewer.getUniqueId()))
            inv.setItem(15, button(Material.DIAMOND_SWORD, "§bAllies & Rivals", List.of("See also:", "/ally <add|remove> <team>", "/rival <add|remove> <team>")));
        
        // Hide "Create Team" if already in a team and not OP OR team customization is disabled
        
        boolean canCreateTeam = viewer.isOp() || !inTeam;
        if (canCreateTeam && teamManagerInstance.allowTeamCustomization) {
            inv.setItem(13, button(Material.PLAYER_HEAD, "§eCreate Team", List.of("Create a new team")));
        }

        // Show Invite Player only if viewer owns a team AND team customization is enabled
        if (t != null && t.getLeader().equals(viewer.getUniqueId()) && teamManagerInstance.allowTeamCustomization) {
            inv.setItem(22, button(Material.PLAYER_HEAD, "§6Invite Player", List.of("Click to open roster")));
        }

        // [Hydraulic] New buttons below:
        // Show leave team only if viewer is on a team AND team customization is enabled
        if (t != null && teamManagerInstance.allowTeamCustomization) {
            inv.setItem(4, button(Material.OAK_DOOR, "§cLeave Team", List.of("Leave this team")));
        }
        // Show rename team only if the viewer owns a team AND team customization is enabled
        if (t != null && t.getLeader().equals(viewer.getUniqueId()) && teamManagerInstance.allowTeamCustomization) {
            inv.setItem(12, button(Material.NAME_TAG, "§9Rename Team", List.of("Click to rename team")));
        }
        // Show recolor team only if the viewer owns a team AND team customization is enabled
        if (t != null && t.getLeader().equals(viewer.getUniqueId()) && teamManagerInstance.allowTeamCustomization) {
            inv.setItem(14, button(Material.DRAGON_BREATH, "§dRecolor Team", List.of("Click to open color picker")));
        }
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(p.getWorld());
        var my = teamManagerInstance.findPlayerTeam(p.getUniqueId());

        if (slot == 11) {
            p.openInventory(new TeamsMenu(p.getWorld()).getInventory());
            return;
        }

        if (slot == 13 && teamManagerInstance.allowTeamCustomization) {
            boolean inTeam = teamManagerInstance.findPlayerTeam(p.getUniqueId()) != null;
            if (inTeam && !p.isOp()) {
                p.sendMessage(Component.text("You're already in a team. Leave it first, or ask an OP.", NamedTextColor.RED));
                return;
            }
            HollowCrownHubPlugin.get().chatInput().prompt(p,
                    "Team name (max " + TeamNamingRules.MAX_NAME_LENGTH + " characters):",
                    name -> org.bukkit.Bukkit.getScheduler().runTask(HollowCrownHubPlugin.get(), () -> {
                        var err = TeamNamingRules.validateNewTeamName(name, HollowCrownHubPlugin.get(), p);
                        if (err.isPresent()) {
                            p.sendMessage(Component.text(err.get(), NamedTextColor.RED));
                            return;
                        }
                        String chosen = TeamNamingRules.stripLegacyColorCodes(name).trim();
                        p.openInventory(new TeamColorMenu(chosen, p.getUniqueId()).getInventory());
                    }), () -> org.bukkit.Bukkit.getScheduler().runTask(HollowCrownHubPlugin.get(), () -> {
                p.sendMessage(Component.text("Team name not entered.", NamedTextColor.RED));
                p.sendMessage(Component.text("Team creation canceled.", NamedTextColor.RED));
            }));
            p.closeInventory();
            return;
        }

        if (slot == 15 && teamManagerInstance.allowTeamCustomization) {
            p.openInventory(new TeamJoinMenu(p.getUniqueId()).getInventory());
            return;
        } else if (slot == 15) {
            p.openInventory(new TeamAllyAndRivalMenu(p, my).getInventory());
        }

        if (slot == 22 && teamManagerInstance.allowTeamCustomization) {
            if (my == null || !my.getLeader().equals(p.getUniqueId())) {
                p.sendMessage(Component.text("You don't own a team.", NamedTextColor.RED));
                return;
            }
            p.openInventory(new TeamInviteMenu(my.getName(), 0, p.getWorld()).getInventory());
            return;
        }
        if (slot == 12 && teamManagerInstance.allowTeamCustomization) { //Rename
            if (my == null || !my.getLeader().equals(p.getUniqueId())) {
                p.sendMessage(Component.text("You don't own a team.", NamedTextColor.RED));
                return;
            }
            HollowCrownHubPlugin.get().chatInput().prompt(p,
                    "Team name (max " + TeamNamingRules.MAX_NAME_LENGTH + " characters):",
                    name -> org.bukkit.Bukkit.getScheduler().runTask(HollowCrownHubPlugin.get(), () -> {
                        var err = TeamNamingRules.validateNewTeamNameForRenaming(name, HollowCrownHubPlugin.get(), p);
                        if (err.isPresent()) {
                            p.sendMessage(Component.text(err.get(), NamedTextColor.RED));
                            return;
                        }
                        String chosen = TeamNamingRules.stripLegacyColorCodes(name).trim();
                        if (!my.rename(chosen)) {
                            p.sendMessage(Component.text("Cannot rename team", NamedTextColor.RED));
                            return;
                        }
                        my.sendFakeTeamPackets();
                        TeamManager.sendFakeTeamPacketsTo(my);
                        p.openInventory(new TeamMainMenu(p).getInventory());
                    }), () -> org.bukkit.Bukkit.getScheduler().runTask(HollowCrownHubPlugin.get(), () -> {
                        p.sendMessage(Component.text("Team name not entered.", NamedTextColor.RED));
                        p.sendMessage(Component.text("Team renaming canceled.", NamedTextColor.RED));
                    }));
            p.closeInventory();
            return;
        }
        if (slot == 14 && teamManagerInstance.allowTeamCustomization) { //Recolor
            if (my == null || !my.getLeader().equals(p.getUniqueId()) && teamManagerInstance.allowTeamCustomization) {
                p.sendMessage(Component.text("You don't own a team.", NamedTextColor.RED));
                return;
            }
            p.openInventory(new TeamRecolorMenu(my).getInventory());
            return;
        }

        if(slot == 4 && teamManagerInstance.allowTeamCustomization) {
            if (my == null) {
                p.sendMessage(Component.text("You aren't on a team.", NamedTextColor.RED));
                return;
            }
            my.members.remove(p.getUniqueId());
            if(my.members.isEmpty()){
                p.sendMessage(Component.text("Team disbanded.", NamedTextColor.GREEN));
                teamManagerInstance.teams.remove(my.getName().toLowerCase(Locale.ROOT));
                teamManagerInstance.takenColors.remove(my.color);
                FakeTeam fakeTeam = new FakeTeam(my.getName());
                for(Player player : Bukkit.getOnlinePlayers())
                    fakeTeam.sendRemovalTo(player);
            }
            else{
                p.sendMessage(Component.text("Team left.", NamedTextColor.GREEN));
                FakeTeam fakeTeam = new FakeTeam(my.getName());
                for(Player player : Bukkit.getOnlinePlayers())
                    fakeTeam.sendPlayerRemovalTo(player, p.getName());
            }
            TeamManager.sendFakeTeamPackets(my);
            TeamManager.sendFakeTeamPacketsTo(p);
            p.openInventory(new TeamMainMenu(p).getInventory());
            return;
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
