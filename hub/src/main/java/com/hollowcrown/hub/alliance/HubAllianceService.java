package com.hollowcrown.hub.alliance;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory alliance graph for the currently-active match.
 *
 * <p>The graph is undirected and transitive: if A allies B and B allies C, then A is allied with C.
 * The {@link #connectedComponent(String)} helper returns the full transitive cluster for a team.</p>
 *
 * <p>The {@link #markedEnemies(String)} method tracks asymmetric "designated enemies" — a team can
 * paint another team orange for itself even if the other side hasn't reciprocated.</p>
 *
 * <p>Auto-break is triggered externally (via {@link #breakAllAlliances()}) when only one alliance
 * group remains alive; this also flips the {@link #areFurtherAlliancesDisabled()} flag so /ally is
 * locked for the remainder of the match. {@link #reset()} returns the service to a clean slate
 * (called at match start / template change).</p>
 *
 * <p>Pending invites time out after {@link #INVITE_TIMEOUT_TICKS} ticks; periodic
 * {@link #tickPending(long)} pruning is driven by the plugin scheduler.</p>
 */
public final class HubAllianceService {

    /** 60 seconds. */
    public static final long INVITE_TIMEOUT_TICKS = 20L * 60L;

    @SuppressWarnings("unused")
    private final HollowCrownHubPlugin plugin;

    /** Lower-cased team name → set of allied team names (lowercase). Symmetric. */
    private final Map<String, Set<String>> alliances = new ConcurrentHashMap<>();

    /** Per-team "designated enemies" set (asymmetric). */
    private final Map<String, Set<String>> designatedEnemies = new ConcurrentHashMap<>();

    /** Pending ally invites: target-team → (inviter-team → expiry-tick). */
    private final Map<String, Map<String, Long>> pendingInvites = new ConcurrentHashMap<>();

    private final AtomicLong scheduleVersion = new AtomicLong(0L);
    private volatile boolean furtherAlliancesDisabled = false;

    public HubAllianceService(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    public void reset() {
        alliances.clear();
        designatedEnemies.clear();
        pendingInvites.clear();
        furtherAlliancesDisabled = false;
        scheduleVersion.incrementAndGet();
    }

    public boolean areFurtherAlliancesDisabled() {
        return furtherAlliancesDisabled;
    }

    public boolean areAllied(String teamA, String teamB) {
        String a = norm(teamA);
        String b = norm(teamB);
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        return connectedComponent(a).contains(b);
    }

    public boolean isDesignatedEnemyOf(String viewerTeam, String otherTeam) {
        String v = norm(viewerTeam);
        String o = norm(otherTeam);
        if (v == null || o == null) return false;
        Set<String> set = designatedEnemies.get(v);
        return set != null && set.contains(o);
    }

    /**
     * Returns the connected component (alliance group) for the given team, including the team itself.
     * For an un-allied team, this returns a singleton set with that team.
     */
    public Set<String> connectedComponent(String team) {
        String start = norm(team);
        if (start == null) return Collections.emptySet();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            Set<String> neighbours = alliances.get(cur);
            if (neighbours == null) continue;
            for (String n : neighbours) {
                if (visited.add(n)) {
                    queue.add(n);
                }
            }
        }
        return visited;
    }

    /**
     * Issues a pending alliance invite from {@code inviterTeam} to {@code targetTeam}. Idempotent.
     */
    public void invite(String inviterTeam, String targetTeam) {
        String a = norm(inviterTeam);
        String b = norm(targetTeam);
        if (a == null || b == null || a.equals(b)) return;
        if (furtherAlliancesDisabled) return;
        long expiry = currentTick() + INVITE_TIMEOUT_TICKS;
        pendingInvites.computeIfAbsent(b, k -> new ConcurrentHashMap<>()).put(a, expiry);
    }

    public boolean hasPendingInviteFrom(String inviterTeam, String targetTeam) {
        String a = norm(inviterTeam);
        String b = norm(targetTeam);
        if (a == null || b == null) return false;
        Map<String, Long> map = pendingInvites.get(b);
        if (map == null) return false;
        Long expiry = map.get(a);
        if (expiry == null) return false;
        if (expiry <= currentTick()) {
            map.remove(a);
            return false;
        }
        return true;
    }

    /**
     * Accepts an invite. The caller must enforce leader-only at the command layer.
     *
     * @return true if the alliance was created or already existed.
     */
    public boolean acceptInvite(String inviterTeam, String acceptingTeam, int maxTeamsPerAlliance) {
        String a = norm(inviterTeam);
        String b = norm(acceptingTeam);
        if (a == null || b == null || a.equals(b)) return false;
        if (furtherAlliancesDisabled) return false;
        if (!hasPendingInviteFrom(a, b)) return false;

        Set<String> mergedSize = new HashSet<>();
        mergedSize.addAll(connectedComponent(a));
        mergedSize.addAll(connectedComponent(b));
        if (mergedSize.size() > Math.max(2, maxTeamsPerAlliance)) {
            return false;
        }

        alliances.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
        alliances.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
        Map<String, Long> map = pendingInvites.get(b);
        if (map != null) {
            map.remove(a);
        }
        scheduleVersion.incrementAndGet();
        return true;
    }

    /**
     * Removes the pending invite without creating an alliance. Returns true if there was one.
     */
    public boolean declineInvite(String inviterTeam, String decliningTeam) {
        String a = norm(inviterTeam);
        String b = norm(decliningTeam);
        if (a == null || b == null) return false;
        Map<String, Long> map = pendingInvites.get(b);
        if (map == null) return false;
        return map.remove(a) != null;
    }

    public void markDesignatedEnemy(String viewerTeam, String otherTeam) {
        String v = norm(viewerTeam);
        String o = norm(otherTeam);
        if (v == null || o == null || v.equals(o)) return;
        designatedEnemies.computeIfAbsent(v, k -> ConcurrentHashMap.newKeySet()).add(o);
        scheduleVersion.incrementAndGet();
    }

    public Set<String> markedEnemies(String viewerTeam) {
        String v = norm(viewerTeam);
        if (v == null) return Collections.emptySet();
        Set<String> set = designatedEnemies.get(v);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    public void breakAllAlliances() {
        alliances.clear();
        furtherAlliancesDisabled = true;
        scheduleVersion.incrementAndGet();
    }

    public long version() {
        return scheduleVersion.get();
    }

    /**
     * @return the set of all distinct alliance groups, each represented as the lower-cased team-name
     *         set of its members. Teams with no allies do not appear here; use
     *         {@link #connectedComponent(String)} for singleton lookups.
     */
    public List<Set<String>> distinctAllianceGroups() {
        List<Set<String>> out = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String t : new ArrayList<>(alliances.keySet())) {
            if (visited.contains(t)) continue;
            Set<String> comp = connectedComponent(t);
            if (comp.size() <= 1) continue;
            visited.addAll(comp);
            out.add(comp);
        }
        return out;
    }

    public void tickPending(long currentTick) {
        for (Map.Entry<String, Map<String, Long>> e : pendingInvites.entrySet()) {
            e.getValue().entrySet().removeIf(en -> en.getValue() <= currentTick);
        }
    }

    /** Resolves any online member of the given team to the canonical case-preserved name. */
    public Optional<String> resolveTeamForPlayer(UUID playerId) {
        var reg = Bukkit.getServicesManager().getRegistration(com.hollowcrown.api.team.TeamService.class);
        if (reg == null) return Optional.empty();
        return reg.getProvider().teamOf(playerId).map(t -> t.name());
    }

    /** Helper for sending a chat message to every online member of a team. */
    public void broadcastToTeam(String teamName, net.kyori.adventure.text.Component message) {
        var reg = Bukkit.getServicesManager().getRegistration(com.hollowcrown.api.team.TeamService.class);
        if (reg == null) return;
        reg.getProvider().teamByName(teamName).ifPresent(tv -> {
            for (UUID id : tv.memberJoinOrder()) {
                Player member = Bukkit.getPlayer(id);
                if (member != null && member.isOnline()) {
                    member.sendMessage(message);
                }
            }
        });
    }

    /** Helper for resolving a player's team owner / leader. */
    public Optional<UUID> leaderOf(String teamName) {
        var reg = Bukkit.getServicesManager().getRegistration(com.hollowcrown.api.team.TeamService.class);
        if (reg == null) return Optional.empty();
        return reg.getProvider().teamByName(teamName).map(t -> t.ownerId());
    }

    private static long currentTick() {
        return System.currentTimeMillis() / 50L;
    }

    private static String norm(String s) {
        if (s == null) return null;
        String n = s.trim().toLowerCase(Locale.ROOT);
        return n.isEmpty() ? null : n;
    }

    /**
     * Snapshot for diagnostics.
     */
    public Map<String, Set<String>> snapshotAlliances() {
        Map<String, Set<String>> out = new HashMap<>();
        alliances.forEach((k, v) -> out.put(k, new LinkedHashSet<>(v)));
        return out;
    }
}
