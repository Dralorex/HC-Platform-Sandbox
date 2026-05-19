package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.managers.TeamManager;
import com.hollowcrown.hungergames.model.Team;
import com.hollowcrown.hungergames.util.TeamNamingRules;
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
import java.util.stream.Collectors;

public class MainMenu extends MenuView {
    private final Inventory inv;
    private final HungerGamesPlugin plugin;
    private final TeamManager teams;

    public MainMenu(Player viewer) {
        this.plugin = HungerGamesPlugin.get();
        this.teams = plugin.teams();
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

        // Always show Teams + Join Team
        inv.setItem(11, button(Material.PLAYER_HEAD, "§aTeams", List.of("View all teams")));
        inv.setItem(15, button(Material.PLAYER_HEAD, "§bJoin Team", List.of("Teams you've been invited to")));

        // Hide "Create Team" if already in a team and not OP
        boolean inTeam = teams.getTeamOf(viewer.getUniqueId()) != null;
        boolean canCreateTeam = viewer.isOp() || !inTeam;
        if (canCreateTeam) {
            inv.setItem(13, button(Material.PLAYER_HEAD, "§eCreate Team", List.of("Create a new team")));
        }

        // Show Invite Player only if viewer owns a team
        Team t = teams.getTeamOf(viewer.getUniqueId());
        if (t != null && t.getOwner().equals(viewer.getUniqueId())) {
            inv.setItem(22, button(Material.PLAYER_HEAD, "§6Invite Player", List.of("Click to open roster")));
        }
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();

        if (slot == 11) {
            p.openInventory(new TeamsMenu().getInventory());
            return;
        }

        if (slot == 13) {
            boolean inTeam = HungerGamesPlugin.get().teams().getTeamOf(p.getUniqueId()) != null;
            if (inTeam && !p.isOp()) {
                p.sendMessage(Component.text("You're already in a team. Leave it first, or ask an OP.", NamedTextColor.RED));
                return;
            }
            HungerGamesPlugin.get().chatInput().prompt(p,
                    "Team name (max " + TeamNamingRules.MAX_NAME_LENGTH + " characters):",
                    name -> org.bukkit.Bukkit.getScheduler().runTask(HungerGamesPlugin.get(), () -> {
                        var err = TeamNamingRules.validateNewTeamName(name, HungerGamesPlugin.get(), p);
                        if (err.isPresent()) {
                            p.sendMessage(Component.text(err.get(), NamedTextColor.RED));
                            return;
                        }
                        String chosen = TeamNamingRules.stripLegacyColorCodes(name).trim();
                        p.openInventory(new TeamColorMenu(chosen, p.getUniqueId()).getInventory());
                    }), () -> org.bukkit.Bukkit.getScheduler().runTask(HungerGamesPlugin.get(), () -> {
                p.sendMessage(Component.text("Team name not entered.", NamedTextColor.RED));
                p.sendMessage(Component.text("Team creation canceled.", NamedTextColor.RED));
            }));
            p.closeInventory();
            return;
        }

        if (slot == 15) {
            p.openInventory(new JoinTeamMenu(p.getUniqueId()).getInventory());
            return;
        }

        if (slot == 22) {
            var tm = HungerGamesPlugin.get().teams();
            var my = tm.getTeamOf(p.getUniqueId());
            if (my == null || !my.getOwner().equals(p.getUniqueId())) {
                p.sendMessage(Component.text("You don't own a team.", NamedTextColor.RED));
                return;
            }
            p.openInventory(new InviteMenu(my.getName(), 0).getInventory());
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
