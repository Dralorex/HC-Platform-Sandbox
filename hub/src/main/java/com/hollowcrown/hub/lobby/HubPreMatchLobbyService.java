package com.hollowcrown.hub.lobby;

import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.api.integration.IronLegacyLobbyBridge;
import com.hollowcrown.api.integration.PreMatchLobbyService;
import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.HubPlayerFacingErrors;
import com.hollowcrown.hub.team.HubLobbyTeamFormation;
import com.hollowcrown.hub.lobby.HubLobbyService;
import com.hollowcrown.hub.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
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

/** Queue / lobby stage before match start; delegates engine work to {@link IronLegacyLobbyBridge}. */
public final class HubPreMatchLobbyService implements PreMatchLobbyService, Listener {

    private final HollowCrownHubPlugin hub;
    private final HubLobbyArenaState arenaState;
    private volatile IronLegacyLobbyBridge bridge;

    private final Map<String, LobbySession> sessions = new HashMap<>();

    public HubPreMatchLobbyService(HollowCrownHubPlugin hub, HubLobbyArenaState arenaState) {
        this.hub = hub;
        this.arenaState = arenaState;
    }

    public void attachBridge(IronLegacyLobbyBridge bridge) {
        this.bridge = bridge;
    }

    private IronLegacyLobbyBridge bridge() {
        return bridge;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoveClamp(PlayerMoveEvent event) {
        IronLegacyLobbyBridge b = bridge();
        if (b == null) return;
        Player p = event.getPlayer();
        if (p.isOp()) return;
        String key = lobbyTemplateKey(p);
        if (key == null) return;
        var zone = sessionZone(p.getWorld(), key, b);
        if (zone == null) return;
        if (zone.contains(event.getTo())) return;
        Location to = event.getTo();
        if (to == null) return;
        event.setTo(clampToZone(to, zone));
    }

    @Override
    public boolean sendToLobby(Player p, String templateKey) {
        IronLegacyLobbyBridge b = bridge();
        if (b == null) return false;
        var info = b.findTemplate(templateKey).orElse(null);
        if (info == null) return false;
        String norm = templateKey.toLowerCase(Locale.ROOT);
        LobbySession s = sessions.get(norm);
        if (s == null) {
            var inst = b.createMatchInstance(info.key()).orElse(null);
            if (inst == null) return false;
            s = new LobbySession(info.key(), inst.worldName(), info.teamSizeMax(), info.maxPlayers());
            sessions.put(norm, s);
        }
        prepareForLobbyJoin(p);
        HubLobbyTeamFormation.resetTeamForLobbyEntry(hub, p);
        s.players.add(p.getUniqueId());
        Location lobbySpawn = b.resolveLobbySpawn(info, s.worldName);
        if (lobbySpawn != null) {
            p.teleport(lobbySpawn);
        }
        b.markJoinIntentCounted(p);
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        applyLobbyLoadout(p);
        p.getInventory().setHeldItemSlot(4);
        p.setGameMode(GameMode.ADVENTURE);
        startQueueTaskIfNeeded(s);
        return true;
    }

    @Override
    public boolean registerHubLobbyJoin(Player p, String templateKey, String lobbyWorldName) {
        IronLegacyLobbyBridge b = bridge();
        if (b == null || p == null || templateKey == null || templateKey.isBlank()
                || lobbyWorldName == null || lobbyWorldName.isBlank()) {
            return false;
        }
        String resolvedKey = templateKey;
        var info = b.findTemplate(resolvedKey).orElse(null);
        if (info == null) {
            String source = hub.templates().get(templateKey)
                    .map(e -> e.sourceWorldName())
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .orElse(null);
            if (source != null) {
                String canon = hub.lobbyLayout().worldsYaml().resolveTemplateKeyForWorld(source).orElse(null);
                if (canon != null && !canon.isBlank()) {
                    resolvedKey = canon;
                    info = b.findTemplate(resolvedKey).orElse(null);
                }
            }
        }
        if (info == null) {
            return false;
        }
        World lobbyWorld = Bukkit.getWorld(lobbyWorldName);
        if (lobbyWorld == null) {
            return false;
        }

        String sessionKey = lobbyWorldName.toLowerCase(Locale.ROOT);
        removeUuidFromLobbySessions(p.getUniqueId());

        LobbySession s = sessions.get(sessionKey);
        if (s == null) {
            s = new LobbySession(info.key(), lobbyWorldName, info.teamSizeMax(), info.maxPlayers());
            sessions.put(sessionKey, s);
        }

        prepareForLobbyJoin(p);
        HubLobbyTeamFormation.resetTeamForLobbyEntry(hub, p);
        s.players.add(p.getUniqueId());
        b.markJoinIntentCounted(p);
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        applyLobbyLoadout(p);
        p.getInventory().setHeldItemSlot(4);
        p.setGameMode(GameMode.ADVENTURE);
        HubLobbyTeamFormation.bindPartyTeamOnLobbyJoin(hub, p, s.teamSizeMax);
        startQueueTaskIfNeeded(s);
        announceLobbyTeamPrompt(p);
        if(TeamManager.getInstance(lobbyWorld) != null)
            TeamManager.getInstance(lobbyWorld).sendAllFakeTeamPackets();
        return true;
    }

    /** Title + chat on lobby-lobby entry — parties auto-team; solo players use Match Team. */
    private void announceLobbyTeamPrompt(Player p) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || !isInLobby(p)) return;
                TeamManager.Team team = TeamManager.getOrCreateWorldInstance(p.getWorld()).findPlayerTeam(p);
                if (team != null) {
                    String name = team.getName();
                    p.showTitle(Title.title(
                            Component.text(name, NamedTextColor.GOLD),
                            Component.text("Your party is on this match team", NamedTextColor.GREEN),
                            Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofMillis(500))));
                    p.sendMessage(Component.text(
                            "You are on " + name + ". Invite others from Match Team (slot 3) if you have room.",
                            NamedTextColor.GOLD));
                    TeamManager.getOrCreateWorldInstance(p.getWorld()).sendAllFakeTeamPackets();
                    return;
                }
                //boolean legacyColors = HubTeamAutoColors.isLegacyColorMode(hub, p);
                p.showTitle(Title.title(
                        Component.text("Match Team", NamedTextColor.GOLD),
                        Component.text("Create or join a team in Match Team (slot 3)",
                                NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofMillis(500))));
                p.sendMessage(Component.text(
                        "Create Team 1, Team 2, … in Match Team (slot 3) and invite teammates.",
                        NamedTextColor.GOLD));
            }
        }.runTaskLater(hub, 20L);
    }

    @Override
    public int teamSizeMaxFor(Player player) {
        LobbySession s = sessionForPlayer(player);
        return s == null ? -1 : Math.max(1, s.teamSizeMax);
    }

    private PartyView resolveParty(Player p) {
        RegisteredServiceProvider<PartyService> reg =
                Bukkit.getServicesManager().getRegistration(PartyService.class);
        if (reg == null) return null;
        return reg.getProvider().partyForMember(p.getUniqueId()).orElse(null);
    }

    @Override
    public void endSessionForWorld(String lobbyWorldName) {
        if (lobbyWorldName == null || lobbyWorldName.isBlank()) {
            return;
        }
        cleanupSession(lobbyWorldName.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean isInLobby(Player p) {
        UUID id = p.getUniqueId();
        for (LobbySession s : sessions.values()) {
            if (s.players.contains(id)) return true;
        }
        return false;
    }

    @Override
    public String lobbyTemplateKey(Player p) {
        LobbySession s = sessionForPlayer(p);
        return s == null ? null : s.templateKey;
    }

    @Override
    public Set<UUID> playersInSameTemplateSession(Player p) {
        if (p == null) {
            return Set.of();
        }
        LobbySession s = sessionForPlayer(p);
        if (s == null || s.players.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(s.players);
    }

    @Override
    public Location getSessionLobbySpawn(Player p) {
        if (p == null) return null;
        IronLegacyLobbyBridge b = bridge();
        if (b == null) return null;
        LobbySession s = sessionForPlayer(p);
        if (s == null) return null;
        var info = b.findTemplate(s.templateKey).orElse(null);
        if (info == null) return null;
        return b.resolveLobbySpawn(info, s.worldName);
    }

    private LobbySession sessionForPlayer(Player p) {
        if (p == null) {
            return null;
        }
        UUID id = p.getUniqueId();
        for (LobbySession s : sessions.values()) {
            if (s.players.contains(id)) {
                return s;
            }
        }
        return null;
    }

    private void removeUuidFromLobbySessions(UUID id) {
        List<String> empty = new ArrayList<>();
        for (var entry : sessions.entrySet()) {
            LobbySession s = entry.getValue();
            s.players.remove(id);
            if (s.players.isEmpty()) empty.add(entry.getKey());
        }
        for (String key : empty) cleanupSession(key);
    }

    @Override
    public void leaveLobby(Player p) {
        IronLegacyLobbyBridge b = bridge();
        removeUuidFromLobbySessions(p.getUniqueId());
        if (b != null) {
            b.clearJoinIntent(p);
        }
    }

    @Override
    public void abortAllQueuesAndKickToHub() {
        IronLegacyLobbyBridge b = bridge();
        for (LobbySession s : new ArrayList<>(sessions.values())) {
            if (s.countdownTask != null) {
                try {
                    s.countdownTask.cancel();
                } catch (Exception ignored) {
                }
                s.countdownTask = null;
            }
        }
        Set<UUID> everyone = new LinkedHashSet<>();
        for (LobbySession s : sessions.values()) everyone.addAll(s.players);
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                hub.sendPlayerToHubSpawn(p);
            } else {
                removeUuidFromLobbySessions(id);
            }
        }
        for (String key : new ArrayList<>(sessions.keySet())) {
            cleanupSession(key);
        }
    }

    @Override
    public boolean forceBeginTeleportCountdown(String worldOrTemplateKey) {
        if (worldOrTemplateKey == null || worldOrTemplateKey.isBlank()) return false;
        LobbySession s = null;
        for (LobbySession cand : sessions.values()) {
            if (cand.worldName.equalsIgnoreCase(worldOrTemplateKey.trim())) {
                s = cand;
                break;
            }
        }
        if (s == null) s = sessions.get(worldOrTemplateKey.toLowerCase(Locale.ROOT).trim());
        if (s == null) return false;
        List<Player> online = onlinePlayers(s.players);
        if (online.isEmpty()) return false;
        s.forceStart = true;
        beginTeleportCountdown(s, online, "Match start triggered.");
        return true;
    }

    private int minPlayersRequired(LobbySession s, IronLegacyLobbyBridge b) {
        int max = Math.max(1, s.maxPlayers);
        int minPercent = b.minimumFillPercent(s.templateKey);
        return (int) Math.ceil(max * (minPercent / 100.0));
    }

    private void cancelMatchStartForLowFill(LobbySession s, List<Player> players, boolean duringTeleportPhase) {
        s.teleportCountdownSeconds = -1;
        s.queueCountdownSeconds = -1;
        if (duringTeleportPhase) {
            announceAll(players, "Match start canceled: not enough players");
        } else {
            announceAll(players, "Timer canceled: not enough players. A new 5 minute countdown starts once fill is sufficient.");
        }
    }

    @Override
    public boolean isInLobbyWorld(World w) {
        if (w == null) return false;
        for (LobbySession s : sessions.values()) {
            if (s.worldName.equalsIgnoreCase(w.getName())) return true;
        }
        return false;
    }

    @Override
    public Set<String> lobbyWorldNames() {
        Set<String> out = new HashSet<>();
        for (LobbySession s : sessions.values()) out.add(s.worldName);
        return out;
    }

    private void startQueueTaskIfNeeded(LobbySession s) {
        if (s.countdownTask != null) return;
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                IronLegacyLobbyBridge b = bridge();
                if (b == null) {
                    cancel();
                    return;
                }
                if (s.players.isEmpty()) {
                    cleanupSession(s.worldName);
                    cancel();
                    return;
                }

                List<Player> online = onlinePlayers(s.players);
                if (online.isEmpty()) {
                    cleanupSession(s.worldName);
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

                if (!s.forceStart && current < required) {
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

    private void announceQueueMilestones(LobbySession s, List<Player> players) {
        int t = s.queueCountdownSeconds;
        if (t == 240) announceAll(players, "Beginning in: 4 Minutes");
        if (t == 180) announceAll(players, "Beginning in: 3 Minutes");
        if (t == 120) announceAll(players, "Beginning in: 2 Minutes");
        if (t == 60) announceAll(players, "Beginning in: 1 Minute");
        if (t == 30) announceAll(players, "Beginning in: 30 Seconds");
        if (t <= 10 && t >= 1) announceAll(players, "Beginning in: " + t + (t == 1 ? " Second" : " Seconds"));
    }

    private void beginTeleportCountdown(LobbySession s, List<Player> players, String message) {
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

    private void tickTeleportCountdown(LobbySession s, List<Player> players) {
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

    private void launchSession(LobbySession s) {
        IronLegacyLobbyBridge b = bridge();
        Set<UUID> onlinePlayers = new LinkedHashSet<>();
        for (UUID id : s.players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                onlinePlayers.add(id);
            }
        }
        if (b == null) {
            evacuatePlayersToHub(onlinePlayers);
            cleanupLobbySession(s);
            return;
        }
        if (onlinePlayers.isEmpty()) {
            cleanupLobbySession(s);
            return;
        }

        for (UUID id : onlinePlayers) {
            arenaState.pvpActive().remove(id);
            arenaState.activateCountdown().remove(id);
            arenaState.deactivateCountdown().remove(id);
            arenaState.pendingDeactivateOnDeath().remove(id);
            arenaState.pvpInvulnerableUntil().remove(id);
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) {
                hub.hubWorld().clearPvpOptInState(pl);
            }
        }
        b.assignTeamsForLobbyLaunch(s.templateKey, s.teamSizeMax, onlinePlayers);
        World instanceWorld = Bukkit.getWorld(s.worldName);
        boolean ok = b.startGameOnPreparedInstance(instanceWorld, onlinePlayers, true);
        if (!ok) {
            String err = b.lastGameStartError();
            boolean spawnPads = err != null && err.toLowerCase(Locale.ROOT).contains("spawn pad");
            HubLog.warn(hub, "action=lobby_match_start_failed",
                    "templateKey=" + s.templateKey,
                    "err=" + (err == null ? "unknown" : err));
            for (UUID id : onlinePlayers) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) {
                    continue;
                }
                if (spawnPads) {
                    p.sendMessage(Component.text(
                            "Someone messed up! Go complain to someone that \"SpawnPads\" have NOT been set up correctly!",
                            NamedTextColor.RED));
                } else {
                    HubPlayerFacingErrors.sendFailure(p,
                            "Failed to start match from lobby: " + (err == null ? "unknown error" : err));
                }
            }
            evacuatePlayersToHub(onlinePlayers);
        } else {
            // Match policy scope begins after a successful launch on this instance world.
            hub.pluginPolicies().markWorldAsMatch(s.worldName);
            hub.lobbyService().onHubLobbyMatchStarted(s.templateKey, s.worldName);
            if (instanceWorld != null) {
                TeamManager.getOrCreateWorldInstance(instanceWorld).sendAllFakeTeamPackets();
            }
        }
        cleanupLobbySession(s);
    }

    private void cleanupLobbySession(LobbySession s) {
        if (s == null) {
            return;
        }
        LobbySession removed = sessions.remove(s.worldName.toLowerCase(Locale.ROOT));
        if (removed == null) {
            removed = sessions.remove(s.templateKey.toLowerCase(Locale.ROOT));
        }
        if (removed == null) {
            return;
        }
        if (removed.countdownTask != null) {
            try {
                removed.countdownTask.cancel();
            } catch (Exception ignored) {
            }
        }
        IronLegacyLobbyBridge b = bridge();
        if (b == null || HubLobbyService.isHubManagedLobbyWorld(removed.worldName)) {
            return;
        }
        World w = Bukkit.getWorld(removed.worldName);
        if (w == null || w.getPlayers().isEmpty()) {
            try {
                b.destroyMatchInstanceIfEmpty(removed.worldName);
            } catch (Throwable ignored) {
            }
        }
    }

    private void cleanupSession(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        LobbySession s = sessions.remove(key.toLowerCase(Locale.ROOT));
        if (s == null) {
            return;
        }
        if (s.countdownTask != null) {
            try {
                s.countdownTask.cancel();
            } catch (Exception ignored) {
            }
        }
        clearSessionTeams(s);
        IronLegacyLobbyBridge b = bridge();
        if (b == null || HubLobbyService.isHubManagedLobbyWorld(s.worldName)) {
            return;
        }
        World w = Bukkit.getWorld(s.worldName);
        if (w == null || w.getPlayers().isEmpty()) {
            try {
                b.destroyMatchInstanceIfEmpty(s.worldName);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Drops match teams for everyone tracked in this lobby session (lobby destroy / empty queue). */
    private void clearSessionTeams(LobbySession s) {
        if (s == null || s.players.isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(s.worldName);
        if(world != null)
            TeamManager.getOrCreateWorldInstance(world).sendAllFakeTeamPackets();
    }

    @Override
    public void clearLobbyZoneAfterSpawnPads(String instanceWorldName, String templateKey) {
        if (instanceWorldName == null || instanceWorldName.isBlank()
                || templateKey == null || templateKey.isBlank()) {
            return;
        }
        Bukkit.getScheduler().runTask(hub, () -> clearLobbyZoneInWorld(instanceWorldName, templateKey));
    }

    /** Removes all blocks (air) and non-player entities inside the configured lobby volume. */
    private void clearLobbyZoneInWorld(String instanceWorldName, String templateKey) {
        World sessionWorld = Bukkit.getWorld(instanceWorldName);
        if (sessionWorld == null) {
            HubLog.warn(hub, "action=lobby_zone_clear_skip", "reason=no_session_world", "world=" + instanceWorldName);
            return;
        }
        LobbyZone zone = sessionZone(sessionWorld, templateKey, bridge());
        if (zone == null) {
            HubLog.warn(hub, "action=lobby_zone_clear_skip", "reason=no_zone", "templateKey=" + templateKey,
                    "world=" + instanceWorldName);
            return;
        }
        clearLobbyZoneBlocks(sessionWorld, zone);
        clearLobbyZoneEntities(sessionWorld, zone);
        HubLog.info(hub, "action=lobby_zone_cleared", "templateKey=" + templateKey, "world=" + instanceWorldName);
    }

    private static void clearLobbyZoneBlocks(World w, LobbyZone zone) {
        Location p1 = zone.pos1();
        Location p2 = zone.pos2();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                w.getChunkAt(x >> 4, z >> 4).load(true);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (zone.circle()) {
                        double dx = (x + 0.5) - zone.circleCenterX();
                        double dz = (z + 0.5) - zone.circleCenterZ();
                        if (dx * dx + dz * dz > zone.circleRadius() * zone.circleRadius()) {
                            continue;
                        }
                    }
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private static void clearLobbyZoneEntities(World w, LobbyZone zone) {
        for (Entity ent : w.getEntities()) {
            if (ent instanceof Player) {
                continue;
            }
            if (!zone.contains(ent.getLocation())) {
                continue;
            }
            ent.remove();
        }
    }

    private void evacuatePlayersToHub(Set<UUID> ids) {
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                hub.sendPlayerToHubSpawn(p);
            }
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

    private Location clampToZone(Location loc, LobbyZone zone) {
        Location p1 = zone.pos1();
        Location p2 = zone.pos2();
        double minY = Math.min(p1.getY(), p2.getY());
        double maxY = Math.max(p1.getY(), p2.getY());
        double y = Math.max(minY + 0.1, Math.min(maxY - 0.1, loc.getY()));
        if (zone.circle()) {
            double dx = loc.getX() - zone.circleCenterX();
            double dz = loc.getZ() - zone.circleCenterZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double limit = Math.max(0.5, zone.circleRadius() - 0.3);
            double x = loc.getX();
            double z = loc.getZ();
            if (dist > limit && dist > 0.0001) {
                x = zone.circleCenterX() + dx / dist * limit;
                z = zone.circleCenterZ() + dz / dist * limit;
            }
            return new Location(loc.getWorld(), x, y, z, loc.getYaw(), loc.getPitch());
        }
        double minX = Math.min(p1.getX(), p2.getX());
        double maxX = Math.max(p1.getX(), p2.getX());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxZ = Math.max(p1.getZ(), p2.getZ());
        double x = Math.max(minX + 0.2, Math.min(maxX - 0.2, loc.getX()));
        double z = Math.max(minZ + 0.2, Math.min(maxZ - 0.2, loc.getZ()));
        return new Location(loc.getWorld(), x, y, z, loc.getYaw(), loc.getPitch());
    }

    private LobbyZone sessionZone(World sessionWorld, String templateKey, IronLegacyLobbyBridge b) {
        if (sessionWorld == null || templateKey == null || templateKey.isBlank()) {
            return null;
        }
        Optional<HubLobbyWorldsYaml.LobbyZone> fromYaml = hub.lobbyLayout().worldsYaml().lobbyZone(templateKey);
        if (fromYaml.isPresent()) {
            HubLobbyWorldsYaml.LobbyZone z = fromYaml.get();
            Location s1 = z.pos1().clone();
            Location s2 = z.pos2().clone();
            s1.setWorld(sessionWorld);
            s2.setWorld(sessionWorld);
            return new LobbyZone(s1, s2, z.circleCenterX(), z.circleCenterZ(), z.circleRadius());
        }
        if (b == null) {
            return null;
        }
        var corners = b.lobbyZoneCorners(templateKey).orElse(null);
        if (corners == null) {
            Optional<String> inferred = hub.lobbyLayout().worldsYaml().resolveTemplateKeyForWorld(sessionWorld.getName());
            if (inferred.isPresent() && !inferred.get().equalsIgnoreCase(templateKey)) {
                return sessionZone(sessionWorld, inferred.get(), b);
            }
            return null;
        }
        Location s1 = new Location(sessionWorld, corners.x1(), corners.y1(), corners.z1());
        Location s2 = new Location(sessionWorld, corners.x2(), corners.y2(), corners.z2());
        return new LobbyZone(s1, s2);
    }

    private record LobbyZone(
            Location pos1,
            Location pos2,
            Double circleCenterX,
            Double circleCenterZ,
            Double circleRadius
    ) {
        LobbyZone(Location pos1, Location pos2) {
            this(pos1, pos2, null, null, null);
        }

        boolean circle() {
            return circleRadius != null && circleRadius > 0
                    && circleCenterX != null && circleCenterZ != null;
        }

        boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null || pos1 == null || pos2 == null) return false;
            if (!loc.getWorld().equals(pos1.getWorld()) || !loc.getWorld().equals(pos2.getWorld())) return false;
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY());
            if (loc.getY() < minY || loc.getY() > maxY) return false;
            if (circle()) {
                double dx = loc.getX() - circleCenterX;
                double dz = loc.getZ() - circleCenterZ;
                return dx * dx + dz * dz <= circleRadius * circleRadius;
            }
            double minX = Math.min(pos1.getX(), pos2.getX());
            double maxX = Math.max(pos1.getX(), pos2.getX());
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxZ = Math.max(pos1.getZ(), pos2.getZ());
            return loc.getX() >= minX && loc.getX() <= maxX
                    && loc.getZ() >= minZ && loc.getZ() <= maxZ;
        }
    }

    @Override
    public void prepareForLobbyJoin(Player p) {
        arenaState.prepareForLobbyJoin(p);
    }

    @Override
    public void applyLobbyLoadout(Player p) {
        int held = Math.min(8, Math.max(0, p.getInventory().getHeldItemSlot()));
        RegisteredServiceProvider<HubWorldApi> reg = Bukkit.getServicesManager().getRegistration(HubWorldApi.class);
        if (reg != null) {
            reg.getProvider().applyLobbyUtilityLoadout(p, held);
        }
    }

    @Override
    public boolean allowHostileMobSpawns() {
        IronLegacyLobbyBridge b = bridge();
        return b != null && b.lobbyHostileMobSpawns();
    }

    @Override
    public boolean allowPassiveMobSpawns() {
        IronLegacyLobbyBridge b = bridge();
        return b != null && b.lobbyPassiveMobSpawns();
    }

    @Override
    public void setAllowHostileMobSpawns(boolean value) {
        IronLegacyLobbyBridge b = bridge();
        if (b != null) {
            b.setLobbyHostileMobSpawns(value);
        }
    }

    @Override
    public void setAllowPassiveMobSpawns(boolean value) {
        IronLegacyLobbyBridge b = bridge();
        if (b != null) {
            b.setLobbyPassiveMobSpawns(value);
        }
    }

    public static final class LobbySession {
        public final String templateKey;
        public final String worldName;
        public final int teamSizeMax;
        public final int maxPlayers;
        public final Set<UUID> players = new LinkedHashSet<>();
        public BukkitTask countdownTask;
        public int queueCountdownSeconds = -1;
        public int teleportCountdownSeconds = -1;
        /** Admin /il start — skip minimum-fill checks for this countdown. */
        public boolean forceStart;

        public LobbySession(String templateKey, String worldName, int teamSizeMax, int maxPlayers) {
            this.templateKey = templateKey;
            this.worldName = worldName;
            this.teamSizeMax = teamSizeMax;
            this.maxPlayers = maxPlayers;
        }
    }
}
