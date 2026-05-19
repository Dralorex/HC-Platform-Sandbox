package com.hollowcrown.hub.team;

import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.api.team.TeamView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Hub-owned team system (ported from Iron Legacy TeamManager semantics).
 * Persisted in {@code teams.yml} (invites are memory-only).
 */
public final class HubTeamService implements TeamService {

    private static final String FILE = "teams.yml";

    private final HollowCrownHubPlugin plugin;
    private final File file;
    private final Map<String, HubTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> invites = new HashMap<>();
    private final HubTeamNameDatabase nameDb;

    public HubTeamService(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE);
        this.nameDb = new HubTeamNameDatabase(plugin);
        load();
    }

    @Override
    public Collection<TeamView> teams() {
        List<TeamView> out = new ArrayList<>();
        for (HubTeam t : teams.values()) out.add(t.toView());
        return List.copyOf(out);
    }

    @Override
    public Optional<TeamView> teamByName(String name) {
        if (name == null) return Optional.empty();
        HubTeam t = teams.get(name.toLowerCase(Locale.ROOT));
        return t == null ? Optional.empty() : Optional.of(t.toView());
    }

    @Override
    public Optional<TeamView> teamOf(UUID playerId) {
        if (playerId == null) return Optional.empty();
        for (HubTeam t : teams.values()) {
            if (t.members.contains(playerId)) return Optional.of(t.toView());
        }
        return Optional.empty();
    }

    @Override
    public boolean nameTaken(String name) {
        if (name == null) return false;
        return teams.containsKey(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean colorTaken(ChatColor color) {
        if (color == null) return false;
        for (HubTeam t : teams.values()) if (color == t.legacyColor) return true;
        return false;
    }

    @Override
    public TeamView createTeam(String name, ChatColor legacyColor, UUID ownerId, Integer displayRgb) {
        Objects.requireNonNull(ownerId, "ownerId");
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw new IllegalArgumentException("Team name is blank.");
        }
        ChatColor c = legacyColor == null ? ChatColor.WHITE : legacyColor;
        HubTeam t = new HubTeam(n, c, ownerId, displayRgb);
        t.members.add(ownerId);
        t.memberJoinOrder.add(ownerId);
        teams.put(n.toLowerCase(Locale.ROOT), t);
        save();
        return t.toView();
    }

    @Override
    public void addMember(String teamName, UUID memberId) {
        if (teamName == null || memberId == null) return;
        HubTeam t = teams.get(teamName.toLowerCase(Locale.ROOT));
        if (t == null) throw new IllegalStateException("Unknown team: " + teamName);
        int cap = defaultMemberCap();
        if (t.members.size() >= cap) throw new IllegalStateException("Team is full.");
        if (teamOf(memberId).isPresent()) throw new IllegalStateException("Player already in a team.");
        t.members.add(memberId);
        t.memberJoinOrder.add(memberId);
        clearInvite(memberId, t.name);
        save();
    }

    @Override
    public boolean removeMember(UUID memberId) {
        if (memberId == null) return false;
        for (var it = teams.entrySet().iterator(); it.hasNext(); ) {
            HubTeam t = it.next().getValue();
            if (t.members.remove(memberId)) {
                t.memberJoinOrder.remove(memberId);
                if (t.members.isEmpty()) {
                    it.remove();
                } else if (Objects.equals(t.ownerId, memberId)) {
                    UUID next = t.nextLeader();
                    if (next != null) t.ownerId = next;
                }
                save();
                return true;
            }
        }
        return false;
    }

    @Override
    public void clearAllTeams() {
        teams.clear();
        invites.clear();
        save();
    }

    @Override
    public void invite(UUID toPlayerId, String teamName) {
        if (toPlayerId == null || teamName == null || teamName.isBlank()) return;
        invites.computeIfAbsent(toPlayerId, k -> new HashSet<>()).add(teamName);
    }

    @Override
    public Set<String> invites(UUID playerId) {
        if (playerId == null) return Set.of();
        return Set.copyOf(invites.getOrDefault(playerId, Collections.emptySet()));
    }

    private void clearInvite(UUID playerId, String teamName) {
        Set<String> s = invites.get(playerId);
        if (s == null) return;
        String key = resolveInviteKey(playerId, teamName);
        if (key != null) s.remove(key);
        if (s.isEmpty()) invites.remove(playerId);
    }

    private String resolveInviteKey(UUID playerId, String teamName) {
        if (teamName == null) return null;
        Set<String> s = invites.get(playerId);
        if (s == null) return null;
        for (String x : s) {
            if (x.equalsIgnoreCase(teamName)) return x;
        }
        return null;
    }

    @Override
    public boolean declineInvite(Player player, String teamName, boolean choseDifferentTeam) {
        if (player == null || teamName == null || teamName.isBlank()) return false;
        UUID id = player.getUniqueId();
        String key = resolveInviteKey(id, teamName);
        if (key == null) return false;
        HubTeam team = teams.get(key.toLowerCase(Locale.ROOT));
        clearInvite(id, key);
        notifyTeamInviteDeclined(player, team, choseDifferentTeam);
        return true;
    }

    @Override
    public boolean acceptInvite(Player player, String teamName, int memberCapOverride) {
        if (player == null || teamName == null || teamName.isBlank()) return false;
        UUID id = player.getUniqueId();
        if (teamOf(id).isPresent()) return false;
        String key = resolveInviteKey(id, teamName);
        if (key == null) return false;
        HubTeam team = teams.get(key.toLowerCase(Locale.ROOT));
        if (team == null) return false;

        int cap = memberCapOverride > 0 ? memberCapOverride : defaultMemberCap();
        if (team.members.size() >= cap) throw new IllegalStateException("Team is full.");

        LinkedHashSet<String> all = new LinkedHashSet<>(invites.getOrDefault(id, Collections.emptySet()));
        for (String other : all) {
            if (other.equalsIgnoreCase(team.name)) continue;
            declineInvite(player, other, true);
        }

        addMember(team.name, id);

        Player owner = Bukkit.getPlayer(team.ownerId);
        if (owner != null && owner.isOnline() && !owner.getUniqueId().equals(id)) {
            owner.showTitle(Title.title(
                    Component.text("Team", NamedTextColor.GREEN),
                    Component.text(player.getName() + " joined", NamedTextColor.GREEN),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(400))
            ));
        }
        player.showTitle(Title.title(
                Component.text("Team", NamedTextColor.GREEN),
                Component.text("Joined " + team.name, NamedTextColor.GREEN),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(250))
        ));
        return true;
    }

    @Override
    public boolean autoCreateTeamsEnabled() {
        return plugin.getConfig().getBoolean("teams.autoCreateDefault", true);
    }

    @Override
    public void setAutoCreateTeamsEnabled(boolean enabled) {
        plugin.getConfig().set("teams.autoCreateDefault", enabled);
        plugin.saveConfig();
    }

    @Override
    public Collection<String> teamNameDatabaseAlphabetical() {
        return nameDb.namesAlphabetical();
    }

    @Override
    public boolean tryAddTeamNameToDatabase(String raw) {
        return nameDb.tryAddName(raw, this);
    }

    @Override
    public String takeUnusedTeamNameForNewTeam() {
        String db = nameDb.takeUnusedNameForNewTeam(this);
        if (db != null) return db;
        return nameDb.generateSequentialTeamName(this);
    }

    private int defaultMemberCap() {
        return Math.max(1, plugin.getConfig().getInt("teams.memberCap", 2));
    }

    private static void showInviteSubtitle(Player viewer, Component subtitle) {
        viewer.showTitle(Title.title(
                Component.empty(),
                subtitle,
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ofMillis(500))
        ));
    }

    private void notifyTeamInviteDeclined(Player chooser, HubTeam team, boolean choseDifferentTeam) {
        if (chooser == null || team == null) return;
        Player owner = Bukkit.getPlayer(team.ownerId);
        if (owner == null || !owner.isOnline()) return;
        Component sub = choseDifferentTeam
                ? Component.text(chooser.getName() + " joined another team", NamedTextColor.YELLOW)
                : Component.text(chooser.getName() + " declined your team invite", NamedTextColor.YELLOW);
        showInviteSubtitle(owner, sub);
    }

    private void load() {
        teams.clear();
        if (!file.exists()) return;
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yaml.getConfigurationSection("teams");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection row = sec.getConfigurationSection(key);
            if (row == null) continue;
            try {
                String name = row.getString("name", key);
                String leader = row.getString("owner", "");
                UUID ownerId = UUID.fromString(leader);
                ChatColor color = ChatColor.valueOf(row.getString("color", "WHITE").toUpperCase(Locale.ROOT));
                Integer rgb = row.contains("displayRgb") ? row.getInt("displayRgb") : null;
                HubTeam t = new HubTeam(name, color, ownerId, rgb);
                List<String> order = row.getStringList("joinOrder");
                for (String s : order) {
                    UUID u = UUID.fromString(s);
                    t.members.add(u);
                    t.memberJoinOrder.add(u);
                }
                if (!t.members.contains(ownerId)) {
                    t.members.add(ownerId);
                    t.memberJoinOrder.add(0, ownerId);
                }
                teams.put(name.toLowerCase(Locale.ROOT), t);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skip bad team row '" + key + "': " + ex.getMessage());
            }
        }
    }

    private void save() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (HubTeam t : teams.values()) {
            String base = "teams." + t.name.toLowerCase(Locale.ROOT);
            yaml.set(base + ".name", t.name);
            yaml.set(base + ".owner", t.ownerId.toString());
            yaml.set(base + ".color", t.legacyColor.name());
            if (t.displayRgb != null) {
                yaml.set(base + ".displayRgb", t.displayRgb);
            }
            yaml.set(base + ".joinOrder", t.memberJoinOrder.stream().map(UUID::toString).toList());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save teams.yml", ex);
        }
    }

    private static final class HubTeam {
        final String name;
        final ChatColor legacyColor;
        UUID ownerId;
        final Integer displayRgb;
        final Set<UUID> members = new LinkedHashSet<>();
        final List<UUID> memberJoinOrder = new ArrayList<>();

        HubTeam(String name, ChatColor legacyColor, UUID ownerId, Integer displayRgb) {
            this.name = name;
            this.legacyColor = legacyColor;
            this.ownerId = ownerId;
            this.displayRgb = displayRgb;
        }

        UUID nextLeader() {
            for (UUID id : memberJoinOrder) {
                if (members.contains(id)) return id;
            }
            for (UUID id : members) return id;
            return null;
        }

        TeamView toView() {
            return new TeamView(name, legacyColor, ownerId, displayRgb, Set.copyOf(members), List.copyOf(memberJoinOrder));
        }
    }
}

