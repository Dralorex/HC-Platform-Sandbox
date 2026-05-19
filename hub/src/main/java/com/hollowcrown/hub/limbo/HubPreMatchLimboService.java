package com.hollowcrown.hub.limbo;

import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.api.integration.IronLegacyLimboBridge;
import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Queue / limbo stage before match start; delegates engine work to {@link IronLegacyLimboBridge}. */
public final class HubPreMatchLimboService implements PreMatchLimboService, Listener {

    private final HollowCrownHubPlugin hub;
    private final HubLimboArenaState arenaState;
    private volatile IronLegacyLimboBridge bridge;

    private final Map<String, LimboSession> sessions = new HashMap<>();

    public HubPreMatchLimboService(HollowCrownHubPlugin hub, HubLimboArenaState arenaState) {
        this.hub = hub;
        this.arenaState = arenaState;
    }

    public void attachBridge(IronLegacyLimboBridge bridge) {
        this.bridge = bridge;
    }

    private IronLegacyLimboBridge bridge() {
        return bridge;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoveClamp(PlayerMoveEvent event) {
        IronLegacyLimboBridge b = bridge();
        if (b == null) return;
        Player p = event.getPlayer();
        if (p.isOp()) return;
        String key = limboTemplateKey(p);
        if (key == null) return;
        var zone = sessionZone(p.getWorld(), key, b);
        if (zone == null) return;
        if (zone.contains(event.getTo())) return;
        Location to = event.getTo();
        if (to == null) return;
        event.setTo(clampToZone(to, zone));
    }

    @Override
    public boolean sendToLimbo(Player p, String templateKey) {
        IronLegacyLimboBridge b = bridge();
        if (b == null) return false;
        var info = b.findTemplate(templateKey).orElse(null);
        if (info == null) return false;
        String norm = templateKey.toLowerCase(Locale.ROOT);
        LimboSession s = sessions.get(norm);
        if (s == null) {
            var inst = b.createMatchInstance(info.key()).orElse(null);
            if (inst == null) return false;
            s = new LimboSession(info.key(), inst.worldName(), info.teamSizeMax(), info.maxPlayers());
            sessions.put(norm, s);
        }
        prepareForLimboJoin(p);
        s.players.add(p.getUniqueId());
        Location limboSpawn = b.resolveLimboSpawn(info, s.worldName);
        if (limboSpawn != null) {
            p.teleport(limboSpawn);
        }
        b.markJoinIntentCounted(p);
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        applyLimboLoadout(p);
        p.getInventory().setHeldItemSlot(4);
        p.setGameMode(GameMode.ADVENTURE);
        startQueueTaskIfNeeded(s);
        return true;
    }

    @Override
    public boolean isInLimbo(Player p) {
        UUID id = p.getUniqueId();
        for (LimboSession s : sessions.values()) {
            if (s.players.contains(id)) return true;
        }
        return false;
    }

    @Override
    public String limboTemplateKey(Player p) {
        UUID id = p.getUniqueId();
        for (LimboSession s : sessions.values()) {
            if (s.players.contains(id)) return s.templateKey;
        }
        return null;
    }

    @Override
    public Set<UUID> playersInSameTemplateSession(Player p) {
        if (p == null) {
            return Set.of();
        }
        String tk = limboTemplateKey(p);
        if (tk == null) {
            return Set.of();
        }
        LimboSession s = sessions.get(tk.toLowerCase(Locale.ROOT));
        if (s == null || s.players.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(s.players);
    }

    @Override
    public Location getSessionLimboSpawn(Player p) {
        if (p == null) return null;
        IronLegacyLimboBridge b = bridge();
        if (b == null) return null;
        String templateKey = limboTemplateKey(p);
        if (templateKey == null) return null;
        LimboSession s = sessions.get(templateKey.toLowerCase(Locale.ROOT));
        if (s == null) return null;
        var info = b.findTemplate(templateKey).orElse(null);
        if (info == null) return null;
        return b.resolveLimboSpawn(info, s.worldName);
    }

    private void removeUuidFromLimboSessions(UUID id) {
        List<String> empty = new ArrayList<>();
        for (var entry : sessions.entrySet()) {
            LimboSession s = entry.getValue();
            s.players.remove(id);
            if (s.players.isEmpty()) empty.add(entry.getKey());
        }
        for (String key : empty) cleanupSession(key);
    }

    @Override
    public void leaveLimbo(Player p) {
        IronLegacyLimboBridge b = bridge();
        removeUuidFromLimboSessions(p.getUniqueId());
        if (b != null) {
            b.clearJoinIntent(p);
        }
    }

    @Override
    public void abortAllQueuesAndKickToHub() {
        IronLegacyLimboBridge b = bridge();
        for (LimboSession s : new ArrayList<>(sessions.values())) {
            if (s.countdownTask != null) {
                try {
                    s.countdownTask.cancel();
                } catch (Exception ignored) {
                }
                s.countdownTask = null;
            }
        }
        Set<UUID> everyone = new LinkedHashSet<>();
        for (LimboSession s : sessions.values()) everyone.addAll(s.players);
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                leaveLimbo(p);
                if (b != null) {
                    b.sendToLobbyExact(p, true, true);
                }
            } else {
                removeUuidFromLimboSessions(id);
            }
        }
        for (String key : new ArrayList<>(sessions.keySet())) {
            cleanupSession(key);
        }
    }

