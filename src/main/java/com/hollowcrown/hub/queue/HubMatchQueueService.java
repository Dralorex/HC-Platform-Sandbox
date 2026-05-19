package com.hollowcrown.hub.queue;

import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.api.queue.MatchEnqueueResult;
import com.hollowcrown.api.queue.MatchQueueService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.template.HubTemplateEntry;
import com.hollowcrown.hub.template.HubTemplateRegistry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.sql.DataSource;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Per-template FIFO queue: whole parties as locked units; optional solo merge into fillable groups.
 */
public final class HubMatchQueueService implements MatchQueueService {

    /** Gson DTO for {@link #jdbcPersist()} / {@link #jdbcLoad()}. */
    public static final class QueueSnapUnit {
        public boolean partyLocked;
        public String partyId;
        public List<String> members;
    }

    private static final Gson GSON = new Gson();
    private static final Type UNIT_LIST_TYPE = new TypeToken<List<QueueSnapUnit>>() { }.getType();

    private final HollowCrownHubPlugin plugin;
    private final PartyService parties;
    private final HubTemplateRegistry templates;
    private DataSource jdbc;

    /** templateKey -> ordered units */
    private final Map<String, List<QueueUnit>> queues = new HashMap<>();
    /** player -> templateKey they are queued for */
    private final Map<UUID, String> playerToTemplate = new HashMap<>();

    public HubMatchQueueService(HollowCrownHubPlugin plugin, PartyService parties, HubTemplateRegistry templates) {
        this.plugin = plugin;
        this.parties = parties;
        this.templates = templates;
    }

    /** When MySQL is enabled, load persisted queue rows after construction. */
    public synchronized void attachJdbc(DataSource dataSource) {
        this.jdbc = dataSource;
        jdbcLoad();
    }

