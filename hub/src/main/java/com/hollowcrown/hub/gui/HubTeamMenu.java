package com.hollowcrown.hub.gui;

import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.api.team.TeamView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.team.HubLimboTeamFormation;
import com.hollowcrown.hub.team.HubTeamAutoColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Match team GUI for pre-match limbo — independent from parties (create, invite, accept).
 */
public final class HubTeamMenu extends HubMenuView {

    private static final int MAIN_SIZE = 36;
    private static final int SUB_ROWS = 4;
    private static final int SUB_SIZE = SUB_ROWS * 9;
    private static final int HEAD_SLOTS = 27;
    private static final int SLOT_BACK = 31;
    private static final int SLOT_PREV = 27;
    private static final int SLOT_NEXT = 35;

    private static final int SLOT_CREATE = 11;
    private static final int SLOT_MEMBERS = 10;
    private static final int SLOT_INVITE = 13;
    private static final int SLOT_INVITES = 15;
    private static final int SLOT_PARTY = 22;
    private static final int SLOT_INFO = 4;
    private static final int SWATCH_START = 19;

    private static final Map<ChatColor, Material> WOOLS = new LinkedHashMap<>();

    static {
        WOOLS.put(ChatColor.WHITE, Material.WHITE_WOOL);
        WOOLS.put(ChatColor.YELLOW, Material.YELLOW_WOOL);
        WOOLS.put(ChatColor.GOLD, Material.ORANGE_WOOL);
        WOOLS.put(ChatColor.RED, Material.RED_WOOL);
        WOOLS.put(ChatColor.LIGHT_PURPLE, Material.MAGENTA_WOOL);
        WOOLS.put(ChatColor.DARK_PURPLE, Material.PURPLE_WOOL);
        WOOLS.put(ChatColor.BLUE, Material.BLUE_WOOL);
        WOOLS.put(ChatColor.AQUA, Material.LIGHT_BLUE_WOOL);
        WOOLS.put(ChatColor.GREEN, Material.LIME_WOOL);
        WOOLS.put(ChatColor.DARK_GREEN, Material.GREEN_WOOL);
        WOOLS.put(ChatColor.DARK_AQUA, Material.CYAN_WOOL);
        WOOLS.put(ChatColor.GRAY, Material.LIGHT_GRAY_WOOL);
        WOOLS.put(ChatColor.DARK_GRAY, Material.GRAY_WOOL);
        WOOLS.put(ChatColor.BLACK, Material.BLACK_WOOL);
    }

    private final HollowCrownHubPlugin plugin;
    private final Player viewer;
    private final Inventory inv;

    public HubTeamMenu(HollowCrownHubPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inv = Bukkit.createInventory(this, MAIN_SIZE,
                Component.text("Match Team", NamedTextColor.YELLOW));
        build();
    }

    public static void open(HollowCrownHubPlugin plugin, Player p) {
        if (!isInLimbo(p)) {
            p.sendMessage(Component.text(
                    "The match team menu is only available inside a lobby limbo.",
                    NamedTextColor.YELLOW));
            return;
        }
        p.openInventory(new HubTeamMenu(plugin, p).getInventory());
    }

    private static boolean isInLimbo(Player p) {
        RegisteredServiceProvider<PreMatchLimboService> reg =
                Bukkit.getServicesManager().getRegistration(PreMatchLimboService.class);
        return reg != null && reg.getProvider().isInLimbo(p);
    }

    private TeamService teams() {
        RegisteredServiceProvider<TeamService> reg =
                Bukkit.getServicesManager().getRegistration(TeamService.class);
        return reg == null ? null : reg.getProvider();
    }

    private int memberCap() {
        return HubLimboTeamFormation.memberCap(plugin, viewer);
    }

