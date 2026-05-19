package com.hollowcrown.hub.party;

import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.sql.SQLException;
import java.util.logging.Level;
import java.time.Duration;

/**
 * Hub parties: in-memory graph + {@code parties.yml}; invite TTL and offline disband match IL defaults.
 */
public final class HubPartyService implements PartyService {

    private static final String DEFAULT_NAME = "Party";
    private static final int FILE_VERSION = 1;

    private final HollowCrownHubPlugin plugin;
    private final DataSource jdbc;
    private final File file;
    private final Map<String, HubParty> parties = new HashMap<>();
    private final Map<UUID, HubParty> byMember = new HashMap<>();
    private final Map<UUID, Set<String>> invites = new HashMap<>();
    private final Map<UUID, Map<String, Long>> inviteCreatedAt = new HashMap<>();
    private final Map<UUID, Long> disconnectedAt = new HashMap<>();

    public HubPartyService(HollowCrownHubPlugin plugin, DataSource jdbc) {
        this.plugin = plugin;
        this.jdbc = jdbc;
        this.file = new File(plugin.getDataFolder(), "parties.yml");
        loadFromDisk();
        if (jdbc != null) {
            try {
                if (HubPartyJdbcSync.loadIfPresent(plugin, jdbc, parties, byMember, invites, inviteCreatedAt)) {
                    HubLog.info(plugin, "action=party_storage", "source=mysql", "parties=" + parties.size());
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Hub parties: MySQL load failed; using parties.yml only.", ex);
            }
        }
    }

    private long inviteTtlMs() {
        return plugin.getConfig().getLong("party.inviteTtlSeconds", 300L) * 1000L;
    }

    private long allOfflineDisbandMs() {
        return plugin.getConfig().getLong("party.allOfflineDisbandSeconds", 180L) * 1000L;
    }

    private long leaderAbandonedPromoteMs() {
        return plugin.getConfig().getLong("party.leaderAbandonedPromoteSeconds", 180L) * 1000L;
    }

    @Override
    public synchronized Optional<PartyView> partyForMember(UUID memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        HubParty p = byMember.get(memberId);
        return p == null ? Optional.empty() : Optional.of(p.toView());
    }

    @Override
    public synchronized Optional<PartyView> partyById(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return Optional.empty();
        }
        HubParty p = parties.get(partyId.trim());
        return p == null ? Optional.empty() : Optional.of(p.toView());
    }

    @Override
    public synchronized Optional<String> createParty(UUID leaderId) {
        if (leaderId == null || byMember.containsKey(leaderId)) {
            return Optional.empty();
        }
        String id = UUID.randomUUID().toString();
        HubParty p = new HubParty(id, DEFAULT_NAME, leaderId);
        p.members.add(leaderId);
        p.memberJoinOrder.add(leaderId);
        parties.put(id, p);
        byMember.put(leaderId, p);
        saveToDisk();
        HubLog.info(plugin, "action=party_create", "partyId=" + id, "leader=" + leaderId);
        return Optional.of(id);
    }

    @Override
    public synchronized boolean leave(UUID memberId) {
        if (memberId == null) {
            return false;
        }
        HubParty p = byMember.get(memberId);
        if (p == null) {
            return false;
        }
        UUID oldLeader = p.leader;
        p.members.remove(memberId);
        byMember.remove(memberId);
        p.memberJoinOrder.remove(memberId);
        disconnectedAt.remove(memberId);
        if (p.members.isEmpty()) {
            removeParty(p);
            notifyLeftParty(memberId, oldLeader, true);
            return true;
        }
        if (Objects.equals(p.leader, memberId)) {
            UUID next = chooseNewLeader(p);
            if (next != null) {
                p.leader = next;
            }
        }
        saveToDisk();
        HubLog.info(plugin, "action=party_leave", "partyId=" + p.id, "member=" + memberId);
        notifyLeftParty(memberId, oldLeader, false);
        return true;
    }

    @Override
    public synchronized boolean disband(UUID leaderId) {
        if (leaderId == null) {
            return false;
        }
        HubParty p = byMember.get(leaderId);
        if (p == null || !Objects.equals(p.leader, leaderId)) {
            return false;
        }
        String pid = p.id;
        removeParty(p);
        HubLog.info(plugin, "action=party_disband", "partyId=" + pid, "leader=" + leaderId);
        return true;
    }