    @Override
    public boolean forceBeginTeleportCountdown(String worldOrTemplateKey) {
        if (worldOrTemplateKey == null || worldOrTemplateKey.isBlank()) return false;
        LimboSession s = null;
        for (LimboSession cand : sessions.values()) {
            if (cand.worldName.equalsIgnoreCase(worldOrTemplateKey.trim())) {
                s = cand;
                break;
            }
        }
        if (s == null) s = sessions.get(worldOrTemplateKey.toLowerCase(Locale.ROOT).trim());
        if (s == null) return false;
        List<Player> online = onlinePlayers(s.players);
        if (online.isEmpty()) return false;
        beginTeleportCountdown(s, online, "Match start triggered.");
        return true;
    }

    private int minPlayersRequired(LimboSession s, IronLegacyLimboBridge b) {
        int max = Math.max(1, s.maxPlayers);
        int minPercent = b.minimumFillPercent(s.templateKey);
        return (int) Math.ceil(max * (minPercent / 100.0));
    }

    private void cancelMatchStartForLowFill(LimboSession s, List<Player> players, boolean duringTeleportPhase) {
        s.teleportCountdownSeconds = -1;
        s.queueCountdownSeconds = -1;
        if (duringTeleportPhase) {
            announceAll(players, "Match start canceled: not enough players");
        } else {
            announceAll(players, "Timer canceled: not enough players. A new 5 minute countdown starts once fill is sufficient.");
        }
    }

    @Override
    public boolean isInLimboWorld(World w) {
        if (w == null) return false;
        for (LimboSession s : sessions.values()) {
            if (s.worldName.equalsIgnoreCase(w.getName())) return true;
        }
        return false;
    }

    @Override
    public Set<String> limboWorldNames() {
        Set<String> out = new HashSet<>();
        for (LimboSession s : sessions.values()) out.add(s.worldName);
        return out;
    }

