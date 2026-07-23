package com.hollowcrown.hub.platform;

import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Routes players (and parties) to numbered hub spawns without exceeding per-instance capacity.
 */
public final class HubHubRouting {

    private HubHubRouting() {
    }

    public static int instanceCapacity(HollowCrownHubPlugin plugin) {
        return Math.max(1, plugin.getConfig().getInt("hub.instanceCapacity", 20));
    }

    /**
     * Sends one player to hub, pulling their whole online party when they are the leader.
     * Keeps the party on the same hub when possible; overflows extra members to the next hub.
     */
    public static boolean sendToHubWithPartyRouting(HollowCrownHubPlugin plugin, Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        PartyService parties = plugin.partyService();
        Optional<PartyView> partyOpt = parties == null
                ? Optional.empty()
                : parties.partyForMember(player.getUniqueId());
        if (partyOpt.isPresent() && partyOpt.get().isLeader(player.getUniqueId())) {
            List<Player> group = new ArrayList<>();
            for (UUID id : partyOpt.get().membersJoinOrder()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline() && p.hasPermission("hollowcrownhub.use")) {
                    group.add(p);
                }
            }
            if (group.size() > 1) {
                return sendGroupToHub(plugin, group);
            }
        }
        return plugin.sendPlayerToHubSpawn(player, pickHubForSolo(plugin));
    }

    /** Assigns a group to hub instance(s) respecting capacity; keeps order stable. */
    public static boolean sendGroupToHub(HollowCrownHubPlugin plugin, List<Player> players) {
        if (players == null || players.isEmpty()) {
            return false;
        }
        List<Integer> hubs = HubHubSpawns.configuredNumbers(plugin);
        if (hubs.isEmpty()) {
            return plugin.sendPlayerToHubSpawn(players.get(0), 1);
        }
        int cap = instanceCapacity(plugin);
        Map<Integer, Integer> occupancy = snapshotOccupancy(plugin, hubs);
        List<Player> remaining = new ArrayList<>(players);
        boolean any = false;
        while (!remaining.isEmpty()) {
            int hub = pickHubForCount(occupancy, hubs, remaining.size(), cap);
            if (hub < 0) {
                hub = hubs.get(hubs.size() - 1);
            }
            int slots = Math.max(0, cap - occupancy.getOrDefault(hub, 0));
            if (slots <= 0) {
                slots = 1;
            }
            int take = Math.min(slots, remaining.size());
            List<Player> batch = remaining.subList(0, take);
            for (Player p : new ArrayList<>(batch)) {
                if (plugin.sendPlayerToHubSpawn(p, hub)) {
                    any = true;
                }
            }
            occupancy.put(hub, occupancy.getOrDefault(hub, 0) + take);
            remaining.subList(0, take).clear();
        }
        return any;
    }

    private static int pickHubForSolo(HollowCrownHubPlugin plugin) {
        List<Integer> hubs = HubHubSpawns.configuredNumbers(plugin);
        if (hubs.isEmpty()) {
            return 1;
        }
        Map<Integer, Integer> occupancy = snapshotOccupancy(plugin, hubs);
        int cap = instanceCapacity(plugin);
        int best = hubs.get(0);
        int bestLoad = Integer.MAX_VALUE;
        for (int hub : hubs) {
            int load = occupancy.getOrDefault(hub, 0);
            if (load < cap && load < bestLoad) {
                bestLoad = load;
                best = hub;
            }
        }
        return best;
    }

    private static int pickHubForCount(Map<Integer, Integer> occupancy, List<Integer> hubs, int need, int cap) {
        for (int hub : hubs) {
            if (occupancy.getOrDefault(hub, 0) + need <= cap) {
                return hub;
            }
        }
        for (int hub : hubs) {
            if (occupancy.getOrDefault(hub, 0) < cap) {
                return hub;
            }
        }
        return hubs.isEmpty() ? -1 : hubs.get(hubs.size() - 1);
    }

    /** Count online players near each configured hub spawn (same world, within 48 blocks). */
    private static Map<Integer, Integer> snapshotOccupancy(HollowCrownHubPlugin plugin, List<Integer> hubs) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (int hub : hubs) {
            out.put(hub, countNearHubSpawn(plugin, hub));
        }
        return out;
    }

    private static int countNearHubSpawn(HollowCrownHubPlugin plugin, int hubNumber) {
        Optional<HubSpawn> spawnOpt = HubHubSpawns.resolve(plugin, hubNumber);
        if (spawnOpt.isEmpty()) {
            return 0;
        }
        HubSpawn s = spawnOpt.get();
        World world = Bukkit.getWorld(s.worldUuid());
        if (world == null) {
            return 0;
        }
        Location center = new Location(world, s.x(), s.y(), s.z());
        int count = 0;
        double radiusSq = 48.0 * 48.0;
        for (Player p : world.getPlayers()) {
            if (!p.isOnline()) {
                continue;
            }
            if (p.getLocation().distanceSquared(center) <= radiusSq) {
                count++;
            }
        }
        return count;
    }
}