    @Override
    public synchronized boolean invite(UUID leaderId, UUID targetId) {
        if (leaderId == null || targetId == null || leaderId.equals(targetId)) {
            return false;
        }
        HubParty p = byMember.get(leaderId);
        if (p == null || !Objects.equals(p.leader, leaderId)) {
            return false;
        }
        if (byMember.containsKey(targetId)) {
            return false;
        }
        if (p.members.contains(targetId)) {
            return false;
        }
        invites.computeIfAbsent(targetId, k -> new LinkedHashSet<>()).add(p.id);
        inviteCreatedAt.computeIfAbsent(targetId, k -> new HashMap<>()).put(p.id, System.currentTimeMillis());
        HubLog.info(plugin, "action=party_invite", "partyId=" + p.id, "leader=" + leaderId, "target=" + targetId);
        jdbcMirrorHotOnly();
        return true;
    }

    @Override
    public synchronized boolean kick(UUID leaderId, UUID targetMemberId) {
        if (leaderId == null || targetMemberId == null || leaderId.equals(targetMemberId)) {
            return false;
        }
        HubParty p = byMember.get(leaderId);
        if (p == null || !Objects.equals(p.leader, leaderId)) {
            return false;
        }
        if (!p.members.contains(targetMemberId)) {
            return false;
        }
        p.members.remove(targetMemberId);
        byMember.remove(targetMemberId);
        p.memberJoinOrder.remove(targetMemberId);
        disconnectedAt.remove(targetMemberId);
        if (p.members.isEmpty()) {
            removeParty(p);
            return true;
        }
        saveToDisk();
        HubLog.info(plugin, "action=party_kick", "partyId=" + p.id, "target=" + targetMemberId);
        return true;
    }

    @Override
    public synchronized boolean acceptInvite(UUID playerId, String partyId) {
        if (playerId == null || partyId == null || partyId.isBlank()) {
            return false;
        }
        pruneExpiredInvites(playerId);
        HubParty p = parties.get(partyId.trim());
        if (p == null || byMember.containsKey(playerId)) {
            return false;
        }
        Set<String> inv = invites.getOrDefault(playerId, Collections.emptySet());
        if (!inv.contains(p.id)) {
            return false;
        }
        Set<String> allInvites = new LinkedHashSet<>(inv);
        p.members.add(playerId);
        p.memberJoinOrder.add(playerId);
        byMember.put(playerId, p);
        clearInvite(playerId, p.id);
        for (String other : allInvites) {
            if (other.equals(p.id)) {
                continue;
            }
            // auto-decline other outstanding invites (joining one party cancels the rest)
            boolean declined = declineInvite(playerId, other);
            if (declined) {
                notifyLeaderPlayerChoseDifferentParty(playerId, other, p.id);
            }
        }
        saveToDisk();
        HubLog.info(plugin, "action=party_accept", "partyId=" + p.id, "member=" + playerId);
        notifyJoinedParty(playerId, p.id);
        return true;
    }

    @Override
    public synchronized boolean declineInvite(UUID playerId, String partyId) {
        if (playerId == null || partyId == null || partyId.isBlank()) {
            return false;
        }
        pruneExpiredInvites(playerId);
        if (!invites.getOrDefault(playerId, Collections.emptySet()).contains(partyId.trim())) {
            return false;
        }
        clearInvite(playerId, partyId.trim());
        jdbcMirrorHotOnly();
        notifyInviteDeclined(playerId, partyId.trim());
        return true;
    }