    private void startQueueTaskIfNeeded(LimboSession s) {
        if (s.countdownTask != null) return;
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                IronLegacyLimboBridge b = bridge();
                if (b == null) {
                    cancel();
                    return;
                }
                if (s.players.isEmpty()) {
                    cleanupSession(s.templateKey);
                    cancel();
                    return;
                }

                List<Player> online = onlinePlayers(s.players);
                if (online.isEmpty()) {
                    cleanupSession(s.templateKey);
                    cancel();
                    return;
                }
                s.players.retainAll(online.stream().map(Player::getUniqueId).toList());

                int current = online.size();
                int max = Math.max(1, s.maxPlayers);
                int required = minPlayersRequired(s, b);
                for (Player player : online) {
                    player.sendActionBar(Component.text(current + "/" + max + " Players", NamedTextColor.YELLOW));
                }

                if (current < required) {
                    if (s.teleportCountdownSeconds >= 0) {
                        cancelMatchStartForLowFill(s, online, true);
                        return;
                    }
                    if (s.queueCountdownSeconds >= 0) {
                        cancelMatchStartForLowFill(s, online, false);
                    }
                    return;
                }

                if (s.teleportCountdownSeconds >= 0) {
                    tickTeleportCountdown(s, online);
                    return;
                }

                if (current >= max) {
                    beginTeleportCountdown(s, online, "Lobby full. Preparing teleport...");
                    return;
                }

                if (current >= required) {
                    if (s.queueCountdownSeconds < 0) {
                        s.queueCountdownSeconds = 300;
                        announceAll(online, "Beginning in: 5 Minutes");
                    } else {
                        announceQueueMilestones(s, online);
                    }
                    s.queueCountdownSeconds--;
                    if (s.queueCountdownSeconds < 0) {
                        beginTeleportCountdown(s, online, "Minimum fill reached. Preparing teleport...");
                    }
                }
            }
        };
        s.countdownTask = runnable.runTaskTimer(hub, 20L, 20L);
    }

    private void announceQueueMilestones(LimboSession s, List<Player> players) {
        int t = s.queueCountdownSeconds;
        if (t == 240) announceAll(players, "Beginning in: 4 Minutes");
        if (t == 180) announceAll(players, "Beginning in: 3 Minutes");
        if (t == 120) announceAll(players, "Beginning in: 2 Minutes");
        if (t == 60) announceAll(players, "Beginning in: 1 Minute");
        if (t == 30) announceAll(players, "Beginning in: 30 Seconds");
        if (t <= 10 && t >= 1) announceAll(players, "Beginning in: " + t + (t == 1 ? " Second" : " Seconds"));
    }

    private void beginTeleportCountdown(LimboSession s, List<Player> players, String message) {
        s.queueCountdownSeconds = -1;
        s.teleportCountdownSeconds = 10;
        announceAll(players, message);
        for (Player p : players) {
            p.showTitle(Title.title(
                    Component.text("TELEPORTING IN", NamedTextColor.GREEN),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(50), Duration.ofSeconds(1), Duration.ofMillis(50))
            ));
        }
    }

    private void tickTeleportCountdown(LimboSession s, List<Player> players) {
        int t = s.teleportCountdownSeconds;
        if (t <= 0) {
            launchSession(s);
            return;
        }
        NamedTextColor color = colorForTeleportCountdown(t);
        for (Player p : players) {
            p.showTitle(Title.title(
                    Component.text(Integer.toString(t), color),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(50), Duration.ofSeconds(1), Duration.ofMillis(50))
            ));
        }
        s.teleportCountdownSeconds--;
    }

    private NamedTextColor colorForTeleportCountdown(int second) {
        if (second >= 8) return NamedTextColor.GREEN;
        if (second >= 5) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private void launchSession(LimboSession s) {
        IronLegacyLimboBridge b = bridge();
        if (b == null) {
            cleanupSession(s.templateKey);
            return;
        }
        Set<UUID> onlinePlayers = new LinkedHashSet<>();
        for (UUID id : s.players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) onlinePlayers.add(id);
        }
        if (onlinePlayers.isEmpty()) {
            cleanupSession(s.templateKey);
            return;
        }

        clearLimboZoneBlocks(s, b);
        b.assignTeamsForLimboLaunch(s.templateKey, s.teamSizeMax, onlinePlayers);
        World instanceWorld = Bukkit.getWorld(s.worldName);
        boolean ok = b.startGameOnPreparedInstance(instanceWorld, onlinePlayers, true);
        if (!ok) {
            for (UUID id : onlinePlayers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.sendMessage(ChatColor.RED + "Failed to start match from limbo: " + b.lastGameStartError());
            }
            evacuatePlayersToHub(onlinePlayers, s.worldName, b);
        }
        cleanupSession(s.templateKey);
    }

    private void cleanupSession(String key) {
        IronLegacyLimboBridge b = bridge();
        LimboSession s = sessions.remove(key.toLowerCase(Locale.ROOT));
        if (s == null) return;
        if (s.countdownTask != null) {
            try {
                s.countdownTask.cancel();
            } catch (Exception ignored) {
            }
        }
        World w = Bukkit.getWorld(s.worldName);
        if (b != null && (w == null || w.getPlayers().isEmpty())) {
            try {
                b.destroyMatchInstanceIfEmpty(s.worldName);
            } catch (Throwable ignored) {
            }
        }
    }

    private void clearLimboZoneBlocks(LimboSession s, IronLegacyLimboBridge b) {
        World w = Bukkit.getWorld(s.worldName);
        if (w == null) return;
        var zone = sessionZone(w, s.templateKey, b);
        if (zone == null) return;
        Location p1 = zone.pos1();
        Location p2 = zone.pos2();
        int minX = Math.min((int) Math.floor(p1.getX()), (int) Math.floor(p2.getX()));
        int maxX = Math.max((int) Math.floor(p1.getX()), (int) Math.floor(p2.getX()));
        int minY = Math.min((int) Math.floor(p1.getY()), (int) Math.floor(p2.getY()));
        int maxY = Math.max((int) Math.floor(p1.getY()), (int) Math.floor(p2.getY()));
        int minZ = Math.min((int) Math.floor(p1.getZ()), (int) Math.floor(p2.getZ()));
        int maxZ = Math.max((int) Math.floor(p1.getZ()), (int) Math.floor(p2.getZ()));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private void evacuatePlayersToHub(Set<UUID> ids, String fromWorldName, IronLegacyLimboBridge b) {
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            if (p.getWorld() != null && p.getWorld().getName().equalsIgnoreCase(fromWorldName)) {
                if (b.velocityEnabled() && !b.hubTargetIsThisServer()) {
                    World safe = Bukkit.getWorlds().stream()
                            .filter(world -> !world.getName().equalsIgnoreCase(fromWorldName))
                            .findFirst().orElse(null);
                    if (safe != null) {
                        p.teleport(safe.getSpawnLocation());
                    }
                }
            }
            b.returnToHubOrLobby(p, true, true);
        }
    }

    private List<Player> onlinePlayers(Set<UUID> ids) {
        List<Player> out = new ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    private void announceAll(List<Player> players, String msg) {
        for (Player p : players) p.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
    }

    private Location clampToZone(Location loc, LimboZone zone) {
        Location p1 = zone.pos1();
        Location p2 = zone.pos2();
        double minX = Math.min(p1.getX(), p2.getX());
        double maxX = Math.max(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY());
        double maxY = Math.max(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxZ = Math.max(p1.getZ(), p2.getZ());
        double x = Math.max(minX + 0.2, Math.min(maxX - 0.2, loc.getX()));
        double y = Math.max(minY + 0.1, Math.min(maxY - 0.1, loc.getY()));
        double z = Math.max(minZ + 0.2, Math.min(maxZ - 0.2, loc.getZ()));
        return new Location(loc.getWorld(), x, y, z, loc.getYaw(), loc.getPitch());
    }

    private LimboZone sessionZone(World sessionWorld, String templateKey, IronLegacyLimboBridge b) {
        var corners = b.limboZoneCorners(templateKey).orElse(null);
        if (corners == null || sessionWorld == null) return null;
        Location s1 = new Location(sessionWorld, corners.x1(), corners.y1(), corners.z1());
        Location s2 = new Location(sessionWorld, corners.x2(), corners.y2(), corners.z2());
        return new LimboZone(s1, s2);
    }

    private record LimboZone(Location pos1, Location pos2) {
        boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null || pos1 == null || pos2 == null) return false;
            if (!loc.getWorld().equals(pos1.getWorld()) || !loc.getWorld().equals(pos2.getWorld())) return false;
            double minX = Math.min(pos1.getX(), pos2.getX());
            double maxX = Math.max(pos1.getX(), pos2.getX());
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY());
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxZ = Math.max(pos1.getZ(), pos2.getZ());
            return loc.getX() >= minX && loc.getX() <= maxX
                    && loc.getY() >= minY && loc.getY() <= maxY
                    && loc.getZ() >= minZ && loc.getZ() <= maxZ;
        }
    }

    @Override
    public void prepareForLimboJoin(Player p) {
        arenaState.prepareForLimboJoin(p);
    }

    @Override
    public void applyLimboLoadout(Player p) {
        int held = Math.min(8, Math.max(0, p.getInventory().getHeldItemSlot()));
        RegisteredServiceProvider<HubWorldApi> reg = Bukkit.getServicesManager().getRegistration(HubWorldApi.class);
        if (reg != null) {
            reg.getProvider().applyLimboUtilityLoadout(p, held);
        }
    }

    @Override
    public boolean allowHostileMobSpawns() {
        IronLegacyLimboBridge b = bridge();
        return b != null && b.limboHostileMobSpawns();
    }

    @Override
    public boolean allowPassiveMobSpawns() {
        IronLegacyLimboBridge b = bridge();
        return b != null && b.limboPassiveMobSpawns();
    }

    @Override
    public void setAllowHostileMobSpawns(boolean value) {
        IronLegacyLimboBridge b = bridge();
        if (b != null) {
            b.setLimboHostileMobSpawns(value);
        }
    }

    @Override
    public void setAllowPassiveMobSpawns(boolean value) {
        IronLegacyLimboBridge b = bridge();
        if (b != null) {
            b.setLimboPassiveMobSpawns(value);
        }
    }

    public static final class LimboSession {
        public final String templateKey;
        public final String worldName;
        public final int teamSizeMax;
        public final int maxPlayers;
        public final Set<UUID> players = new LinkedHashSet<>();
        public BukkitTask countdownTask;
        public int queueCountdownSeconds = -1;
        public int teleportCountdownSeconds = -1;

        public LimboSession(String templateKey, String worldName, int teamSizeMax, int maxPlayers) {
            this.templateKey = templateKey;
            this.worldName = worldName;
            this.teamSizeMax = teamSizeMax;
            this.maxPlayers = maxPlayers;
        }
    }
}
