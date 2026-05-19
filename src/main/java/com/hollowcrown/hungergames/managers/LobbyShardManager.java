package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Fixed-cap lobby shard assignment (phase 8 core). */
public class LobbyShardManager implements Listener {
    private final HungerGamesPlugin plugin;
    private final Map<UUID, Integer> assignedShard = new HashMap<>();
    private final Map<Integer, Integer> shardLoads = new HashMap<>();
    private int activeShardCount;

    public LobbyShardManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        this.activeShardCount = baseShardCount();
        ensureShardBuckets();
    }

    public int assignPlayer(Player p) {
        ensureShardBuckets();
        UUID id = p.getUniqueId();
        Integer existing = assignedShard.get(id);
        if (existing != null) return existing;

        int cap = shardCap();
        int chosen = -1;
        int bestLoad = Integer.MAX_VALUE;
        for (int shard = 0; shard < shardCount(); shard++) {
            int load = shardLoads.getOrDefault(shard, 0);
            if (load >= cap) continue;
            if (load < bestLoad) {
                bestLoad = load;
                chosen = shard;
            }
        }
        if (chosen < 0 && isAutoscaleEnabled() && activeShardCount < maxShardCount()) {
            chosen = activeShardCount;
            activeShardCount++;
            shardLoads.putIfAbsent(chosen, 0);
        }
        if (chosen < 0) return -1;

        assignedShard.put(id, chosen);
        int load = shardLoads.getOrDefault(chosen, 0) + 1;
        shardLoads.put(chosen, load);
        plugin.sharedState().updateShardLoad(plugin.serverRole().serverId(), chosen, load, shardCap());
        return chosen;
    }

    public void releasePlayer(Player p) {
        UUID id = p.getUniqueId();
        Integer shard = assignedShard.remove(id);
        if (shard == null) return;
        int load = Math.max(0, shardLoads.getOrDefault(shard, 0) - 1);
        shardLoads.put(shard, load);
        plugin.sharedState().updateShardLoad(plugin.serverRole().serverId(), shard, load, shardCap());
    }

    public int getAssignedShard(Player p) {
        return assignedShard.getOrDefault(p.getUniqueId(), -1);
    }

    public int getShardLoad(int shard) {
        return shardLoads.getOrDefault(shard, 0);
    }

    public int shardCount() {
        return Math.max(1, activeShardCount);
    }

    public int shardCap() {
        return Math.max(1, plugin.files().gameflow().getInt("caps.lobbyShardPlayers", 20));
    }

    private void ensureShardBuckets() {
        for (int i = 0; i < shardCount(); i++) {
            shardLoads.putIfAbsent(i, 0);
        }
    }

    private boolean isAutoscaleEnabled() {
        return plugin.files().gameflow().getBoolean("lobbies.autoscaleEnabled", true);
    }

    private int baseShardCount() {
        return Math.max(1, plugin.files().gameflow().getInt("lobbies.defaultShards", 6));
    }

    private int maxShardCount() {
        int configured = Math.max(baseShardCount(), plugin.files().gameflow().getInt("lobbies.maxShards", 24));
        return Math.max(baseShardCount(), configured);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        releasePlayer(e.getPlayer());
    }
}

