package com.hollowcrown.hub.gui;

import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.api.team.TeamView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Limbo team-setup GUI. Party leaders pick a team color here so the match starts with their
 * party already bound as a Hub-owned team; non-leaders see the current team roster.
 */
public final class HubLimboTeamMenu extends HubMenuView {

    private static final int SIZE = 27;
    private static final int SLOT_TEAM_INFO = 4;
    private static final int SLOT_OPEN_PARTY = 22;
    private static final int SWATCH_START = 9;

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

    public HubLimboTeamMenu(HollowCrownHubPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inv = Bukkit.createInventory(this, SIZE,
                Component.text("Match Team Setup", NamedTextColor.YELLOW));
        build();
    }

    /** Convenience entry-point — opens the team menu in limbo. */
    public static void open(HollowCrownHubPlugin plugin, Player p) {
        if (!isInLimbo(p)) {
            p.sendMessage(Component.text(
                    "The match-team setup is only available inside a lobby limbo.",
                    NamedTextColor.YELLOW));
            return;
        }
        p.openInventory(new HubLimboTeamMenu(plugin, p).getInventory());
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

    private PartyView partyOf(Player p) {
        RegisteredServiceProvider<PartyService> reg =
                Bukkit.getServicesManager().getRegistration(PartyService.class);
        if (reg == null) return null;
        return reg.getProvider().partyForMember(p.getUniqueId()).orElse(null);
    }

    private void build() {
        inv.clear();

        TeamService teams = teams();
        Optional<TeamView> myTeam = teams == null ? Optional.empty() : teams.teamOf(viewer.getUniqueId());
        PartyView party = partyOf(viewer);

        inv.setItem(SLOT_TEAM_INFO, buildTeamInfoItem(myTeam.orElse(null), party));

        if (party != null && party.isLeader(viewer.getUniqueId())) {
            int slot = SWATCH_START;
            for (Map.Entry<ChatColor, Material> entry : WOOLS.entrySet()) {
                if (slot >= 9 + 14) break;
                ChatColor color = entry.getKey();
                boolean isCurrent = myTeam.isPresent() && myTeam.get().legacyColor() == color;
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(
                        isCurrent ? "Currently selected" : "Click to pick this color",
                        isCurrent ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                if (teams != null && teams.colorTaken(color) && !isCurrent) {
                    lore.add(Component.text("Already taken by another team", NamedTextColor.DARK_GRAY));
                }
                inv.setItem(slot++, swatchItem(entry.getValue(), color, lore));
            }
        } else if (party == null) {
            inv.setItem(13, infoItem(Material.PAPER, Component.text("Solo Player", NamedTextColor.YELLOW),
                    List.of(Component.text("You are not in a party.", NamedTextColor.GRAY),
                            Component.text("Open the Party Menu to invite friends,", NamedTextColor.GRAY),
                            Component.text("or stay solo — a team is assigned at match start.", NamedTextColor.GRAY))));
        } else {
            inv.setItem(13, infoItem(Material.PAPER, Component.text("Party Member", NamedTextColor.YELLOW),
                    List.of(Component.text("Only your party leader can pick a team color.", NamedTextColor.GRAY),
                            Component.text("You'll join the team automatically.", NamedTextColor.GRAY))));
        }

        inv.setItem(SLOT_OPEN_PARTY, infoItem(Material.WRITABLE_BOOK,
                Component.text("Open Party Menu", NamedTextColor.AQUA),
                List.of(Component.text("Manage party invites and roster", NamedTextColor.GRAY))));
    }

    private ItemStack buildTeamInfoItem(TeamView team, PartyView party) {
        Material mat = Material.WHITE_BANNER;
        Component title;
        List<Component> lore = new ArrayList<>();

        if (team != null) {
            title = Component.text("Team: " + team.name(),
                    TextColor.color(team.displayRgb() != null
                            ? team.displayRgb()
                            : namedColorOf(team.legacyColor())));
            lore.add(Component.text("Color: " + team.legacyColor().name(), NamedTextColor.GRAY));
            for (UUID id : team.memberJoinOrder()) {
                lore.add(Component.text("- " + nameOf(id),
                        Bukkit.getPlayer(id) != null ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
            }
        } else if (party != null) {
            title = Component.text("Team: " + nameOf(party.leaderId()) + "'s Party", NamedTextColor.YELLOW);
            lore.add(Component.text(
                    party.isLeader(viewer.getUniqueId())
                            ? "Pick a color below to form your team."
                            : "Waiting for your leader to choose a color.",
                    NamedTextColor.GRAY));
            lore.add(Component.empty());
            for (UUID id : party.membersJoinOrder()) {
                lore.add(Component.text("- " + nameOf(id),
                        Bukkit.getPlayer(id) != null ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
            }
        } else {
            title = Component.text("No Team Yet", NamedTextColor.YELLOW);
            lore.add(Component.text("A team is assigned at match start.", NamedTextColor.GRAY));
        }

        return infoItem(mat, title, lore);
    }

    private static int namedColorOf(ChatColor c) {
        if (c == null) return 0xFFFFFF;
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

    private static ItemStack swatchItem(Material wool, ChatColor color, List<Component> lore) {
        ItemStack it = new ItemStack(wool);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Component.text("Pick " + prettyColorName(color),
                    TextColor.color(namedColorOf(color))));
            if (lore != null && !lore.isEmpty()) {
                im.lore(lore);
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private static String prettyColorName(ChatColor c) {
        if (c == null) return "White";
        StringBuilder sb = new StringBuilder();
        for (String part : c.name().toLowerCase().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static ItemStack infoItem(Material m, Component name, List<Component> lore) {
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

    private static String nameOf(UUID id) {
        if (id == null) return "?";
        var off = Bukkit.getOfflinePlayer(id);
        return off.getName() != null ? off.getName() : id.toString().substring(0, 8);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();

        if (slot == SLOT_OPEN_PARTY) {
            p.openInventory(new HubPartyMenu(plugin, p).getInventory());
            return;
        }

        PartyView party = partyOf(p);
        if (party == null || !party.isLeader(p.getUniqueId())) return;
        if (slot < SWATCH_START || slot >= SWATCH_START + WOOLS.size()) return;

        ItemStack it = e.getCurrentItem();
        if (it == null) return;

        ChatColor chosen = findColorBySwatch(it.getType());
        if (chosen == null) return;

        TeamService teams = teams();
        if (teams == null) {
            p.sendMessage(Component.text("Team service unavailable.", NamedTextColor.RED));
            return;
        }

        if (teams.colorTaken(chosen)) {
            Optional<TeamView> already = teams.teamOf(p.getUniqueId());
            if (already.isEmpty() || already.get().legacyColor() != chosen) {
                p.sendMessage(Component.text(
                        "Another team already uses that color. Pick a different one.",
                        NamedTextColor.RED));
                return;
            }
        }

        applyTeamColor(p, party, chosen);
        p.openInventory(new HubLimboTeamMenu(plugin, p).getInventory());
    }

    private static ChatColor findColorBySwatch(Material mat) {
        for (Map.Entry<ChatColor, Material> e : WOOLS.entrySet()) {
            if (e.getValue() == mat) return e.getKey();
        }
        return null;
    }

    private void applyTeamColor(Player leader, PartyView party, ChatColor color) {
        TeamService teams = teams();
        if (teams == null) return;

        Optional<TeamView> existing = teams.teamOf(leader.getUniqueId());
        String teamName = existing.map(TeamView::name).orElseGet(() -> resolveTeamName(party, teams));

        if (existing.isPresent()) {
            for (UUID memberId : existing.get().memberJoinOrder()) {
                teams.removeMember(memberId);
            }
        }

        teams.createTeam(teamName, color, leader.getUniqueId(), null);

        for (UUID memberId : party.membersJoinOrder()) {
            if (memberId.equals(leader.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(memberId);
            if (member == null || !member.isOnline()) continue;
            try {
                teams.addMember(teamName, memberId);
            } catch (Exception ignored) {
            }
        }

        leader.sendMessage(Component.text("Team color set to " + prettyColorName(color) + ".",
                NamedTextColor.GREEN));
        for (UUID memberId : party.membersJoinOrder()) {
            if (memberId.equals(leader.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text(
                        leader.getName() + " set your team color to " + prettyColorName(color) + ".",
                        NamedTextColor.AQUA));
            }
        }
    }

    private static String resolveTeamName(PartyView party, TeamService teams) {
        String base = party.displayName() == null || party.displayName().isBlank()
                ? "Party"
                : party.displayName().trim();
        if (base.length() > 20) base = base.substring(0, 20);
        String candidate = base;
        int bump = 2;
        while (teams.nameTaken(candidate)) {
            String suffix = " " + bump++;
            int keep = Math.max(1, 20 - suffix.length());
            String trimmed = base.length() > keep ? base.substring(0, keep) : base;
            candidate = trimmed + suffix;
            if (candidate.length() > 20) candidate = candidate.substring(0, 20);
        }
        return candidate;
    }

    @SuppressWarnings("unused")
    private static ItemStack head(UUID owner, String name, List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            meta.displayName(Component.text(name, NamedTextColor.WHITE));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}