    private void build() {
        inv.clear();
        TeamService teams = teams();
        Optional<TeamView> myTeam = teams == null ? Optional.empty() : teams.teamOf(viewer.getUniqueId());
        boolean legacy = HubTeamAutoColors.isLegacyColorMode(plugin, viewer);

        inv.setItem(SLOT_INFO, buildInfoItem(myTeam.orElse(null), legacy));

        if (myTeam.isEmpty()) {
            if (legacy) {
                int slot = SWATCH_START;
                for (Map.Entry<ChatColor, Material> entry : WOOLS.entrySet()) {
                    if (slot >= SWATCH_START + 14) {
                        break;
                    }
                    ChatColor color = entry.getKey();
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Click to create your team with this color", NamedTextColor.GRAY));
                    if (teams != null && teams.colorTaken(color)) {
                        lore.add(Component.text("Color taken — pick another", NamedTextColor.DARK_GRAY));
                    }
                    inv.setItem(slot++, swatchItem(entry.getValue(), color, lore));
                }
            } else {
                inv.setItem(SLOT_CREATE, button(Material.EMERALD,
                        Component.text("Create Team", NamedTextColor.GREEN),
                        List.of(
                                Component.text("Next name: " + previewName(teams), NamedTextColor.GRAY),
                                Component.text("Auto colors; invite players after creating", NamedTextColor.GRAY))));
            }
            inv.setItem(SLOT_INVITES, button(Material.PAPER,
                    Component.text("Team Invites", NamedTextColor.AQUA),
                    List.of(Component.text("Accept or decline pending invites", NamedTextColor.GRAY))));
        } else {
            TeamView team = myTeam.get();
            inv.setItem(SLOT_MEMBERS, button(Material.WRITABLE_BOOK,
                    Component.text("Team Members", NamedTextColor.YELLOW),
                    List.of(Component.text(team.name(), NamedTextColor.GRAY),
                            Component.text(team.memberCount() + "/" + memberCap(), NamedTextColor.GRAY))));
            if (team.isLeader(viewer.getUniqueId())) {
                inv.setItem(SLOT_INVITE, button(Material.WRITABLE_BOOK,
                        Component.text("Invite Players", NamedTextColor.GREEN),
                        List.of(Component.text("Invite up to team cap", NamedTextColor.GRAY))));
            }
            inv.setItem(SLOT_INVITES, button(Material.PAPER,
                    Component.text("Team Invites", NamedTextColor.AQUA),
                    List.of(Component.text("Other teams inviting you", NamedTextColor.GRAY))));
        }

        inv.setItem(SLOT_PARTY, button(Material.WRITABLE_BOOK,
                Component.text("Open Party Menu", NamedTextColor.AQUA),
                List.of(Component.text("Parties are separate from match teams", NamedTextColor.GRAY))));
    }

    private String previewName(TeamService teams) {
        return teams == null ? "Team 1" : HubLimboTeamFormation.previewNextTeamName(teams);
    }

