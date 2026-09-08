package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hub.team.TeamManager;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

public class InviteMenu extends MenuView {
    private final String teamName;
    private final int page; // 0-based
    private final Inventory inv;
    private final Map<Integer, UUID> index = new HashMap<>();

    // show up to 45 heads per page; bottom row has nav
    private static final int PAGE_SIZE = 45;

    public InviteMenu(String teamName, int page) {
        this.teamName = teamName;
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, 54, Component.text("Invite to " + teamName + " (p." + (this.page + 1) + ")", NamedTextColor.GOLD));
        build();
    }

    private void build() {
        index.clear();
        var plugin = HungerGamesPlugin.get();
        var tm = plugin.teams();

        // Candidate = every offline/online player who has joined, NOT already in a team
        List<OfflinePlayer> candidates = Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(op -> op.getUniqueId() != null && op.getName() != null)
                .filter(op -> TeamManager.findPlayerTeam(op.getUniqueId()) == null)
                .sorted(Comparator.comparing(op -> op.getName().toLowerCase()))
                .collect(Collectors.toList());

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, candidates.size());
        List<OfflinePlayer> pageList = candidates.subList(Math.min(start, candidates.size()), end);

        int slot = 0;
        for (OfflinePlayer op : pageList) {
            if (slot >= PAGE_SIZE) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(op);
                sm.displayName(Component.text(op.getName(), NamedTextColor.YELLOW));
                sm.lore(List.of(Component.text("Click to invite", NamedTextColor.GRAY)));
                head.setItemMeta(sm);
            }
            inv.setItem(slot, head);
            index.put(slot, op.getUniqueId());
            slot++;
        }

        // Nav buttons
        if (page > 0) {
            inv.setItem(45, button(Material.ARROW, "§aPrevious", List.of("Page " + page)));
        }
        if (end < candidates.size()) {
            inv.setItem(53, button(Material.ARROW, "§aNext", List.of("Page " + (page + 2))));
        }
        inv.setItem(49, button(Material.BARRIER, "§cClose", null));
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

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot >= inv.getSize()) return;

        var tm = HungerGamesPlugin.get().teams();
        var my = TeamManager.getOrCreateWorldInstance(p.getWorld()).findPlayerTeam(p.getUniqueId());
        if (my == null || !my.getLeader().equals(p.getUniqueId()) || !my.getName().equalsIgnoreCase(teamName)) {
            p.sendMessage(Component.text("You no longer own this team.", NamedTextColor.RED));
            p.closeInventory();
            return;
        }

        if (slot == 45 && inv.getItem(45) != null && inv.getItem(45).getType() == Material.ARROW && page > 0) {
            p.openInventory(new InviteMenu(teamName, page - 1).getInventory());
            return;
        }
        if (slot == 53 && inv.getItem(53) != null && inv.getItem(53).getType() == Material.ARROW) {
            p.openInventory(new InviteMenu(teamName, page + 1).getInventory());
            return;
        }
        if (slot == 49) {
            p.closeInventory();
            return;
        }

        UUID targetId = index.get(slot);
        if (targetId == null) return;
        if (TeamManager.getOrCreateWorldInstance(p.getWorld()).findPlayerTeam(targetId) != null) {
            p.sendMessage(Component.text("That player already joined a team.", NamedTextColor.RED));
            p.openInventory(new InviteMenu(teamName, page).getInventory());
            return;
        }

        //tm.invite(targetId, teamName);
        TeamManager.teamJoinInvites.add(new TeamManager.TeamJoinInvite(my, targetId));
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        p.sendMessage(Component.text("Invited ", NamedTextColor.GREEN)
                .append(Component.text(op.getName() == null ? targetId.toString() : op.getName()))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(my.getName(), TextColor.color(my.color.asRGB()))));

        if (op.isOnline()) {
            Objects.requireNonNull(op.getPlayer()).sendMessage(Component.text("You were invited to join team ", NamedTextColor.GOLD)
                    .append(Component.text(my.getName(), TextColor.color(my.color.asRGB())))
                    .append(Component.text(". Open /il gui -> Join Team.", NamedTextColor.GOLD)));

        }

        // Refresh page (their head disappears once they have a team; invite alone keeps them here,
        // but we keep the UX simple and just refresh UI)
        p.openInventory(new InviteMenu(teamName, page).getInventory());
    }

    @Override
    public Inventory getInventory() { return inv; }
}
