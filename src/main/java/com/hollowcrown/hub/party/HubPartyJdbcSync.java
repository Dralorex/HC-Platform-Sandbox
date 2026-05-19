package com.hollowcrown.hub.party;

import com.hollowcrown.hub.HollowCrownHubPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * MySQL mirror for {@link HubPartyService} hot data (parties + pending invites).
 */
final class HubPartyJdbcSync {

    private HubPartyJdbcSync() {
    }

    /**
     * @return true if any party row existed in JDBC (memory was replaced)
     */
    static boolean loadIfPresent(HollowCrownHubPlugin plugin, DataSource ds,
            Map<String, HubParty> parties,
            Map<UUID, HubParty> byMember,
            Map<UUID, Set<String>> invites,
            Map<UUID, Map<String, Long>> inviteCreatedAt) throws SQLException {
        try (Connection c = ds.getConnection();
             var ps = c.prepareStatement("SELECT COUNT(*) FROM hc_party");
             var rs = ps.executeQuery()) {
            if (!rs.next() || rs.getInt(1) <= 0) {
                return false;
            }
        }

        Map<String, HubParty> nextParties = new HashMap<>();
        Map<UUID, HubParty> nextByMember = new HashMap<>();
        Map<UUID, Set<String>> nextInvites = new HashMap<>();
        Map<UUID, Map<String, Long>> nextInviteCreated = new HashMap<>();

        try (Connection c = ds.getConnection();
             var ps = c.prepareStatement("SELECT party_id, display_name, leader_uuid FROM hc_party");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                String pid = rs.getString(1);
                String display = rs.getString(2);
                UUID leader = UUID.fromString(rs.getString(3));
                HubParty p = new HubParty(pid, display, leader);
                nextParties.put(pid, p);
            }
        }

        try (Connection c = ds.getConnection();
             var ps = c.prepareStatement(
                     "SELECT party_id, ord, member_uuid FROM hc_party_member ORDER BY party_id, ord");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                String pid = rs.getString(1);
                UUID mem = UUID.fromString(rs.getString(3));
                HubParty p = nextParties.get(pid);
                if (p == null) {
                    continue;
                }
                if (!p.members.contains(mem)) {
                    p.members.add(mem);
                    p.memberJoinOrder.add(mem);
                    nextByMember.put(mem, p);
                }
            }
        }

        for (HubParty p : nextParties.values()) {
            if (!p.members.contains(p.leader)) {
                p.members.add(p.leader);
                p.memberJoinOrder.add(0, p.leader);
                nextByMember.put(p.leader, p);
            }
        }

        try (Connection c = ds.getConnection();
             var ps = c.prepareStatement("SELECT invitee_uuid, party_id, created_ms FROM hc_party_invite");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID invitee = UUID.fromString(rs.getString(1));
                String pid = rs.getString(2);
                long created = rs.getLong(3);
                nextInvites.computeIfAbsent(invitee, k -> new LinkedHashSet<>()).add(pid);
                nextInviteCreated.computeIfAbsent(invitee, k -> new HashMap<>()).put(pid, created);
            }
        }

        parties.clear();
        byMember.clear();
        invites.clear();
        inviteCreatedAt.clear();
        parties.putAll(nextParties);
        byMember.putAll(nextByMember);
        invites.putAll(nextInvites);
        inviteCreatedAt.putAll(nextInviteCreated);
        plugin.getLogger().info("Hub parties: loaded " + parties.size() + " row(s) from MySQL.");
        return true;
    }

    static void save(HollowCrownHubPlugin plugin, DataSource ds,
            Map<String, HubParty> parties,
            Map<UUID, Set<String>> invites,
            Map<UUID, Map<String, Long>> inviteCreatedAt) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (var st = c.createStatement()) {
                    st.executeUpdate("DELETE FROM hc_party_invite");
                    st.executeUpdate("DELETE FROM hc_party_member");
                    st.executeUpdate("DELETE FROM hc_party");
                }
                try (var insParty = c.prepareStatement(
                        "INSERT INTO hc_party(party_id, display_name, leader_uuid) VALUES(?,?,?)");
                     var insMem = c.prepareStatement(
                             "INSERT INTO hc_party_member(party_id, ord, member_uuid) VALUES(?,?,?)");
                     var insInv = c.prepareStatement(
                             "INSERT INTO hc_party_invite(invitee_uuid, party_id, created_ms) VALUES(?,?,?)")) {
                    for (HubParty p : parties.values()) {
                        insParty.setString(1, p.id);
                        insParty.setString(2, p.displayName);
                        insParty.setString(3, p.leader.toString());
                        insParty.addBatch();
                        int ord = 0;
                        for (UUID m : p.memberJoinOrder) {
                            if (!p.members.contains(m)) {
                                continue;
                            }
                            insMem.setString(1, p.id);
                            insMem.setInt(2, ord++);
                            insMem.setString(3, m.toString());
                            insMem.addBatch();
                        }
                    }
                    insParty.executeBatch();
                    insMem.executeBatch();

                    for (Map.Entry<UUID, Set<String>> e : invites.entrySet()) {
                        UUID invitee = e.getKey();
                        Map<String, Long> created = inviteCreatedAt.getOrDefault(invitee, Map.of());
                        for (String pid : e.getValue()) {
                            insInv.setString(1, invitee.toString());
                            insInv.setString(2, pid);
                            insInv.setLong(3, created.getOrDefault(pid, 0L));
                            insInv.addBatch();
                        }
                    }
                    insInv.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                }
                plugin.getLogger().log(Level.SEVERE, "Hub parties: MySQL save failed (YAML still on disk).", ex);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Hub parties: MySQL connection failed during save.", ex);
        }
    }
}
