package com.hollowcrown.hungergames.managers;

import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.integration.HubIntegrationRouting;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.plugin.RegisteredServiceProvider;
import com.hollowcrown.hub.team.TeamManager;
import com.hollowcrown.hub.team.TeamNamingRules;
import org.bukkit.*;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import java.time.Duration;

public class GameManager implements Listener {
    public enum State { LOBBY, FREEZE, RUNNING, ENDED }

    private final HungerGamesPlugin plugin;
    //private final TeamManager teamManager;
    private final SpawnManager spawnManager;
    private State state = State.LOBBY;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> countedParticipants = new HashSet<>();
    private final Set<UUID> bypassParticipants = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Map<UUID, Long> punished = new HashMap<>();
    private final Map<UUID, Location> assignedPads = new HashMap<>();
    private final Set<UUID> countedJoinIntent = new HashSet<>();
    private final Set<UUID> bypassJoinIntent = new HashSet<>();
    private Set<UUID> forcedParticipants = null;
    private final Scoreboard scoreboard;
    private String lastError = null;

    // freeze / leash control
    private boolean hardFreezeActive = false;           // true during the 30s countdown
    private long softLeashEndMs = 0L;                   // 4s leash window after TP
    private World currentWorld = null;                  // world for border + win handling
    private String activeInstanceWorldName = null;      // runtime cloned instance world, if any
    private String activeMatchTemplateKey = null;
    private WorldGameSettings activeMatchSettings = null;
    /** Eliminated players held in place before hub teleport so death cannon audio can play. */
    private final Set<UUID> legacyDeathHold = new HashSet<>();
    
    // border shrink management
    private BorderShrinkManager borderShrinkManager;

