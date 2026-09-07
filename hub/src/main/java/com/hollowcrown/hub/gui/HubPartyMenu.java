package com.hollowcrown.hub.gui;

import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.party.HubPartyText;
import com.hollowcrown.hub.party.HubPartyListener;
import com.hollowcrown.hub.team.HubTeamAutoColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hub party GUI — layout matches the former HCIronLegacy {@code PartyMenu} (3-row main + paged submenus).
 */
public final class HubPartyMenu extends HubMenuView {

    private static final int MAIN_SIZE = 27;
    private static final int SLOT_CREATE = 11;
    private static final int SLOT_INVITES_HUB = 15;
    private static final int SLOT_MEMBERS = 10;
    private static final int SLOT_INVITE_PLAYERS = 13;
    private static final int SLOT_INVITES_IN_PARTY = 16;
    private static final int SLOT_MATCH_TEAM = 22;

    private final HollowCrownHubPlugin plugin;
    private final PartyService parties;
    private final Player viewer;
    private final Inventory inv;

    public HubPartyMenu(HollowCrownHubPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.parties = plugin.partyService();
        this.viewer = viewer;
        this.inv = Bukkit.createInventory(this, MAIN_SIZE, Component.text("Party", NamedTextColor.AQUA));
        build();
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

    private void build() {
        inv.clear();
        PartyView party = parties.partyForMember(viewer.getUniqueId()).orElse(null);
        boolean inLimbo = isViewerInLimbo();
        if (party == null) {
            inv.setItem(SLOT_CREATE, button(Material.EMERALD,
                    Component.text("Create Party", NamedTextColor.GREEN),
                    List.of(Component.text("Click to start a party", NamedTextColor.GRAY))));
            inv.setItem(SLOT_INVITES_HUB, button(Material.PAPER,
                    Component.text("Party Invites", NamedTextColor.AQUA),
                    List.of(Component.text("Open pending invites", NamedTextColor.GRAY))));
            if (inLimbo) {
                inv.setItem(SLOT_MATCH_TEAM, button(Material.WHITE_BANNER,
                        Component.text("Match Team", NamedTextColor.YELLOW),
                        List.of(Component.text(
                                HubTeamAutoColors.isLegacyColorMode(plugin, viewer)
                                        ? "Configure your match team"
                                        : "View match teams (auto colors)",
                                NamedTextColor.GRAY))));
            }
            return;
        }

        inv.setItem(SLOT_MEMBERS, button(Material.WRITABLE_BOOK,
                Component.text("Party Members", NamedTextColor.YELLOW),
                List.of(Component.text("Click to view roster", NamedTextColor.GRAY),
                        Component.text("Leader: ", NamedTextColor.GRAY)
                                .append(Component.text(nameOf(party.leaderId()), NamedTextColor.WHITE)))));
        if (party.isLeader(viewer.getUniqueId())) {
            inv.setItem(SLOT_INVITE_PLAYERS, button(Material.WRITABLE_BOOK,
                    Component.text("Invite Players", NamedTextColor.GREEN),
                    List.of(Component.text("Click to pick players to invite", NamedTextColor.GRAY))));
        }
        inv.setItem(SLOT_INVITES_IN_PARTY, button(Material.PAPER,
                Component.text("Party Invites", NamedTextColor.AQUA),
                List.of(Component.text("Click to view pending invites", NamedTextColor.GRAY))));
        if (inLimbo) {
            boolean legacyColors = HubTeamAutoColors.isLegacyColorMode(plugin, viewer);
            inv.setItem(SLOT_MATCH_TEAM, button(Material.WHITE_BANNER,
                    Component.text("Match Team", NamedTextColor.YELLOW),
                    List.of(
                            Component.text(party.isLeader(viewer.getUniqueId())
                                    ? (legacyColors
                                    ? "Pick a team color before match start"
                                    : "View your match team (auto colors)")
                                    : "View your match team",
                                    NamedTextColor.GRAY))));
        }
    }

    private boolean isViewerInLimbo() {
        RegisteredServiceProvider<PreMatchLimboService> reg =
                Bukkit.getServicesManager().getRegistration(PreMatchLimboService.class);
        return reg != null && reg.getProvider().isInLimbo(viewer);
    }

    private static String nameOf(UUID id) {
        if (id == null) {
            return "?";
        }
        var off = Bukkit.getOfflinePlayer(id);
        return off.getName() != null ? off.getName() : id.toString().substring(0, 8);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        PartyView party = parties.partyForMember(p.getUniqueId()).orElse(null);

        if (slot == SLOT_MATCH_TEAM) {
            HubTeamMenu.open(plugin, p);
            return;
        }

        if (party == null) {
            if (slot == SLOT_CREATE) {
                if (parties.createParty(p.getUniqueId()).isEmpty()) {
                    p.sendMessage(Component.text("Could not create party.", NamedTextColor.RED));
                } else {
                    p.openInventory(new HubPartyMenu(plugin, p).getInventory());
                }
            } else if (slot == SLOT_INVITES_HUB) {
                p.openInventory(new HubPartyPendingInvitesMenu(plugin, p, 0).getInventory());
            }
            return;
        }

        if (slot == SLOT_MEMBERS) {
            p.openInventory(new HubPartyMembersMenu(plugin, p, party, 0).getInventory());
            return;
        }
        if (slot == SLOT_INVITE_PLAYERS && party.isLeader(p.getUniqueId())) {
            p.openInventory(new HubPartyInvitePlayersMenu(plugin, p, party, 0).getInventory());
            return;
        }
        if (slot == SLOT_INVITES_IN_PARTY) {
            p.openInventory(new HubPartyPendingInvitesMenu(plugin, p, 0).getInventory());
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    private static final int SUB_ROWS = 4;
    private static final int SUB_SIZE = SUB_ROWS * 9;
    private static final int HEAD_SLOTS = 27;
    private static final int SLOT_BACK = 31;
    private static final int SLOT_PREV = 27;
    private static final int SLOT_NEXT = 35;

    public static final class HubPartyInvitePlayersMenu extends HubMenuView {
        private final HollowCrownHubPlugin plugin;
        private final PartyService parties;
        private final Player viewer;
        private final PartyView party;
        private final int page;
        private final Inventory inv;

        public HubPartyInvitePlayersMenu(HollowCrownHubPlugin plugin, Player viewer, PartyView party, int page) {
            this.plugin = plugin;
            this.parties = plugin.partyService();
            this.viewer = viewer;
            this.party = party;
            this.page = Math.max(0, page);
            this.inv = Bukkit.createInventory(this, SUB_SIZE, Component.text("Invite Players", NamedTextColor.GREEN));
            build();
        }

        private List<Player> candidates() {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(pl -> !pl.getUniqueId().equals(viewer.getUniqueId()))
                    .filter(pl -> !party.isMember(pl.getUniqueId()))
                    .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        private void build() {
            inv.clear();
            List<Player> candidates = candidates();
            int start = page * HEAD_SLOTS;
            for (int i = 0; i < HEAD_SLOTS; i++) {
                int idx = start + i;
                if (idx >= candidates.size()) {
                    break;
                }
                Player target = candidates.get(idx);
                inv.setItem(i, skullHead(target.getUniqueId(), target.getName(),
                        List.of(Component.text("Click to invite", NamedTextColor.GRAY))));
            }

            inv.setItem(SLOT_BACK, button(Material.BOOK,
                    Component.text("Back", NamedTextColor.GOLD),
                    List.of(Component.text("Return to Party menu", NamedTextColor.GRAY))));
            if (page > 0) {
                inv.setItem(SLOT_PREV, button(Material.ARROW,
                        Component.text("Page " + page, NamedTextColor.YELLOW),
                        List.of()));
            }
            if (start + HEAD_SLOTS < candidates.size()) {
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
                p.openInventory(new HubPartyMenu(plugin, p).getInventory());
                return;
            }
            if (slot == SLOT_PREV && page > 0) {
                p.openInventory(new HubPartyInvitePlayersMenu(plugin, p, party, page - 1).getInventory());
                return;
            }
            if (slot == SLOT_NEXT) {
                if ((page + 1) * HEAD_SLOTS < candidates().size()) {
                    p.openInventory(new HubPartyInvitePlayersMenu(plugin, p, party, page + 1).getInventory());
                }
                return;
            }
            if (slot >= 0 && slot < HEAD_SLOTS) {
                ItemStack it = e.getCurrentItem();
                if (it == null || it.getType() != Material.PLAYER_HEAD || !it.hasItemMeta()) {
                    return;
                }
                SkullMeta sm = (SkullMeta) it.getItemMeta();
                if (sm.getOwningPlayer() == null) {
                    return;
                }
                UUID tid = sm.getOwningPlayer().getUniqueId();
                Player target = Bukkit.getPlayer(tid);
                if (target == null || target.equals(p)) {
                    return;
                }
                if (!party.isLeader(p.getUniqueId())) {
                    return;
                }
                if (parties.partyForMember(target.getUniqueId()).isPresent()) {
                    p.sendMessage(Component.text()
                            .append(HubPartyText.coloredPlayerName(plugin, target))
                            .append(Component.text(" is already in a party.", NamedTextColor.RED))
                            .build());
                    return;
                }
                if (!parties.invite(p.getUniqueId(), target.getUniqueId())) {
                    p.sendMessage(Component.text("Could not invite that player.", NamedTextColor.RED));
                    return;
                }
                String partyId = parties.partyForMember(p.getUniqueId()).map(PartyView::partyId).orElse("");
                var leftover = target.getInventory().addItem(HubPartyListener.createInvitePaper(plugin, partyId));
                for (ItemStack drop : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), drop);
                }
                p.sendMessage(Component.text("Invited " + target.getName(), NamedTextColor.GREEN));
                target.sendMessage(Component.text(p.getName() + " invited you to a party. Check your inventory for paper.", NamedTextColor.AQUA));
                p.openInventory(new HubPartyInvitePlayersMenu(plugin, p, party, page).getInventory());
            }
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    public static final class HubPartyMembersMenu extends HubMenuView {
        private static final int SLOT_LEAVE = 33;

        private final HollowCrownHubPlugin plugin;
        private final PartyService parties;
        private final PartyView party;
        private final int page;
        private final Inventory inv;

        public HubPartyMembersMenu(HollowCrownHubPlugin plugin, Player viewer, PartyView party, int page) {
            this.plugin = plugin;
            this.parties = plugin.partyService();
            this.party = party;
            this.page = Math.max(0, page);
            this.inv = Bukkit.createInventory(this, SUB_SIZE, Component.text("Party Members", NamedTextColor.YELLOW));
            build();
        }

        private void build() {
            inv.clear();
            List<UUID> members = new ArrayList<>(party.membersJoinOrder());

            int start = page * HEAD_SLOTS;
            for (int i = 0; i < HEAD_SLOTS; i++) {
                int idx = start + i;
                if (idx >= members.size()) {
                    break;
                }
                UUID id = members.get(idx);
                boolean leader = party.isLeader(id);
                inv.setItem(i, skullHead(id, nameOf(id), List.of(
                        Component.text(leader ? "Party Leader" : "Members",
                                leader ? NamedTextColor.GOLD : NamedTextColor.BLUE)
                )));
            }

            inv.setItem(SLOT_BACK, button(Material.BOOK,
                    Component.text("Back", NamedTextColor.GOLD),
                    List.of(Component.text("Return to Party menu", NamedTextColor.GRAY))));
            inv.setItem(SLOT_LEAVE, button(Material.RED_BED,
                    Component.text("Leave Party", NamedTextColor.RED),
                    List.of(Component.text("Click to leave this party", NamedTextColor.GRAY))));

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
                p.openInventory(new HubPartyMenu(plugin, p).getInventory());
                return;
            }
            if (slot == SLOT_PREV && page > 0) {
                p.openInventory(new HubPartyMembersMenu(plugin, p, party, page - 1).getInventory());
                return;
            }
            if (slot == SLOT_NEXT) {
                if ((page + 1) * HEAD_SLOTS < party.memberCount()) {
                    p.openInventory(new HubPartyMembersMenu(plugin, p, party, page + 1).getInventory());
                }
                return;
            }
            if (slot == SLOT_LEAVE) {
                if (parties.leave(p.getUniqueId())) {
                    p.sendMessage(Component.text("You left the party.", NamedTextColor.YELLOW));
                }
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.openInventory(new HubPartyMenu(plugin, p).getInventory()));
            }
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    public static final class HubPartyPendingInvitesMenu extends HubMenuView {
        private final HollowCrownHubPlugin plugin;
        private final PartyService parties;
        private final Player viewer;
        private final int page;
        private final Inventory inv;

        public HubPartyPendingInvitesMenu(HollowCrownHubPlugin plugin, Player viewer, int page) {
            this.plugin = plugin;
            this.parties = plugin.partyService();
            this.viewer = viewer;
            this.page = Math.max(0, page);
            this.inv = Bukkit.createInventory(this, SUB_SIZE, Component.text("Party Invites", NamedTextColor.AQUA));
            build();
        }

        private List<String> inviteIds() {
            return parties.pendingInvitePartyIds(viewer.getUniqueId());
        }

        private void build() {
            inv.clear();
            List<String> ids = inviteIds();

            int start = page * HEAD_SLOTS;
            for (int i = 0; i < HEAD_SLOTS; i++) {
                int idx = start + i;
                if (idx >= ids.size()) {
                    break;
                }
                String partyId = ids.get(idx);
                inv.setItem(i, HubPartyListener.createInvitePaper(plugin, partyId));
            }

            inv.setItem(SLOT_BACK, button(Material.BOOK,
                    Component.text("Back", NamedTextColor.GOLD),
                    List.of(Component.text("Return to Party menu", NamedTextColor.GRAY))));
            if (page > 0) {
                inv.setItem(SLOT_PREV, button(Material.ARROW,
                        Component.text("Page " + page, NamedTextColor.YELLOW),
                        List.of()));
            }
            if (start + HEAD_SLOTS < ids.size()) {
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
                p.openInventory(new HubPartyMenu(plugin, p).getInventory());
                return;
            }
            if (slot == SLOT_PREV && page > 0) {
                p.openInventory(new HubPartyPendingInvitesMenu(plugin, p, page - 1).getInventory());
                return;
            }
            if (slot == SLOT_NEXT) {
                if ((page + 1) * HEAD_SLOTS < inviteIds().size()) {
                    p.openInventory(new HubPartyPendingInvitesMenu(plugin, p, page + 1).getInventory());
                }
                return;
            }
            if (slot >= 0 && slot < HEAD_SLOTS) {
                ItemStack it = e.getCurrentItem();
                if (it == null || it.getType() != Material.PAPER) {
                    return;
                }
                String partyId = HubPartyListener.readInvitePartyId(plugin, it);
                if (partyId == null) {
                    return;
                }
                if (e.isRightClick()) {
                    parties.declineInvite(p.getUniqueId(), partyId);
                    p.openInventory(new HubPartyPendingInvitesMenu(plugin, p, page).getInventory());
                    return;
                }
                if (parties.acceptInvite(p.getUniqueId(), partyId)) {
                    p.sendMessage(Component.text("Joined party!", NamedTextColor.GREEN));
                    p.openInventory(new HubPartyMenu(plugin, p).getInventory());
                } else {
                    p.sendMessage(Component.text("Could not accept that invite.", NamedTextColor.RED));
                    p.openInventory(new HubPartyPendingInvitesMenu(plugin, p, page).getInventory());
                }
            }
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
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
}