    private ItemStack buildInfoItem(TeamView team, boolean legacy) {
        List<Component> lore = new ArrayList<>();
        Component title;
        if (team != null) {
            title = Component.text("Team: " + team.name(),
                    team.displayRgb() != null
                            ? TextColor.color(team.displayRgb())
                            : NamedTextColor.YELLOW);
            lore.add(Component.text("Members " + team.memberCount() + "/" + memberCap(), NamedTextColor.GRAY));
            if (legacy) {
                lore.add(Component.text("Color: " + team.legacyColor().name(), NamedTextColor.GRAY));
            }
            for (UUID id : team.memberJoinOrder()) {
                lore.add(Component.text("- " + nameOf(id),
                        Bukkit.getPlayer(id) != null ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
            }
        } else {
            title = Component.text("No Team Yet: " + previewName(teams()), NamedTextColor.YELLOW);
            lore.add(Component.text("Create a team or accept an invite.", NamedTextColor.GRAY));
            lore.add(Component.text("Teams reset after each match.", NamedTextColor.DARK_GRAY));
        }
        return infoItem(Material.WHITE_BANNER, title, lore);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        TeamService teams = teams();
        Optional<TeamView> myTeam = teams == null ? Optional.empty() : teams.teamOf(p.getUniqueId());

        if (slot == SLOT_PARTY) {
            p.openInventory(new HubPartyMenu(plugin, p).getInventory());
            return;
        }

        if (myTeam.isEmpty()) {
            if (slot == SLOT_CREATE && !HubTeamAutoColors.isLegacyColorMode(plugin, p)) {
                if (teams == null) {
                    p.sendMessage(Component.text("Team service unavailable.", NamedTextColor.RED));
                    return;
                }
                HubLimboTeamFormation.createTeam(
                        plugin, p, teams, HubTeamAutoColors.nextAvailable(teams), false);
                p.openInventory(new HubTeamMenu(plugin, p).getInventory());
                return;
            }
            if (slot == SLOT_INVITES) {
                p.openInventory(new HubTeamPendingInvitesMenu(plugin, p, 0).getInventory());
                return;
            }
            if (HubTeamAutoColors.isLegacyColorMode(plugin, p)
                    && slot >= SWATCH_START && slot < SWATCH_START + WOOLS.size()) {
                handleLegacySwatch(p, e.getCurrentItem(), teams);
            }
            return;
        }

        TeamView team = myTeam.get();
        if (slot == SLOT_MEMBERS) {
            p.openInventory(new HubTeamMembersMenu(plugin, p, team, 0).getInventory());
            return;
        }
        if (slot == SLOT_INVITE && team.isLeader(p.getUniqueId())) {
            p.openInventory(new HubTeamInvitePlayersMenu(plugin, p, team, 0).getInventory());
            return;
        }
        if (slot == SLOT_INVITES) {
            p.openInventory(new HubTeamPendingInvitesMenu(plugin, p, 0).getInventory());
        }
    }

    private void handleLegacySwatch(Player p, ItemStack it, TeamService teams) {
        if (teams == null || it == null) {
            return;
        }
        ChatColor chosen = findColorBySwatch(it.getType());
        if (chosen == null) {
            return;
        }
        if (teams.colorTaken(chosen)) {
            p.sendMessage(Component.text("That color is taken. Pick another.", NamedTextColor.RED));
            return;
        }
        HubLimboTeamFormation.createTeam(
                plugin, p, teams, new HubTeamAutoColors.AutoColor(chosen, null), true);
        p.openInventory(new HubTeamMenu(plugin, p).getInventory());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    // ── submenus ─────────────────────────────────────────────────────

    public static final class HubTeamInvitePlayersMenu extends HubMenuView {
        private final HollowCrownHubPlugin plugin;
        private final Player viewer;
        private final TeamView team;
        private final int page;
        private final Inventory inv;

        HubTeamInvitePlayersMenu(HollowCrownHubPlugin plugin, Player viewer, TeamView team, int page) {
            this.plugin = plugin;
            this.viewer = viewer;
            this.team = team;
            this.page = Math.max(0, page);
            this.inv = Bukkit.createInventory(this, SUB_SIZE, Component.text("Invite to Team", NamedTextColor.GREEN));
            build();
        }

        private TeamService teams() {
            RegisteredServiceProvider<TeamService> reg =
                    Bukkit.getServicesManager().getRegistration(TeamService.class);
            return reg == null ? null : reg.getProvider();
        }

        private List<Player> candidates() {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(pl -> !pl.getUniqueId().equals(viewer.getUniqueId()))
                    .filter(pl -> {
                        TeamService ts = teams();
                        return ts == null || ts.teamOf(pl.getUniqueId()).isEmpty();
                    })
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
                        List.of(Component.text("Click to invite to " + team.name(), NamedTextColor.GRAY))));
            }
            inv.setItem(SLOT_BACK, button(Material.BOOK, Component.text("Back", NamedTextColor.GOLD), List.of()));
            if (page > 0) {
                inv.setItem(SLOT_PREV, button(Material.ARROW, Component.text("Page " + page, NamedTextColor.YELLOW), List.of()));
            }
            if (start + HEAD_SLOTS < candidates.size()) {
                inv.setItem(SLOT_NEXT, button(Material.ARROW, Component.text("Page " + (page + 2), NamedTextColor.YELLOW), List.of()));
            }
        }

        @Override
        public void onClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player p)) {
                return;
            }
            int slot = e.getRawSlot();
            if (slot == SLOT_BACK) {
                p.openInventory(new HubTeamMenu(plugin, p).getInventory());
                return;
            }
            if (slot == SLOT_PREV && page > 0) {
                p.openInventory(new HubTeamInvitePlayersMenu(plugin, p, team, page - 1).getInventory());
                return;
            }
            if (slot == SLOT_NEXT && (page + 1) * HEAD_SLOTS < candidates().size()) {
                p.openInventory(new HubTeamInvitePlayersMenu(plugin, p, team, page + 1).getInventory());
                return;
            }
            if (slot < 0 || slot >= HEAD_SLOTS) {
                return;
            }
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
            TeamService ts = teams();
            if (target == null || ts == null || !team.isLeader(p.getUniqueId())) {
                return;
            }
            if (team.memberCount() >= HubLimboTeamFormation.memberCap(plugin, p)) {
                p.sendMessage(Component.text("Your team is full.", NamedTextColor.RED));
                return;
            }
            if (ts.teamOf(target.getUniqueId()).isPresent()) {
                p.sendMessage(Component.text(target.getName() + " is already on a team.", NamedTextColor.RED));
                return;
            }
            ts.invite(target.getUniqueId(), team.name());
            p.sendMessage(Component.text("Invited " + target.getName() + " to " + team.name() + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text(
                    p.getName() + " invited you to " + team.name() + ". Open Match Team → Team Invites to accept.",
                    NamedTextColor.AQUA));
            p.openInventory(new HubTeamInvitePlayersMenu(plugin, p, team, page).getInventory());
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    public static final class HubTeamMembersMenu extends HubMenuView {
        private static final int SLOT_LEAVE = 33;

        private final HollowCrownHubPlugin plugin;
        private final Player viewer;
        private final TeamView team;
        private final int page;
        private final Inventory inv;

        HubTeamMembersMenu(HollowCrownHubPlugin plugin, Player viewer, TeamView team, int page) {
            this.plugin = plugin;
            this.viewer = viewer;
            this.team = team;
            this.page = Math.max(0, page);
            this.inv = Bukkit.createInventory(this, SUB_SIZE, Component.text("Team Members", NamedTextColor.YELLOW));
            build();
        }

        private TeamService teams() {
            RegisteredServiceProvider<TeamService> reg =
                    Bukkit.getServicesManager().getRegistration(TeamService.class);
            return reg == null ? null : reg.getProvider();
        }

        private void build() {
            inv.clear();
            List<UUID> members = new ArrayList<>(team.memberJoinOrder());
            int start = page * HEAD_SLOTS;
            for (int i = 0; i < HEAD_SLOTS; i++) {
                int idx = start + i;
                if (idx >= members.size()) {
                    break;
                }
                UUID id = members.get(idx);
                inv.setItem(i, skullHead(id, nameOf(id), List.of(
                        Component.text(team.isLeader(id) ? "Team Leader" : "Member",
                                team.isLeader(id) ? NamedTextColor.GOLD : NamedTextColor.BLUE))));
            }
            inv.setItem(SLOT_BACK, button(Material.BOOK, Component.text("Back", NamedTextColor.GOLD), List.of()));
            inv.setItem(SLOT_LEAVE, button(Material.RED_BED, Component.text("Leave Team", NamedTextColor.RED), List.of()));
            if (page > 0) {
                inv.setItem(SLOT_PREV, button(Material.ARROW, Component.text("Page " + page, NamedTextColor.YELLOW), List.of()));
            }
            if (start + HEAD_SLOTS < members.size()) {
                inv.setItem(SLOT_NEXT, button(Material.ARROW, Component.text("Page " + (page + 2), NamedTextColor.YELLOW), List.of()));
            }
        }

        @Override
        public void onClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player p)) {
                return;
            }
            TeamService ts = teams();
            if (ts == null) {
                return;
            }
            int slot = e.getRawSlot();
            if (slot == SLOT_BACK) {
                p.openInventory(new HubTeamMenu(plugin, p).getInventory());
                return;
            }
            if (slot == SLOT_LEAVE) {
                ts.removeMember(p.getUniqueId());
                p.sendMessage(Component.text("You left " + team.name() + ".", NamedTextColor.YELLOW));
                p.openInventory(new HubTeamMenu(plugin, p).getInventory());
                return;
            }
            if (slot == SLOT_PREV && page > 0) {
                p.openInventory(new HubTeamMembersMenu(plugin, p, team, page - 1).getInventory());
                return;
            }
            if (slot == SLOT_NEXT && (page + 1) * HEAD_SLOTS < team.memberCount()) {
                p.openInventory(new HubTeamMembersMenu(plugin, p, team, page + 1).getInventory());
            }
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    public static final class HubTeamPendingInvitesMenu extends HubMenuView {
        private final HollowCrownHubPlugin plugin;
        private final Player viewer;
        private final int page;
        private final Inventory inv;

        HubTeamPendingInvitesMenu(HollowCrownHubPlugin plugin, Player viewer, int page) {
            this.plugin = plugin;
            this.viewer = viewer;
            this.page = Math.max(0, page);
            this.inv = Bukkit.createInventory(this, SUB_SIZE, Component.text("Team Invites", NamedTextColor.AQUA));
            build();
        }

        private TeamService teams() {
            RegisteredServiceProvider<TeamService> reg =
                    Bukkit.getServicesManager().getRegistration(TeamService.class);
            return reg == null ? null : reg.getProvider();
        }

        private List<String> inviteNames() {
            TeamService ts = teams();
            if (ts == null) {
                return List.of();
            }
            return new ArrayList<>(ts.invites(viewer.getUniqueId()));
        }

        private void build() {
            inv.clear();
            List<String> names = inviteNames();
            int start = page * HEAD_SLOTS;
            for (int i = 0; i < HEAD_SLOTS; i++) {
                int idx = start + i;
                if (idx >= names.size()) {
                    break;
                }
                String teamName = names.get(idx);
                inv.setItem(i, button(Material.PAPER,
                        Component.text(teamName, NamedTextColor.GREEN),
                        List.of(
                                Component.text("Left-click: Accept", NamedTextColor.GRAY),
                                Component.text("Right-click: Decline", NamedTextColor.GRAY))));
            }
            inv.setItem(SLOT_BACK, button(Material.BOOK, Component.text("Back", NamedTextColor.GOLD), List.of()));
            if (page > 0) {
                inv.setItem(SLOT_PREV, button(Material.ARROW, Component.text("Page " + page, NamedTextColor.YELLOW), List.of()));
            }
            if (start + HEAD_SLOTS < names.size()) {
                inv.setItem(SLOT_NEXT, button(Material.ARROW, Component.text("Page " + (page + 2), NamedTextColor.YELLOW), List.of()));
            }
        }

        @Override
        public void onClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player p)) {
                return;
            }
            TeamService ts = teams();
            if (ts == null) {
                return;
            }
            int slot = e.getRawSlot();
            if (slot == SLOT_BACK) {
                p.openInventory(new HubTeamMenu(plugin, p).getInventory());
                return;
            }
            if (slot == SLOT_PREV && page > 0) {
                p.openInventory(new HubTeamPendingInvitesMenu(plugin, p, page - 1).getInventory());
                return;
            }
            if (slot == SLOT_NEXT && (page + 1) * HEAD_SLOTS < inviteNames().size()) {
                p.openInventory(new HubTeamPendingInvitesMenu(plugin, p, page + 1).getInventory());
                return;
            }
            if (slot < 0 || slot >= HEAD_SLOTS) {
                return;
            }
            List<String> names = inviteNames();
            int idx = page * HEAD_SLOTS + slot;
            if (idx >= names.size()) {
                return;
            }
            String teamName = names.get(idx);
            int cap = HubLimboTeamFormation.memberCap(plugin, p);
            if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                ts.declineInvite(p, teamName, false);
                p.sendMessage(Component.text("Declined invite to " + teamName + ".", NamedTextColor.YELLOW));
            } else {
                try {
                    if (ts.acceptInvite(p, teamName, cap)) {
                        p.sendMessage(Component.text("Joined " + teamName + ".", NamedTextColor.GREEN));
                    }
                } catch (IllegalStateException ex) {
                    p.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
                }
            }
            p.openInventory(new HubTeamPendingInvitesMenu(plugin, p, page).getInventory());
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    // ── shared UI helpers ────────────────────────────────────────────

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

    private static ItemStack infoItem(Material m, Component name, List<Component> lore) {
        return button(m, name, lore);
    }

    private static ItemStack swatchItem(Material wool, ChatColor color, List<Component> lore) {
        ItemStack it = new ItemStack(wool);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Component.text("Create " + prettyColorName(color), TextColor.color(namedColorOf(color))));
            if (lore != null) {
                im.lore(lore);
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private static ChatColor findColorBySwatch(Material mat) {
        for (Map.Entry<ChatColor, Material> e : WOOLS.entrySet()) {
            if (e.getValue() == mat) {
                return e.getKey();
            }
        }
        return null;
    }

    private static int namedColorOf(ChatColor c) {
        if (c == null) {
            return 0xFFFFFF;
        }
        return switch (c) {
            case BLACK -> 0x000000;
            case DARK_BLUE -> 0x0000AA;
            case DARK_GREEN -> 0x00AA00;
            case DARK_AQUA -> 0x00AAAA;
            case DARK_RED -> 0xAA0000;
            case DARK_PURPLE -> 0xAA00AA;
            case GOLD -> 0xFFAA00;
            case GRAY -> 0xAAAAAA;
            case DARK_GRAY -> 0x555555;
            case BLUE -> 0x5555FF;
            case GREEN -> 0x55FF55;
            case AQUA -> 0x55FFFF;
            case RED -> 0xFF5555;
            case LIGHT_PURPLE -> 0xFF55FF;
            case YELLOW -> 0xFFFF55;
            default -> 0xFFFFFF;
        };
    }

    private static String prettyColorName(ChatColor c) {
        if (c == null) {
            return "White";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : c.name().toLowerCase().split("_")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String nameOf(UUID id) {
        if (id == null) {
            return "?";
        }
        var off = Bukkit.getOfflinePlayer(id);
        return off.getName() != null ? off.getName() : id.toString().substring(0, 8);
    }

    static ItemStack skullHead(UUID id, String name, List<Component> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) it.getItemMeta();
        if (sm != null) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            sm.displayName(Component.text(name == null ? "?" : name, NamedTextColor.WHITE));
            if (lore != null) {
                sm.lore(lore);
            }
            it.setItemMeta(sm);
        }
        return it;
    }
}