    public GameManager(HungerGamesPlugin plugin, SpawnManager spawns) {
        this.plugin = plugin;
        //this.teamManager = teams;
        this.spawnManager = spawns;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    public State getState() { return state; }
    public String getLastError() { return lastError; }

    public String getActiveMatchTemplateKey() {
        return activeMatchTemplateKey;
    }

    /** Match arena world for border / win logic; may remain set briefly after {@link #stopGame}. */
    public World getCurrentWorld() {
        return currentWorld;
    }

    /** Effective settings for the running match; null when lobby/ended. */
    public WorldGameSettings getActiveMatchSettings() {
        return activeMatchSettings;
    }

    public boolean isMatchParticipant(UUID id) {
        return participants.contains(id);
    }

    /**
     * True when {@code player} is inside the active match world (freeze or running). Used by Hub
     * for scope resolution (e.g. plugin policy MATCH scope) and post-game routing.
     */
    private static void scheduleLimboZoneClearAfterSpawnPads(String instanceWorldName, String templateKey) {
        RegisteredServiceProvider<PreMatchLimboService> reg =
                Bukkit.getServicesManager().getRegistration(PreMatchLimboService.class);
        if (reg == null) {
            return;
        }
        reg.getProvider().clearLimboZoneAfterSpawnPads(instanceWorldName, templateKey);
    }

    public boolean isPlayerInActiveMatchWorld(Player player) {
        if (player == null) return false;
        if (state == State.LOBBY || state == State.ENDED) return false;
        World w = player.getWorld();
        if (w == null) return false;
        String name = w.getName();
        if (activeInstanceWorldName != null && activeInstanceWorldName.equalsIgnoreCase(name)) {
            return true;
        }
        return currentWorld != null && currentWorld.getName().equalsIgnoreCase(name);
    }
    public void markJoinIntentCounted(Player p) {
        UUID id = p.getUniqueId();
        countedJoinIntent.add(id);
        bypassJoinIntent.remove(id);
    }
    public void markJoinIntentBypass(Player p) {
        UUID id = p.getUniqueId();
        bypassJoinIntent.add(id);
        countedJoinIntent.remove(id);
    }
    public void clearJoinIntent(Player p) {
        UUID id = p.getUniqueId();
        bypassJoinIntent.remove(id);
        countedJoinIntent.remove(id);
    }

    /** Starts the game with the new staged flow. */
    public boolean startGame(World world) {
        return startGame(world, null, false, false);
    }

    public boolean startGame(World world, Set<UUID> explicitParticipants) {
        return startGame(world, explicitParticipants, false, false);
    }

    public boolean startGameFromTemplate(String templateKey, Set<UUID> explicitParticipants) {
        return startGameFromTemplate(templateKey, explicitParticipants, false);
    }

    public boolean startGameFromTemplate(String templateKey, Set<UUID> explicitParticipants, boolean immediateTeleportFromLimbo) {
        if (state != State.LOBBY && state != State.ENDED) {
            lastError = "Game already running or freezing.";
            plugin.getLogger().warning(lastError);
            return false;
        }
        TemplateWorldManager.MatchInstance instance = plugin.templates().createMatchInstanceFromTemplate(templateKey);
        if (instance == null) {
            lastError = "Failed to create runtime instance from template: " + templateKey;
            plugin.getLogger().warning(lastError);
            return false;
        }
        World iw = Bukkit.getWorld(instance.worldName());
        if (iw == null) {
            lastError = "Instance world failed to load: " + instance.worldName();
            plugin.getLogger().warning(lastError);
            return false;
        }
        activeInstanceWorldName = iw.getName();
        return startGame(iw, explicitParticipants, true, immediateTeleportFromLimbo);
    }

    /** Starts a match on a pre-created runtime instance world. */
    public boolean startGameOnPreparedInstance(World instanceWorld, Set<UUID> explicitParticipants, boolean immediateTeleportFromLimbo) {
        if (instanceWorld == null) {
            lastError = "Prepared instance world is null.";
            return false;
        }
        activeInstanceWorldName = instanceWorld.getName();
        return startGame(instanceWorld, explicitParticipants, true, immediateTeleportFromLimbo);
    }

    private boolean startGame(World world, Set<UUID> explicitParticipants, boolean skipTemplateClone, boolean immediateTeleportFromLimbo) {
        if (state != State.LOBBY && state != State.ENDED) {
            lastError = "Game already running or freezing.";
            plugin.getLogger().warning(lastError);
            return false;
        }

        String padTemplateKey = plugin.templates().resolveTemplateKeyForWorld(world.getName()).orElse(null);
        if (padTemplateKey == null) {
            var ti = plugin.templates().getTemplateByWorldName(world.getName());
            if (ti != null) padTemplateKey = ti.key();
        }
        if (padTemplateKey == null || spawnManager.count(padTemplateKey) == 0) {
            lastError = "No spawn pads set for template"
                    + (padTemplateKey != null ? " '" + padTemplateKey + "'" : "")
                    + "! Use /il addspawn or /il addspawnring in the template editor.";
            plugin.getLogger().warning(lastError);
            return false;
        }
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(world);
        teamManagerInstance.gameNotRunning = false;
        teamManagerInstance.sendAllFakeTeamPackets();

        if (!skipTemplateClone) {
            // If a template world was selected, run the match on a cloned instance.
            var templateInfo = plugin.templates().getTemplateByWorldName(world.getName());
            if (templateInfo != null) {
                padTemplateKey = templateInfo.key();
                var instance = plugin.templates().createMatchInstanceFromTemplate(templateInfo.key());
                if (instance == null) {
                    lastError = "Failed to create runtime instance from template: " + templateInfo.worldName();
                    plugin.getLogger().warning(lastError);
                    return false;
                }
                World iw = Bukkit.getWorld(instance.worldName());
                if (iw == null) {
                    lastError = "Instance world failed to load: " + instance.worldName();
                    plugin.getLogger().warning(lastError);
                    return false;
                }
                world = iw;
                activeInstanceWorldName = iw.getName();
                teamManagerInstance.friendlyFire = templateInfo.teamFriendlyFire();
                teamManagerInstance.allyFriendlyFire = templateInfo.allyFriendlyFire();
            } else {
                activeInstanceWorldName = null;
            }
        }

        participants.clear();
        countedParticipants.clear();
        bypassParticipants.clear();
        eliminated.clear();
        assignedPads.clear();
        currentWorld = world;
        activeMatchTemplateKey = plugin.templates().resolveTemplateKeyForWorld(world.getName()).orElse(padTemplateKey);
        activeMatchSettings = plugin.worldSettings().resolve(activeMatchTemplateKey);
        forcedParticipants = explicitParticipants == null ? null : new HashSet<>(explicitParticipants);

        int countedCap = Math.max(1, plugin.files().gameflow().getInt("caps.countedPlayers", 100));
        int countedAdded = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            UUID id = p.getUniqueId();
            if (forcedParticipants != null && !forcedParticipants.contains(id)) continue;
            boolean op = p.isOp();
            boolean bypassIntent = bypassJoinIntent.contains(id);
            boolean forcedCounted = countedJoinIntent.contains(id);

            if (bypassIntent) {
                participants.add(id);
                bypassParticipants.add(id);
                continue;
            }

            // OPs do not count/join unless they entered the normal counted flow.
            if (op && !forcedCounted && !activeMatchSettings.includeOpsInGame()) continue;

            if (countedAdded >= countedCap) continue;
            participants.add(id);
            countedParticipants.add(id);
            countedAdded++;
        }

        String padsKey = activeMatchTemplateKey != null ? activeMatchTemplateKey : padTemplateKey;
        List<Location> padsRaw = new ArrayList<>(spawnManager.getSpawnPads(padsKey, world));
        List<Location> pads2 = new ArrayList<>();
        for (Location pad : padsRaw) {
            if (pad == null) continue;
            Location r = pad.clone();
            r.setWorld(world);
            pads2.add(r);
        }
        //This causes random spread around the circle.
        //Collections.shuffle(pads);
        //but this next function tries to space out spawn order for any number of pads.
        List<Location> pads = evenDistribute(pads2);

        List<Player> plist = participants.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        assignAutoTeamsIfNeeded(plist);

        if (pads.size() < plist.size()) {
            activeMatchTemplateKey = null;
            activeMatchSettings = null;
            lastError = "Not enough spawn pads: need " + plist.size() + ", have " + pads.size()
                    + ". Add " + (plist.size() - pads.size()) + " more.";
            plugin.getLogger().warning(lastError);
            return false;
        }

        try {
            HungerGamesPlugin.get().replenish().setMatchContext(activeMatchTemplateKey, world);
        } catch (Throwable ignored) {}

        // Prepare world border (centered on world spawn, set to max size)
        WorldBorder wb = world.getWorldBorder();
        double start = activeMatchSettings.borderStartSize();
        wb.setCenter(world.getSpawnLocation());
        wb.setSize(start);

        // Run any hooks (e.g., envoy). These can be delayed via "delay:<ticks>:/command"
        runHooks(plugin.getConfig().getStringList("hooks.onGameStart"));
        final World launchWorld = world;
        final WorldGameSettings ws = activeMatchSettings;

        final boolean fromLimboLaunch = immediateTeleportFromLimbo;
        final String limboClearTemplateKey = activeMatchTemplateKey;
        Runnable doTeleportPhase = () -> {
            new BukkitRunnable() {
            @Override public void run() {
                // assign pads 1:1 and TP
                for (int i = 0; i < plist.size(); i++) {
                    Player p = plist.get(i);
                    Location pad = pads.get(i).clone();
                    assignedPads.put(p.getUniqueId(), pad);
                    // safety
                    p.setFallDistance(0f);
                    p.setNoDamageTicks(100);
                    p.teleport(pad);
                    // Match launch transition policy for normal counted players.
                    if (countedParticipants.contains(p.getUniqueId())) {
                        p.getInventory().clear();
                        p.getInventory().setArmorContents(new ItemStack[4]);
                        p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                        boolean keepAdventure = ws.keepAdventureOnStart();
                        p.setGameMode(keepAdventure ? GameMode.ADVENTURE : GameMode.SURVIVAL);
                    }
                    p.setFoodLevel(20);
                    p.setSaturation(5f);
                    p.setHealth(Math.min(20.0, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
                }

                if (fromLimboLaunch && limboClearTemplateKey != null) {
                    scheduleLimboZoneClearAfterSpawnPads(launchWorld.getName(), limboClearTemplateKey);
                }

                try {
                    launchWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, ws.doDaylightCycle());
                    launchWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, ws.doWeatherCycle());
                } catch (Throwable ignored) {}
                launchWorld.setTime(6000L);
                launchWorld.setStorm(false);
                launchWorld.setThundering(false);

                if (ws.freeMovementAfterTeleport()) {
                    state = State.RUNNING;
                    hardFreezeActive = false;
                    softLeashEndMs = 0L;
                    try { plugin.leaderboardSidebar().refreshAll(); } catch (Throwable ignored) {}
                    try { HungerGamesPlugin.get().replenish().startSchedule(); } catch (Throwable ignored) {}
                    
                    // Initialize and schedule border shrink phases
                    borderShrinkManager = new BorderShrinkManager(plugin, launchWorld, plist, GameManager.this, ws.borderShrinkSchedule());
                    borderShrinkManager.scheduleAllPhases();
                } else {
                    state = State.FREEZE;
                    hardFreezeActive = false;
                    softLeashEndMs = System.currentTimeMillis() + 5000L;
                    try { plugin.leaderboardSidebar().refreshAll(); } catch (Throwable ignored) {}

                    new BukkitRunnable() {
                        @Override public void run() {
                            beginCountdown(plist, launchWorld);
                            try { HungerGamesPlugin.get().replenish().startSchedule(); } catch (Throwable ignored) {}
                        }
                    }.runTaskLater(plugin, 5 * 20L);
                }
            }
            }.runTask(plugin);
        };

        boolean skipPreTeleportCountdown = immediateTeleportFromLimbo || !ws.movementCountdownEnabled();
        if (skipPreTeleportCountdown) {
            doTeleportPhase.run();
        } else {
            new BukkitRunnable() {
                int pre = 5;
                @Override public void run() {
                    if (pre <= 0) {
                        doTeleportPhase.run();
                        cancel();
                        return;
                    }
                    for (Player p : promptAudience(plist, launchWorld)) {
                        sendPrompt(
                                p,
                                Component.text(Integer.toString(pre), NamedTextColor.RED),
                                Component.text("DO NOT MOVE", NamedTextColor.DARK_RED),
                                Component.text("DO NOT MOVE - Teleporting in " + pre, NamedTextColor.RED)
                        );
                    }
                    pre--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }

        return true;
    }

    //Tries to evenly distribute spawn locations to be the furthest possible from each other, but with a random starting point
    private List<Location> evenDistribute(List<Location> spots){
        ArrayList<Location> temp = new ArrayList<>(spots);
        ArrayList<Location> result = new ArrayList<>();
        Random random = new Random();
        result.add(temp.remove(random.nextInt(temp.size())));
        while (!temp.isEmpty()){
            double maxDist = -1;
            Location furthest = null;
            double dist;
            for(Location sample : temp){
                dist = Double.MAX_VALUE;
                for(Location existing : result)
                    //calculate the minimum distance to an already selected point as to optimize for the most isolated one
                    dist = Math.min(dist, sample.distanceSquared(existing));
                if(dist > maxDist){
                    furthest = sample;
                    maxDist = dist;
                }
            }
            //This probably isn't necessary but might prevent errors
            if(furthest != null){
                temp.remove(furthest);
                result.add(furthest);
            }
        }
        return result;
    }

    public static String formatList(List<String> items) {
        if (items == null || items.isEmpty())
            return "";
        if (items.size() == 1)
            return items.get(0);
        if (items.size() == 2)
            return items.get(0) + " and " + items.get(1);
        String standardElements = String.join(", ", items.subList(0, items.size() - 1));
        return standardElements + ", and " + items.getLast();
    }

    private void beginCountdown(List<Player> plist, World launchWorld) {
        hardFreezeActive = true; // punish moves from now on

        final int base = activeMatchSettings != null
                ? activeMatchSettings.freezeCountdownSeconds()
                : plugin.getConfig().getInt("game.countdownSeconds", 30);
        new BukkitRunnable() {
            int t = base;
            @Override public void run() {
                if (t <= 0) {
                    // BEGIN!
                    for (Player p : promptAudience(plist, currentWorld)) {
                        sendPrompt(
                                p,
                                Component.text("BEGIN!", NamedTextColor.GOLD),
                                Component.empty(),
                                Component.text("BEGIN!", NamedTextColor.GOLD)
                        );
                    }

                    state = State.RUNNING;
                    hardFreezeActive = false; // freeze over
                    try { plugin.leaderboardSidebar().refreshAll(); } catch (Throwable ignored) {}

                    // Initialize and schedule border shrink phases
                    borderShrinkManager = new BorderShrinkManager(plugin, launchWorld, plist, GameManager.this, activeMatchSettings.borderShrinkSchedule());
                    borderShrinkManager.scheduleAllPhases();

                    cancel();
                    return;
                }

                NamedTextColor col = (t >= 16) ? NamedTextColor.GREEN : (t >= 6 ? NamedTextColor.YELLOW : NamedTextColor.RED);

                for (Player p : promptAudience(plist, currentWorld)) {
                    sendPrompt(
                            p,
                            Component.text(Integer.toString(t), col),
                            Component.empty(),
                            Component.text("Countdown: " + t, col)
                    );
                }
                t--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void stopGame(boolean announce) {
        //Bukkit.getLogger().info("Stop Game Called!");
        if (state == State.LOBBY) {
            try { plugin.leaderboardSidebar().refreshAll(); } catch (Throwable ignored) {}
            return;
        }
        //Bukkit.getLogger().info("Game Ending");
        state = State.ENDED;
        if (announce) Bukkit.broadcast(Component.text("[HCIronLegacy] Game ended.", NamedTextColor.YELLOW));
        try {
            HungerGamesPlugin.get().replenish().stopSchedule();
            HungerGamesPlugin.get().replenish().clearMatchContext();
        } catch (Throwable ignored) {}
        runHooks(plugin.getConfig().getStringList("hooks.onGameEnd"));

        //Bukkit.getLogger().info("beginning reset");

        assignedPads.clear();
        countedParticipants.clear();
        bypassParticipants.clear();
        forcedParticipants = null;
        hardFreezeActive = false;
        activeMatchTemplateKey = null;
        activeMatchSettings = null;
        
        // Clean up border shrink manager
        if (borderShrinkManager != null) {
            borderShrinkManager.reset();
            borderShrinkManager = null;
        }
        
        //clearMatchTeams();
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getInstance(currentWorld);
        if(teamManagerInstance != null) {
            teamManagerInstance.gameNotRunning = true;
            teamManagerInstance.clearAllTeams();
            TeamManager.destroyInstance(teamManagerInstance.getIdentifier());
        }
        //TeamManager.nukeTeamsForWorld(currentWorld);

        // Tear down runtime match instance worlds when the match ends.
        if (activeInstanceWorldName != null) {
            String instanceToCleanup = activeInstanceWorldName;
            try {
                evacuatePlayersFromInstance(instanceToCleanup);
                boolean ok = plugin.templates().destroyMatchInstanceByWorld(instanceToCleanup);
                if (!ok) {
                    plugin.getLogger().warning("Could not fully cleanup instance world immediately: " + instanceToCleanup + ". Scheduling retries.");
                    scheduleInstanceCleanupRetry(instanceToCleanup, 1);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Error cleaning instance world " + instanceToCleanup + ": " + t.getMessage());
                scheduleInstanceCleanupRetry(instanceToCleanup, 1);
            } finally {
                activeInstanceWorldName = null;
            }
        }
        try { plugin.leaderboardSidebar().refreshAll(); } catch (Throwable ignored) {}
    }

    /** Clears match teams for this instance only — never wipes other lobbies/sessions. */
    /*private void clearMatchTeams() {
        try {
            Set<UUID> scoped = new HashSet<>(participants);
            scoped.addAll(eliminated);
            if (activeInstanceWorldName != null) {
                World w = Bukkit.getWorld(activeInstanceWorldName);
                if (w != null) {
                    for (Player p : w.getPlayers()) {
                        scoped.add(p.getUniqueId());
                    }
                }
            }
            if (!scoped.isEmpty()) {
                teamManager.clearTeamsFor(scoped);
            }
        } catch (Throwable ignored) {
        }
    }*/

    private void evacuatePlayersFromInstance(String instanceWorldName) {
        if (instanceWorldName == null || instanceWorldName.isBlank()) return;
        World instance = Bukkit.getWorld(instanceWorldName);
        if (instance == null) return;
        List<Player> players = new ArrayList<>(instance.getPlayers());
        if (players.isEmpty()) return;
        Set<UUID> handled = new java.util.HashSet<>();
        for (Player p : players) {
            if (!handled.add(p.getUniqueId())) continue;
            plugin.hollowPartyService().flatMap(ps -> ps.partyForMember(p.getUniqueId()))
                    .filter(pv -> pv.isLeader(p.getUniqueId()))
                    .ifPresent(pv -> handled.addAll(pv.membersJoinOrder()));
            plugin.lobby().sendToLobbyExact(p, true, true);
        }
    }

    private void scheduleInstanceCleanupRetry(String worldName, int attempt) {
        if (worldName == null || worldName.isBlank() || attempt > 10) return;
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    evacuatePlayersFromInstance(worldName);
                    boolean ok = plugin.templates().destroyMatchInstanceByWorld(worldName);
                    if (!ok) {
                        scheduleInstanceCleanupRetry(worldName, attempt + 1);
                    }
                } catch (Throwable ignored) {
                    scheduleInstanceCleanupRetry(worldName, attempt + 1);
                }
            }
        }.runTaskLater(plugin, 40L);
    }

    public void forceStop() {
        stopGame(false);
    }

    // --- Adventure bridge: Bukkit ChatColor -> NamedTextColor ---
    @SuppressWarnings("deprecation") // Bridge function for team color compatibility
    private static NamedTextColor toNamed(ChatColor c) {
        return switch (c) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE, RESET -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }

    private static boolean isAutoTeamName(String name) {
        return name != null && name.trim().matches("(?i)team\\s+\\d+(?:\\s*#\\d+)?");
    }

    /*public void applyTeamColors() {
        for (com.hollowcrown.hungergames.model.Team t : teamManager.getTeams()) {
            String sbName = ("hg_" + t.getName()).replaceAll("[^A-Za-z0-9_]", "").toLowerCase();
            Team sbTeam = scoreboard.getTeam(sbName);
            if (sbTeam == null) sbTeam = scoreboard.registerNewTeam(sbName);

            @SuppressWarnings("deprecation")
            ChatColor scoreboardChat = t.getDisplayNameRgb() != null
                    ? TeamColorRgb.nearestChatColor(t.getDisplayNameRgb())
                    : t.getColor();
            sbTeam.setColor(scoreboardChat);

            String teamName = t.getName();
            boolean auto = isAutoTeamName(teamName);
            String prefixPlain = (auto ? "{" + teamName + "}" : "[" + teamName + "]") + " ";

            sbTeam.prefix(Component.text(prefixPlain, NamedTextColor.WHITE));

            sbTeam.setSuffix("");

            for (UUID id : t.getMembers()) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(id);
                if (off.getName() == null) continue;

                if (!sbTeam.hasEntry(off.getName())) {
                    sbTeam.addEntry(off.getName());
                }

                Player p = off.getPlayer();
                if (p != null) {
                    TextColor nameColor = t.getDisplayNameRgb() != null
                            ? TextColor.color(0xFF000000 | (0xFFFFFF & t.getDisplayNameRgb()))
                            : toNamed(t.getColor());
                    Component prefixComp = Component.text(prefixPlain, NamedTextColor.WHITE);
                    Component nameComp = Component.text(p.getName(), nameColor);
                    p.playerListName(prefixComp.append(nameComp));
                    p.displayName(prefixComp.append(nameComp));
                }
            }
        }
    }*/

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (legacyDeathHold.contains(id)) {
            if (e.getTo() != null && e.getFrom().toVector().distanceSquared(e.getTo().toVector()) < 0.0001) return;
            e.setCancelled(true);
            return;
        }
        if (state != State.FREEZE) return;
        if (!participants.contains(id)) return;

        // ignore micro-movements
        if (e.getFrom().toVector().distanceSquared(e.getTo().toVector()) < 0.0001) return;

        long now = System.currentTimeMillis();

        // During soft leash (first 5s after TP): snap back to pad, no punishment.
        if (now < softLeashEndMs) {
            Location pad = assignedPads.get(id);
            if (pad != null) {
                // if they left the pad block, snap them
                if (e.getTo().getBlockX() != pad.getBlockX() ||
                    e.getTo().getBlockY() != pad.getBlockY() ||
                    e.getTo().getBlockZ() != pad.getBlockZ()) {
                    new BukkitRunnable() { @Override public void run() {
                        if (e.getPlayer().isOnline()) {
                            e.getPlayer().teleport(pad);
                            e.getPlayer().setFallDistance(0f);
                        }
                    }}.runTask(plugin);
                }
            }
            return;
        }

        // Hard freeze (the 30s countdown): default is teleport-back + damage.
        if (hardFreezeActive) {
            long last = punished.getOrDefault(id, 0L);
            boolean launchMode = activeMatchSettings != null
                    ? activeMatchSettings.launchOnMove()
                    : plugin.getConfig().getBoolean("game.launchOnMove", false);
            long cooldownMs = launchMode ? 2000L : 500L;
            if (now - last < cooldownMs) return;
            punished.put(id, now);
            Location pad = assignedPads.get(id);
            if (pad != null) {
                new BukkitRunnable() { @Override public void run() {
                    if (e.getPlayer().isOnline()) {
                        e.getPlayer().teleport(new Location(pad.getWorld(), pad.x(), pad.y(), pad.z())); //Don't pass a rotation so the player's is maintained
                        e.getPlayer().setFallDistance(0f);
                    }
                }}.runTask(plugin);
            }
            if (launchMode) {
                int height = activeMatchSettings != null
                        ? activeMatchSettings.freezePenaltyHeight()
                        : plugin.getConfig().getInt("game.freezePenaltyHeight", 100);
                e.getPlayer().setVelocity(new org.bukkit.util.Vector(0, Math.max(2, height / 20.0), 0));
                e.getPlayer().sendMessage(Component.text("You should not have moved!", NamedTextColor.RED));
                new BukkitRunnable() {
                    @Override public void run() {
                        if (e.getPlayer().isOnline() && e.getPlayer().getHealth() > 0) {
                            e.getPlayer().setHealth(0.0);
                        }
                    }
                }.runTaskLater(plugin, 40L);
            } else {
                Player mover = e.getPlayer();
                if (mover.getHealth() > 0) {
                    mover.setHealth(Math.max(0.0, mover.getHealth() - 1.0));
                }
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        boolean suppress = activeMatchSettings != null
                ? activeMatchSettings.suppressDeathMessages()
                : plugin.getConfig().getBoolean("game.suppressDeathMessages", true);
        if (suppress) {
            e.deathMessage(null);
        }
        boolean inGame = participants.contains(e.getEntity().getUniqueId());
        if (inGame) {
            Player dead = e.getEntity();
            final UUID deadId = dead.getUniqueId();
            final Location deathSpot = dead.getLocation().clone();

            boolean cannon = activeMatchSettings != null
                    ? activeMatchSettings.playCannonOnDeath()
                    : plugin.getConfig().getBoolean("game.playCannonOnDeath", true);
            if (cannon && currentWorld != null) {
                for (Player listener : currentWorld.getPlayers()) {
                    if (listener == null || !listener.isOnline()) continue;
                    listener.playSound(deathSpot, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 1.0f, 0.8f);
                }
            }

            e.setKeepInventory(false);
            e.getDrops().clear();
            removeFromGame(deadId);

            final long respawnAt = 1L;
            final long hubAt = respawnAt + 100L;
            new BukkitRunnable() {
                @Override public void run() {
                    Player p = Bukkit.getPlayer(deadId);
                    if (p == null || !p.isOnline()) return;
                    p.spigot().respawn();
                    legacyDeathHold.add(deadId);
                    p.setGameMode(GameMode.SPECTATOR);
                    p.teleport(deathSpot);
                    p.setFallDistance(0f);
                    p.showTitle(Title.title(
                            Component.text("Your Legacy is Over", NamedTextColor.DARK_RED),
                            Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofMillis(500))
                    ));
                }
            }.runTaskLater(plugin, respawnAt);
            new BukkitRunnable() {
                @Override public void run() {
                    legacyDeathHold.remove(deadId);
                    Player p = Bukkit.getPlayer(deadId);
                    if (p != null && p.isOnline()) {
                        p.setGameMode(GameMode.ADVENTURE);
                        sendPlayerToHub(p);
                    }
                    evaluateWinCondition();
                }
            }.runTaskLater(plugin, hubAt);
        }
    }

    @EventHandler
    public void onQuitDuringLegacyHold(PlayerQuitEvent e) {
        legacyDeathHold.remove(e.getPlayer().getUniqueId());
    }

    /** One team left? Celebrate, reset border, end game. */
    private void evaluateWinCondition() {
        if (state == State.ENDED) return;

        // group alive participants by their team (or solo bucket)
        Map<String, Integer> aliveGroups = new HashMap<>();
        for (UUID id : participants) {
            if (eliminated.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null || p.isDead()) continue;

            var team = TeamManager.findPlayerTeam(id);
            String key = (team != null) ? "team:" + team.getName() : "solo:" + id;
            aliveGroups.merge(key, 1, Integer::sum);
        }

        if (aliveGroups.size() <= 1 && state != State.LOBBY) {
            Player winner = null;
            for (UUID id : participants) {
                if (eliminated.contains(id)) continue;
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline() && !p.isDead()) {
                    winner = p;
                    break;
                }
            }
            if (winner != null) {
                Player finalWinner = winner;
                //If anyone in the future is trying to find where it announces the winning team, this is where it is!
                com.hollowcrown.hub.team.TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getInstance(finalWinner.getWorld());
                if(teamManagerInstance != null){
                    TeamManager.Team team = teamManagerInstance.findPlayerTeam(winner);
                    if(team != null) {
                        Bukkit.broadcast(Component.text("[" + team.getName() + "]", TextColor.color(team.color.asRGB())).append(Component.text(" has won!", NamedTextColor.YELLOW)));
                        List<String> playerNames = new ArrayList<>(team.members.size());
                        //Bukkit.broadcast(Component.text(team.members.toString()));
                        for(UUID uuid : team.members){
                            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                            if(offlinePlayer.isOnline() || offlinePlayer.hasPlayedBefore()){
                                if(offlinePlayer.getName() != null)
                                    playerNames.add(offlinePlayer.getName());
                            }
                        }
                        Bukkit.broadcast(Component.text("Congratulations " + formatList(playerNames) + "!", NamedTextColor.YELLOW));
                    }
                }
                finalWinner.showTitle(Title.title(
                        Component.text("Congratulations!", NamedTextColor.GOLD),
                        Component.text("You have won!", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(200))
                ));
                finalWinner.setInvulnerable(true);
                new BukkitRunnable() {
                    @Override public void run() {
                        if (finalWinner.isOnline()) {
                            finalWinner.setInvulnerable(false);
                            sendPlayerToHub(finalWinner);
                        }
                        if (currentWorld != null) {
                            double start = activeMatchSettings != null
                                    ? activeMatchSettings.borderStartSize()
                                    : plugin.getConfig().getDouble("worldborder.startSize");
                            currentWorld.getWorldBorder().setSize(start);
                        }
                        stopGame(true);
                    }
                }.runTaskLater(plugin, 200L);
            } else {
                if (currentWorld != null) {
                    double start = activeMatchSettings != null
                            ? activeMatchSettings.borderStartSize()
                            : plugin.getConfig().getDouble("worldborder.startSize");
                    currentWorld.getWorldBorder().setSize(start);
                }
                stopGame(true);
            }
        }
    }

    private void assignAutoTeamsIfNeeded(List<Player> plist) {
        if (activeMatchSettings == null || !activeMatchSettings.autoAssignTeams()) return;

        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(plist.getFirst().getWorld());

        var info = activeMatchTemplateKey != null ? plugin.templates().getTemplateByNameKey(activeMatchTemplateKey) : null;
        int teamCap = info != null ? Math.max(1, info.teamSizeMax()) : Math.max(1, plugin.getConfig().getInt("game.teamSize", 2));

        Set<UUID> pIds = plist.stream().map(Player::getUniqueId).collect(Collectors.toCollection(HashSet::new));
        List<UUID> unassigned = new ArrayList<>();
        for (Player p : plist) {
            if (teamManagerInstance.findPlayerTeam(p.getUniqueId()) == null) unassigned.add(p.getUniqueId());
        }
        if (unassigned.isEmpty()) return;
        Collections.shuffle(unassigned);

        List<TeamManager.Team> partial = teamManagerInstance.teams.values().stream()
                .filter(t -> t.members.stream().anyMatch(pIds::contains))
                .filter(t -> t.members.size() < teamCap)
                .sorted((a, b) -> Integer.compare(b.members.size(), a.members.size()))
                .toList();

        Iterator<UUID> it = unassigned.iterator();
        for (TeamManager.Team t : partial) {
            while (t.members.size() < teamCap && it.hasNext()) {
                UUID nid = it.next();
                try {
                    t.members.add(nid);
                } catch (IllegalStateException ignored) {
                    break;
                }
            }
        }

        List<UUID> remaining = new ArrayList<>();
        while (it.hasNext()) remaining.add(it.next());

        //TeamNameDatabaseManager db = plugin.teamNames();
        while (!remaining.isEmpty()) {
            UUID owner = remaining.remove(0);
            String nm = TeamNamingRules.randomTeamName(teamManagerInstance);
            //String nm = db.takeUnusedNameForNewTeam();
            //if (nm == null) nm = db.generateSequentialTeamName();
            //ChatColor color = db.pickUnusedChatColor();
            Color color = teamManagerInstance.getRandomUnusedColor();
            TeamManager.Team created = new TeamManager.Team(nm, color, teamManagerInstance);
            created.members.add(owner);
            while (created.members.size() < teamCap && !remaining.isEmpty()) {
                UUID nid = remaining.removeFirst();
                try {
                    created.members.add(nid);
                } catch (IllegalStateException ignored) {
                    break;
                }
            }
        }
        teamManagerInstance.sendAllFakeTeamPackets();
    }

    private void removeFromGame(UUID id) {
        participants.remove(id);
        countedParticipants.remove(id);
        bypassParticipants.remove(id);
        eliminated.add(id);
        assignedPads.remove(id);
        punished.remove(id);
        //Player should NOT be removed from their team when they die.
        //TeamManager.Team team = TeamManager.findPlayerTeam(id);
        //if(team != null)
        //    team.members.remove(id);
    }

    private void sendPlayerToHub(Player p) {
        if (p == null) return;
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        plugin.game().clearJoinIntent(p);
        HubIntegrationRouting.returnToHubOrLobby(plugin, p, true, true);
    }

    private boolean shouldUseBigTitle(Player p) {
        if (p == null) return false;
        if (!p.isOp()) return true;
        return countedParticipants.contains(p.getUniqueId());
    }

    private List<Player> promptAudience(List<Player> participantsInRound, World world) {
        LinkedHashMap<UUID, Player> out = new LinkedHashMap<>();
        if (participantsInRound != null) {
            for (Player p : participantsInRound) {
                if (p != null && p.isOnline()) out.put(p.getUniqueId(), p);
            }
        }
        if (world != null) {
            for (Player p : world.getPlayers()) {
                if (p == null || !p.isOnline() || !p.isOp()) continue;
                if (!participants.contains(p.getUniqueId())) out.putIfAbsent(p.getUniqueId(), p);
            }
        }
        return new ArrayList<>(out.values());
    }

    private void sendPrompt(Player p, Component title, Component subtitle, Component chatFallback) {
        if (p == null || !p.isOnline()) return;
        if (shouldUseBigTitle(p)) {
            p.showTitle(Title.title(
                    title == null ? Component.empty() : title,
                    subtitle == null ? Component.empty() : subtitle,
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
            ));
        } else if (chatFallback != null) {
            p.sendMessage(chatFallback);
        }
    }

    // supports "delay:<ticks>:/some command" lines in hooks
    private void runHooks(List<String> cmds) {
        if (cmds == null) return;
        for (String raw : cmds) {
            if (raw == null || raw.isBlank()) continue;

            long delay = 0L;
            String cmd = raw;

            if (raw.startsWith("delay:")) {
                int first = raw.indexOf(':', 6); // after "delay:"
                if (first > 6) {
                    try {
                        delay = Long.parseLong(raw.substring(6, first));
                        cmd = raw.substring(first + 1);
                    } catch (NumberFormatException ignore) { /* no-op */ }
                }
            }

            final String toRun = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toRun),
                    delay);
        }
    }
}
