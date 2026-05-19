package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.Yaml;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.HostAndPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared-state abstraction (file backend for now; Redis-ready seam). */
public class SharedStateManager {
    private static final String FILE = "shared-state.yml";

    private final HungerGamesPlugin plugin;
    private FileConfiguration cfg;
    private JedisPooled redis;

    public SharedStateManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cfg = Yaml.loadOrCreate(plugin, FILE);
        if (!cfg.contains("schemaVersion")) cfg.set("schemaVersion", 1);
        if (!cfg.isConfigurationSection("cluster")) cfg.createSection("cluster");
        save();
        reconnectRedis();
    }

    public void save() {
        Yaml.save(plugin, FILE, cfg);
    }

    public void updateShardLoad(String serverId, int shardId, int load, int cap) {
        if (isRedis()) {
            redisSafe(() -> {
                String key = "cluster:servers:" + serverId + ":shards:" + shardId;
                redis.hset(key, Map.of(
                        "load", Integer.toString(Math.max(0, load)),
                        "cap", Integer.toString(Math.max(1, cap)),
                        "updatedAt", Long.toString(System.currentTimeMillis())
                ));
            });
            return;
        }
        String base = "cluster.servers." + serverId + ".shards." + shardId;
        cfg.set(base + ".load", Math.max(0, load));
        cfg.set(base + ".cap", Math.max(1, cap));
        cfg.set(base + ".updatedAt", System.currentTimeMillis());
        save();
    }

    public String backend() {
        return plugin.files().gameflow().getString("sharedState.backend", "FILE");
    }

    public void saveParty(String id, String name, String leader, List<String> members, List<String> joinOrder) {
        if (isRedis()) {
            redisSafe(() -> {
                String key = "parties:" + id;
                redis.hset(key, Map.of(
                        "name", name,
                        "leader", leader,
                        "members", String.join(",", members),
                        "joinOrder", String.join(",", joinOrder),
                        "updatedAt", Long.toString(System.currentTimeMillis())
                ));
            });
            return;
        }
        String base = "cluster.parties." + id;
        cfg.set(base + ".name", name);
        cfg.set(base + ".leader", leader);
        cfg.set(base + ".members", members);
        cfg.set(base + ".joinOrder", joinOrder);
        cfg.set(base + ".updatedAt", System.currentTimeMillis());
        save();
    }

    public void deleteParty(String id) {
        if (isRedis()) {
            redisSafe(() -> redis.del("parties:" + id));
            return;
        }
        cfg.set("cluster.parties." + id, null);
        save();
    }

    public void addPartyInvite(String playerId, String partyId) {
        if (isRedis()) {
            redisSafe(() -> redis.sadd("partyInvites:" + playerId, partyId));
            return;
        }
        String base = "cluster.partyInvites." + playerId;
        List<String> invites = new ArrayList<>(cfg.getStringList(base));
        if (!invites.contains(partyId)) invites.add(partyId);
        cfg.set(base, invites);
        save();
    }

    public void removePartyInvite(String playerId, String partyId) {
        if (isRedis()) {
            redisSafe(() -> redis.srem("partyInvites:" + playerId, partyId));
            return;
        }
        String base = "cluster.partyInvites." + playerId;
        List<String> invites = new ArrayList<>(cfg.getStringList(base));
        invites.remove(partyId);
        cfg.set(base, invites);
        save();
    }

    public List<String> getPartyInvites(String playerId) {
        if (isRedis()) {
            var set = redisSafeResult(() -> redis.smembers("partyInvites:" + playerId));
            if (set == null || set.isEmpty()) return List.of();
            return set.stream().sorted().collect(Collectors.toList());
        }
        List<String> invites = cfg.getStringList("cluster.partyInvites." + playerId);
        return invites == null ? Collections.emptyList() : invites;
    }

    private boolean isRedis() {
        return "REDIS".equalsIgnoreCase(backend());
    }

    private void reconnectRedis() {
        if (!isRedis()) {
            redis = null;
            return;
        }
        try {
            String host = plugin.files().gameflow().getString("sharedState.redis.host", "127.0.0.1");
            int port = plugin.files().gameflow().getInt("sharedState.redis.port", 6379);
            redis = new JedisPooled(new HostAndPort(host, port));
            redis.ping();
        } catch (Exception ex) {
            plugin.getLogger().warning("Redis unavailable, falling back to FILE backend semantics: " + ex.getMessage());
            redis = null;
        }
    }

    private void redisSafe(Runnable runnable) {
        try {
            if (redis != null) runnable.run();
        } catch (Exception ex) {
            plugin.getLogger().warning("Redis write failed: " + ex.getMessage());
        }
    }

    private <T> T redisSafeResult(SupplierWithException<T> action) {
        try {
            if (redis != null) return action.get();
        } catch (Exception ex) {
            plugin.getLogger().warning("Redis read failed: " + ex.getMessage());
        }
        return null;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}