    @Override
    public synchronized List<String> pendingInvitePartyIds(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        pruneExpiredInvites(playerId);
        Set<String> set = invites.get(playerId);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(set));
    }

    /** Called when a player disconnects (Hub listener). */
    public synchronized void markDisconnected(UUID playerId) {
        if (playerId != null) {
            disconnectedAt.put(playerId, System.currentTimeMillis());
        }
    }

    /** Called when a player joins the server. */
    public synchronized void clearDisconnectMark(UUID playerId) {
        if (playerId != null) {
            disconnectedAt.remove(playerId);
        }
    }

    public synchronized void tickMaintenance() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(invites.keySet())) {
            pruneExpiredInvites(id);
        }
        for (HubParty p : new ArrayList<>(parties.values())) {
            if (p.members.isEmpty()) {
                removeParty(p);
                continue;
            }
            boolean anyOnline = false;
            for (UUID mid : p.members) {
                Player pl = Bukkit.getPlayer(mid);
                if (pl != null && pl.isOnline()) {
                    anyOnline = true;
                    break;
                }
            }
            if (!anyOnline) {
                long oldest = p.members.stream()
                        .map(disconnectedAt::get)
                        .filter(Objects::nonNull)
                        .min(Long::compareTo)
                        .orElse(now);
                if (now - oldest >= allOfflineDisbandMs()) {
                    HubLog.info(plugin, "action=party_timeout_disband", "partyId=" + p.id, "reason=all_offline");
                    removeParty(p);
                    continue;
                }
            }
            Player leadPl = Bukkit.getPlayer(p.leader);
            if (leadPl == null || !leadPl.isOnline()) {
                long leftAt = disconnectedAt.getOrDefault(p.leader, now);
                if (now - leftAt >= leaderAbandonedPromoteMs()) {
                    UUID replacement = chooseNewLeader(p);
                    if (replacement != null && !replacement.equals(p.leader)) {
                        p.leader = replacement;
                        saveToDisk();
                        HubLog.info(plugin, "action=party_leader_promote", "partyId=" + p.id, "newLeader=" + p.leader);
                    }
                }
            }
        }
    }

    public synchronized List<PartyView> allPartiesSnapshot() {
        List<PartyView> out = new ArrayList<>();
        for (HubParty p : parties.values()) {
            out.add(p.toView());
        }
        return List.copyOf(out);
    }

    private void removeParty(HubParty p) {
        String pid = p.id;
        for (UUID invitee : new ArrayList<>(invites.keySet())) {
            Set<String> set = invites.get(invitee);
            if (set != null && set.contains(pid)) {
                clearInvite(invitee, pid);
            }
        }
        parties.remove(pid);
        for (UUID id : new HashSet<>(p.members)) {
            byMember.remove(id);
        }
        saveToDisk();
    }

    /**
     * Prefer an online member (join order); otherwise first remaining member.
     */
    private static UUID chooseNewLeader(HubParty p) {
        for (UUID id : p.memberJoinOrder) {
            if (!p.members.contains(id)) {
                continue;
            }
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && pl.isOnline()) {
                return id;
            }
        }
        return p.nextLeader();
    }

    private void pruneExpiredInvites(UUID playerId) {
        long now = System.currentTimeMillis();
        Set<String> set = invites.get(playerId);
        if (set == null || set.isEmpty()) {
            return;
        }
        Map<String, Long> created = inviteCreatedAt.getOrDefault(playerId, Collections.emptyMap());
        List<String> expired = new ArrayList<>();
        for (String pid : new ArrayList<>(set)) {
            long at = created.getOrDefault(pid, 0L);
            if (at > 0L && now - at > inviteTtlMs()) {
                expired.add(pid);
            }
        }
        for (String pid : expired) {
            clearInvite(playerId, pid);
        }
        if (!expired.isEmpty()) {
            jdbcMirrorHotOnly();
        }
    }

    private void clearInvite(UUID playerId, String partyId) {
        Set<String> inv = invites.get(playerId);
        if (inv != null) {
            inv.remove(partyId);
            if (inv.isEmpty()) {
                invites.remove(playerId);
            }
        }
        Map<String, Long> created = inviteCreatedAt.get(playerId);
        if (created != null) {
            created.remove(partyId);
            if (created.isEmpty()) {
                inviteCreatedAt.remove(playerId);
            }
        }
    }

    private void loadFromDisk() {
        parties.clear();
        byMember.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int ver = yaml.getInt("version", 0);
        if (ver > FILE_VERSION) {
            plugin.getLogger().warning("parties.yml version " + ver + " newer than supported " + FILE_VERSION);
        }
        ConfigurationSection sec = yaml.getConfigurationSection("parties");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection row = sec.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            try {
                UUID leader = UUID.fromString(row.getString("leader", ""));
                HubParty p = new HubParty(key, row.getString("displayName", DEFAULT_NAME), leader);
                List<String> order = row.getStringList("joinOrder");
                for (String s : order) {
                    UUID u = UUID.fromString(s);
                    p.members.add(u);
                    p.memberJoinOrder.add(u);
                    byMember.put(u, p);
                }
                if (!p.members.contains(leader)) {
                    p.members.add(leader);
                    p.memberJoinOrder.add(0, leader);
                    byMember.put(leader, p);
                }
                parties.put(key, p);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skip bad party row '" + key + "': " + ex.getMessage());
            }
        }
    }

    private void saveToDisk() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", FILE_VERSION);
        for (HubParty p : parties.values()) {
            String base = "parties." + p.id;
            yaml.set(base + ".displayName", p.displayName.isEmpty() ? DEFAULT_NAME : p.displayName);
            yaml.set(base + ".leader", p.leader.toString());
            yaml.set(base + ".joinOrder", p.memberJoinOrder.stream().map(UUID::toString).toList());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save parties.yml", ex);
        }
        if (jdbc != null) {
            HubPartyJdbcSync.save(plugin, jdbc, parties, invites, inviteCreatedAt);
        }
    }

    /** Invites are not written to parties.yml; mirror them to SQL when JDBC is active. */
    private void jdbcMirrorHotOnly() {
        if (jdbc != null) {
            HubPartyJdbcSync.save(plugin, jdbc, parties, invites, inviteCreatedAt);
        }
    }

    private void notifyJoinedParty(UUID joinerId, String partyId) {
        if (joinerId == null || partyId == null || partyId.isBlank()) return;
        HubParty party = parties.get(partyId);
        if (party == null) return;

        Player joiner = Bukkit.getPlayer(joinerId);
        if (joiner != null && joiner.isOnline()) {
            joiner.sendMessage(Component.text("Joined party.", NamedTextColor.GREEN));
            joiner.showTitle(Title.title(
                    Component.text("Party", NamedTextColor.GREEN),
                    Component.text("You joined " + safeName(party.displayName), NamedTextColor.GREEN),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(250))
            ));
        }

        Player leader = Bukkit.getPlayer(party.leader);
        if (leader != null && leader.isOnline() && !leader.getUniqueId().equals(joinerId)) {
            String joinerName = joiner != null ? joiner.getName() : joinerId.toString().substring(0, 8);
            leader.sendMessage(Component.text(joinerName + " joined your party.", NamedTextColor.GREEN));
            leader.showTitle(Title.title(
                    Component.text("Party", NamedTextColor.GREEN),
                    Component.text(joinerName + " joined", NamedTextColor.GREEN),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(400))
            ));
        }
    }

    private void notifyInviteDeclined(UUID chooserId, String partyId) {
        if (chooserId == null || partyId == null || partyId.isBlank()) return;
        HubParty party = parties.get(partyId);
        if (party == null) return;
        Player chooser = Bukkit.getPlayer(chooserId);
        if (chooser != null && chooser.isOnline()) {
            chooser.sendMessage(Component.text("Invite declined.", NamedTextColor.YELLOW));
            chooser.showTitle(Title.title(
                    Component.text("Party", NamedTextColor.YELLOW),
                    Component.text("Invite declined", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(250))
            ));
        }
        Player leader = Bukkit.getPlayer(party.leader);
        if (leader != null && leader.isOnline() && !leader.getUniqueId().equals(chooserId)) {
            String chooserName = chooser != null ? chooser.getName() : chooserId.toString().substring(0, 8);
            leader.sendMessage(Component.text(chooserName + " declined your party invite.", NamedTextColor.YELLOW));
            leader.showTitle(Title.title(
                    Component.empty(),
                    Component.text(chooserName + " declined your invite", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(400))
            ));
        }
    }

    private void notifyLeaderPlayerChoseDifferentParty(UUID chooserId, String declinedPartyId, String acceptedPartyId) {
        if (chooserId == null) return;
        HubParty declined = parties.get(declinedPartyId);
        if (declined == null) return;
        Player leader = Bukkit.getPlayer(declined.leader);
        if (leader == null || !leader.isOnline()) return;
        Player chooser = Bukkit.getPlayer(chooserId);
        String chooserName = chooser != null ? chooser.getName() : chooserId.toString().substring(0, 8);
        leader.sendMessage(Component.text(chooserName + " joined a different party.", NamedTextColor.YELLOW));
        leader.showTitle(Title.title(
                Component.empty(),
                Component.text(chooserName + " joined a different party", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(400))
        ));
    }

    private void notifyLeftParty(UUID leaverId, UUID oldLeaderId, boolean disbanded) {
        Player leaver = Bukkit.getPlayer(leaverId);
        if (leaver != null && leaver.isOnline()) {
            leaver.sendMessage(Component.text("You left the party.", NamedTextColor.YELLOW));
            leaver.showTitle(Title.title(
                    Component.text("Party", NamedTextColor.YELLOW),
                    Component.text("You left", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(250))
            ));
        }
        if (oldLeaderId != null && !oldLeaderId.equals(leaverId)) {
            Player oldLeader = Bukkit.getPlayer(oldLeaderId);
            if (oldLeader != null && oldLeader.isOnline()) {
                String leaverName = leaver != null ? leaver.getName() : leaverId.toString().substring(0, 8);
                oldLeader.sendMessage(Component.text(leaverName + " left the party.", NamedTextColor.YELLOW));
                oldLeader.showTitle(Title.title(
                        Component.empty(),
                        Component.text(leaverName + " left", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(250))
                ));
            }
        }
    }

    private static String safeName(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_NAME;
        return raw;
    }
}