    private void jdbcLoad() {
        if (jdbc == null) {
            return;
        }
        try (Connection c = jdbc.getConnection();
             var ps = c.prepareStatement("SELECT template_key, payload_json FROM hc_queue_snapshot");
             var rs = ps.executeQuery()) {
            Map<String, List<QueueSnapUnit>> loaded = new LinkedHashMap<>();
            while (rs.next()) {
                String tk = rs.getString(1);
                String json = rs.getString(2);
                List<QueueSnapUnit> units = GSON.fromJson(json, UNIT_LIST_TYPE);
                if (units != null) {
                    loaded.put(tk, units);
                }
            }
            if (loaded.isEmpty()) {
                return;
            }
            queues.clear();
            playerToTemplate.clear();
            for (Map.Entry<String, List<QueueSnapUnit>> e : loaded.entrySet()) {
                String tk = e.getKey();
                List<QueueUnit> list = new ArrayList<>();
                for (QueueSnapUnit s : e.getValue()) {
                    LinkedHashSet<UUID> mem = new LinkedHashSet<>();
                    if (s.members != null) {
                        for (String u : s.members) {
                            try {
                                UUID id = UUID.fromString(u);
                                mem.add(id);
                                playerToTemplate.put(id, tk);
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                    list.add(new QueueUnit(s.partyLocked, s.partyId == null ? "" : s.partyId, mem));
                }
                queues.put(tk, list);
            }
            plugin.getLogger().info("Hub match queue: restored " + loaded.size() + " template(s) from MySQL.");
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Hub match queue: MySQL load failed; starting empty.", ex);
        }
    }

    private void jdbcPersist() {
        if (jdbc == null) {
            return;
        }
        long now = System.currentTimeMillis();
        try (Connection c = jdbc.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (var del = c.createStatement()) {
                    del.executeUpdate("DELETE FROM hc_queue_snapshot");
                }
                try (var ins = c.prepareStatement(
                        "INSERT INTO hc_queue_snapshot(template_key, payload_json, updated_ms) VALUES(?,?,?)")) {
                    for (Map.Entry<String, List<QueueUnit>> e : queues.entrySet()) {
                        List<QueueSnapUnit> snap = new ArrayList<>();
                        for (QueueUnit u : e.getValue()) {
                            QueueSnapUnit s = new QueueSnapUnit();
                            s.partyLocked = u.partyLocked;
                            s.partyId = u.partyId;
                            s.members = u.members.stream().map(UUID::toString).toList();
                            snap.add(s);
                        }
                        ins.setString(1, e.getKey());
                        ins.setString(2, GSON.toJson(snap));
                        ins.setLong(3, now);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                }
                plugin.getLogger().log(Level.SEVERE, "Hub match queue: MySQL persist failed (queue still in memory).", ex);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Hub match queue: MySQL connection failed during persist.", ex);
        }
    }

    private static String normTemplate(String templateKey) {
        return templateKey == null ? "" : templateKey.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public synchronized MatchEnqueueResult enqueue(UUID actingPlayerId, String templateKey) {
        if (actingPlayerId == null) {
            return MatchEnqueueResult.denied(MatchEnqueueResult.Status.OTHER);
        }
        String tk = normTemplate(templateKey);
        if (tk.isEmpty()) {
            return MatchEnqueueResult.denied(MatchEnqueueResult.Status.UNKNOWN_TEMPLATE);
        }
        Optional<HubTemplateEntry> tplOpt = templates.get(tk);
        if (tplOpt.isEmpty()) {
            return MatchEnqueueResult.denied(MatchEnqueueResult.Status.UNKNOWN_TEMPLATE);
        }
        HubTemplateEntry tpl = tplOpt.get();
        Player actor = Bukkit.getPlayer(actingPlayerId);
        if (actor == null || !actor.isOnline()) {
            return MatchEnqueueResult.denied(MatchEnqueueResult.Status.OFFLINE_PARTY_MEMBER);
        }
        boolean bypass = actor.hasPermission("hollowcrownhub.queue.bypass");

        Optional<PartyView> pv = parties.partyForMember(actingPlayerId);
        LinkedHashSet<UUID> unitMembers = new LinkedHashSet<>();
        boolean partyLocked;
        String partyIdLog = "";

        if (pv.isPresent()) {
            PartyView p = pv.get();
            if (!p.isLeader(actingPlayerId)) {
                return MatchEnqueueResult.denied(MatchEnqueueResult.Status.NOT_PARTY_LEADER);
            }
            partyLocked = true;
            partyIdLog = p.partyId();
            unitMembers.addAll(p.membersJoinOrder());
        } else {
            partyLocked = false;
            unitMembers.add(actingPlayerId);
        }

        if (!bypass && tpl.queueSoloOnly() && unitMembers.size() > 1) {
            return MatchEnqueueResult.denied(MatchEnqueueResult.Status.SOLO_ONLY);
        }
        int cap = Math.max(1, tpl.queueMaxUnitSize());
        if (!bypass && unitMembers.size() > cap) {
            return MatchEnqueueResult.denied(MatchEnqueueResult.Status.PARTY_TOO_LARGE);
        }
        for (UUID id : unitMembers) {
            if (playerToTemplate.containsKey(id)) {
                return MatchEnqueueResult.denied(MatchEnqueueResult.Status.ALREADY_QUEUED);
            }
        }
        for (UUID id : unitMembers) {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null || !pl.isOnline()) {
                return MatchEnqueueResult.denied(MatchEnqueueResult.Status.OFFLINE_PARTY_MEMBER);
            }
        }

        List<QueueUnit> list = queues.computeIfAbsent(tk, k -> new ArrayList<>());

        if (!partyLocked && tpl.queueSoloAutoMerge() && unitMembers.size() == 1) {
            UUID solo = unitMembers.iterator().next();
            QueueUnit tail = list.isEmpty() ? null : list.get(list.size() - 1);
            if (tail != null && !tail.partyLocked && tail.members.size() < cap) {
                tail.members.add(solo);
                playerToTemplate.put(solo, tk);
                int pos = list.indexOf(tail) + 1;
                HubLog.info(plugin, "action=queue_merge_solo", "templateKey=" + tk, "player=" + solo, "unitSize=" + tail.members.size());
                jdbcPersist();
                return MatchEnqueueResult.ok(pos);
            }
        }

        QueueUnit nu = new QueueUnit(partyLocked, partyIdLog, new LinkedHashSet<>(unitMembers));
        list.add(nu);
        for (UUID id : unitMembers) {
            playerToTemplate.put(id, tk);
        }
        HubLog.info(plugin, "action=queue_enqueue", "templateKey=" + tk, "partyLocked=" + partyLocked,
                "unitSize=" + unitMembers.size(), "partyId=" + partyIdLog);
        jdbcPersist();
        return MatchEnqueueResult.ok(list.size());
    }

    @Override
    public synchronized boolean leaveQueue(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        String tk = playerToTemplate.remove(playerId);
        if (tk == null) {
            return false;
        }
        List<QueueUnit> list = queues.get(tk);
        if (list == null) {
            return true;
        }
        for (int i = 0; i < list.size(); i++) {
            QueueUnit u = list.get(i);
            if (!u.members.contains(playerId)) {
                continue;
            }
            if (u.partyLocked) {
                Set<UUID> snapshot = new LinkedHashSet<>(u.members);
                for (UUID id : u.members) {
                    playerToTemplate.remove(id);
                }
                list.remove(i);
                if (snapshot.size() > 1) {
                    boolean actorWasLeader = parties.partyForMember(playerId)
                            .map(pv -> pv.isLeader(playerId))
                            .orElse(false);
                    notifyPartyMembersQueueDrop(playerId, snapshot, tk, actorWasLeader);
                }
            } else {
                u.members.remove(playerId);
                if (u.members.isEmpty()) {
                    list.remove(i);
                }
            }
            HubLog.info(plugin, "action=queue_leave", "templateKey=" + tk, "player=" + playerId);
            jdbcPersist();
            return true;
        }
        return true;
    }

    @Override
    public synchronized Optional<String> queuedTemplateFor(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(playerToTemplate.get(playerId));
    }

    @Override
    public synchronized List<String> describeQueue(String templateKey) {
        String tk = normTemplate(templateKey);
        List<QueueUnit> list = queues.getOrDefault(tk, List.of());
        List<String> lines = new ArrayList<>();
        int i = 1;
        for (QueueUnit u : list) {
            String kind = u.partyLocked ? "party" : (u.members.size() > 1 ? "solo+" : "solo");
            lines.add("#" + i + " " + kind + " " + u.members.size() + "p" + (u.partyId.isEmpty() ? "" : " id=" + u.partyId));
            i++;
        }
        return lines;
    }

    @Override
    public synchronized int queuedUnitCount(String templateKey) {
        return queues.getOrDefault(normTemplate(templateKey), List.of()).size();
    }

    /**
     * When a whole party unit is removed from the queue, other members were "left behind" by whoever
     * triggered the leave (disconnect, {@code /hch queue leave}, etc.). Title + action bar only — no leader chat spam.
     */
    private void notifyPartyMembersQueueDrop(UUID actorId, Set<UUID> unitMembers, String templateKey, boolean actorWasPartyLeader) {
        Player actor = Bukkit.getPlayer(actorId);
        String actorName = actor != null ? actor.getName() : "A party member";
        Component main = Component.text("Removed from queue", NamedTextColor.GOLD);
        Component sub = actorWasPartyLeader
                ? Component.text("Your party leader left — removed from \"" + templateKey + "\" queue.", NamedTextColor.GRAY)
                : Component.text(actorName + " left — your party was removed from \"" + templateKey + "\" queue.", NamedTextColor.GRAY);
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofMillis(600));
        for (UUID id : unitMembers) {
            if (id.equals(actorId)) {
                continue;
            }
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                continue;
            }
            p.showTitle(Title.title(main, sub, times));
            p.sendActionBar(Component.text("No longer queued for " + templateKey, NamedTextColor.YELLOW));
        }
    }

    private static final class QueueUnit {
        final boolean partyLocked;
        final String partyId;
        final LinkedHashSet<UUID> members;

        QueueUnit(boolean partyLocked, String partyId, LinkedHashSet<UUID> members) {
            this.partyLocked = partyLocked;
            this.partyId = partyId == null ? "" : partyId;
            this.members = members;
        }
    }
}
