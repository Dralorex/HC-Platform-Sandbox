package com.hollowcrown.hungergames.integration;

import com.hollowcrown.api.integration.IronLegacyLobbyBridge;
import com.hollowcrown.api.integration.IronLegacyWorldsCatalog;
import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.team.TeamManager;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hub.team.TeamNamingRules;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

//TODO: [Hydraulic] Hook new teams system up, probably here.
/**
 * Engine hooks for Hub-owned pre-match lobby (templates, match start, teams, lobby routing).
 */
public final class IronLegacyLobbyBridgeImpl implements IronLegacyLobbyBridge {

    private final HungerGamesPlugin plugin;
    private final List<String> randomTeamNames = List.of(
            "Ravens", "Sentinels", "Valkyries", "Outriders", "Wardens",
            "Nomads", "Phantoms", "Cyclones", "Titans", "Gladiators"
    );

    public IronLegacyLobbyBridgeImpl(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        migrateLobbyYamlDefaultsIfNeeded();
    }

    @Override
    public Optional<TemplateRow> findTemplate(String keyOrWorld) {
        var cat = plugin.worldsCatalog().flatMap(c -> c.templateByKeyOrWorld(keyOrWorld));
        if (cat.isPresent()) {
            var info = cat.get();
            return Optional.of(new TemplateRow(info.key(), info.worldName(), info.maxPlayers(), info.teamSizeMax()));
        }
        var info = plugin.templates().getTemplateByNameKey(keyOrWorld);
        if (info == null) return Optional.empty();
        return Optional.of(new TemplateRow(info.key(), info.worldName(), info.maxPlayers(), info.teamSizeMax()));
    }

    @Override
    public Optional<MatchInstanceRow> createMatchInstance(String templateKey) {
        var inst = plugin.templates().createMatchInstanceFromTemplate(templateKey);
        if (inst == null) return Optional.empty();
        return Optional.of(new MatchInstanceRow(inst.worldName()));
    }

    @Override
    public void destroyMatchInstanceIfEmpty(String worldName) {
        plugin.templates().destroyMatchInstanceByWorld(worldName);
    }

    @Override
    public int minimumFillPercent(String templateKey) {
        var cat = plugin.worldsCatalog();
        if (cat.isPresent()) return cat.get().minimumPercentage(templateKey);
        return plugin.templates().minimumPercentage(templateKey);
    }

    @Override
    public Optional<LobbyZoneCorners> lobbyZoneCorners(String templateKey) {
        var cat = plugin.worldsCatalog().flatMap(c -> c.lobbyZoneCorners(templateKey));
        if (cat.isPresent()) {
            var z = cat.get();
            return Optional.of(new LobbyZoneCorners(z.x1(), z.y1(), z.z1(), z.x2(), z.y2(), z.z2()));
        }
        return plugin.templates().lobbyZone(templateKey).map(z -> new LobbyZoneCorners(
                z.pos1().getX(), z.pos1().getY(), z.pos1().getZ(),
                z.pos2().getX(), z.pos2().getY(), z.pos2().getZ()));
    }

    @Override
    public Location resolveLobbySpawn(TemplateRow info, String sessionWorldName) {
        // Prefer Hub catalog (reads worlds.yml fresh from disk) even when session world is set.
        IronLegacyWorldsCatalog.LobbySpawn spawn = plugin.worldsCatalog()
                .flatMap(c -> c.lobbySpawn(info.key()))
                .orElse(null);

        String lobbyWorldName = sessionWorldName;
        if (lobbyWorldName == null || lobbyWorldName.isBlank()) {
            if (spawn != null && spawn.worldName() != null && !spawn.worldName().isBlank()) {
                lobbyWorldName = spawn.worldName();
            } else {
                String base = "worlds." + info.key() + ".lobby";
                lobbyWorldName = plugin.templates().config().getString(base + ".world", "");
            }
        }
        World w = null;
        if (lobbyWorldName != null && !lobbyWorldName.isBlank()) w = Bukkit.getWorld(lobbyWorldName);
        if (w == null && info.worldName() != null) w = Bukkit.getWorld(info.worldName());
        if (w == null && info.worldName() != null) w = Bukkit.createWorld(new WorldCreator(info.worldName()));
        if (w == null) return plugin.lobby().getLobbySpawn();

        if (spawn != null) {
            return new Location(w, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        }
        String base = "worlds." + info.key() + ".lobby";
        if (plugin.templates().config().contains(base + ".x")) {
            double x = plugin.templates().config().getDouble(base + ".x");
            double y = plugin.templates().config().getDouble(base + ".y");
            double z = plugin.templates().config().getDouble(base + ".z");
            float yaw = (float) plugin.templates().config().getDouble(base + ".yaw", 0.0);
            float pitch = (float) plugin.templates().config().getDouble(base + ".pitch", 0.0);
            return new Location(w, x, y, z, yaw, pitch);
        }
        plugin.getLogger().warning("No lobby spawn for template '" + info.key()
                + "' — falling back to world spawn. Set via /hch template lobby spawn.");
        return w.getSpawnLocation().clone().add(0.5, 0, 0.5);
    }

    @Override
    public boolean startGameOnPreparedInstance(World instanceWorld, Set<UUID> players, boolean fromLobby) {
        return plugin.game().startGameOnPreparedInstance(instanceWorld, players, fromLobby);
    }

    @Override
    public String lastGameStartError() {
        return plugin.game().getLastError();
    }

    @Override
    public void markJoinIntentCounted(Player player) {
        plugin.game().markJoinIntentCounted(player);
    }

    @Override
    public void clearJoinIntent(Player player) {
        plugin.game().clearJoinIntent(player);
    }

    @Override
    public void assignTeamsForLobbyLaunch(String templateKey, int teamSizeMax, Set<UUID> onlinePlayerIds) {
        boolean randomize = plugin.files().gameflow().getBoolean("teams.randomizeDefault", false);
        int maxSize = Math.max(1, teamSizeMax);
        if (randomize) {
            autoCreateRandomTeams(new ArrayList<>(onlinePlayerIds), maxSize);
            return;
        }

        World world = Objects.requireNonNull(plugin.getServer().getPlayer(onlinePlayerIds.stream().findFirst().orElseThrow())).getWorld();
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(world);
        for (UUID id : onlinePlayerIds) {
            //if (plugin.teams().getTeamOf(id) != null) continue;
            if(teamManagerInstance.findPlayerTeam(id) != null) continue;
            var candidate = teamManagerInstance.teams.values().stream()
                    .filter(t -> t.members.size() < maxSize)
                    .findFirst().orElse(null);
            if (candidate != null) {
                try {
                    //plugin.teams().addMember(candidate, id);
                    candidate.members.add(id);
                } catch (Exception ignored) {
                }
            }
        }

        List<UUID> unteamed = onlinePlayerIds.stream().filter(id -> teamManagerInstance.findPlayerTeam(id) == null).toList();
        if (!unteamed.isEmpty()) {
            autoCreateRandomTeams(unteamed, maxSize);
        }
    }

    @Override
    public void returnToHubOrLobby(Player player, boolean wipeInv, boolean selectCompass) {
        HubIntegrationRouting.returnToHubOrLobby(plugin, player, wipeInv, selectCompass);
    }

    @Override
    public boolean velocityEnabled() {
        return plugin.serverRole().velocityEnabled();
    }

    @Override
    public boolean hubTargetIsThisServer() {
        return HubIntegrationRouting.hubTargetIsThisServer(plugin);
    }

    @Override
    public void recordKillCredit(UUID killerId) {
        plugin.leaderboard().addKill(killerId);
    }

    @Override
    public void sendToLobbyExact(Player player, boolean wipeInv, boolean selectCompass) {
        plugin.lobby().sendToLobbyExact(player, wipeInv, selectCompass);
    }

    @Override
    public boolean lobbyHostileMobSpawns() {
        return plugin.files().lobby().getBoolean("lobby.mobSpawns.hostile", false);
    }

    @Override
    public boolean lobbyPassiveMobSpawns() {
        return plugin.files().lobby().getBoolean("lobby.mobSpawns.passive", false);
    }

    @Override
    public void setLobbyHostileMobSpawns(boolean value) {
        plugin.files().lobby().set("lobby.mobSpawns.hostile", value);
        plugin.files().saveLobby();
    }

    @Override
    public void setLobbyPassiveMobSpawns(boolean value) {
        plugin.files().lobby().set("lobby.mobSpawns.passive", value);
        plugin.files().saveLobby();
    }

    @Override
    public String lobbyPvpSwordMaterialName() {
        return plugin.files().lobby().getString("lobby.pvpSwordMaterial", "IRON_SWORD");
    }

    @Override
    public void migrateLobbyYamlDefaultsIfNeeded() {
        FileConfiguration c = plugin.files().lobby();
        String legacy = "lim" + "bo";
        boolean dirty = false;
        if (!c.contains("lobby.mobSpawns.hostile") && c.contains(legacy + ".mobSpawns.hostile")) {
            c.set("lobby.mobSpawns.hostile", c.getBoolean(legacy + ".mobSpawns.hostile"));
            dirty = true;
        }
        if (!c.contains("lobby.mobSpawns.passive") && c.contains(legacy + ".mobSpawns.passive")) {
            c.set("lobby.mobSpawns.passive", c.getBoolean(legacy + ".mobSpawns.passive"));
            dirty = true;
        }
        if (!c.contains("lobby.pvpSwordMaterial") && c.contains(legacy + ".pvpSwordMaterial")) {
            c.set("lobby.pvpSwordMaterial", c.getString(legacy + ".pvpSwordMaterial", "IRON_SWORD"));
            dirty = true;
        }
        if (!c.contains("lobby.mobSpawns.hostile")) {
            c.set("lobby.mobSpawns.hostile", false);
            dirty = true;
        }
        if (!c.contains("lobby.mobSpawns.passive")) {
            c.set("lobby.mobSpawns.passive", false);
            dirty = true;
        }
        if (!c.contains("lobby.pvpSwordMaterial")) {
            c.set("lobby.pvpSwordMaterial", "IRON_SWORD");
            dirty = true;
        }
        if (dirty) {
            plugin.files().saveLobby();
        }
    }

    @Override
    public boolean isHubLikeServerRole() {
        return plugin.serverRole().isHubLike();
    }

    @Override
    public boolean isActiveMatchParticipant(java.util.UUID playerId) {
        return playerId != null && plugin.game().isMatchParticipant(playerId);
    }

    private void autoCreateRandomTeams(List<UUID> players, int maxSize) {
        if (players.isEmpty()) return;
        List<UUID> pool = new ArrayList<>(players);
        Collections.shuffle(pool);
        World world = Objects.requireNonNull(plugin.getServer().getPlayer(pool.getFirst())).getWorld();
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(world);
        int idx = 0;
        //int nameIdx = 0;
        while (idx < pool.size()) {
            UUID owner = pool.get(idx++);
            if (teamManagerInstance.findPlayerTeam(owner) != null) continue;
            //String base = randomTeamNames.get(nameIdx % randomTeamNames.size());
            //nameIdx++;
            //String teamName = base;
            //int bump = 2;
            //while (teamManagerInstance.teams.containsKey(teamName)) teamName = base + " " + bump++;
            String teamName = TeamNamingRules.randomTeamName(teamManagerInstance);
            Color color = teamManagerInstance.getRandomUnusedColor();
            TeamManager.Team team = new TeamManager.Team(teamName, color, teamManagerInstance);
            team.members.add(owner);
            int filled = 1;
            while (idx < pool.size() && filled < maxSize) {
                UUID id = pool.get(idx++);
                if (teamManagerInstance.findPlayerTeam(id) != null) continue;
                try {
                    team.members.add(id);
                    filled++;
                } catch (Exception ignored) {
                }
            }
        }
        teamManagerInstance.sendAllFakeTeamPackets();
    }

    private void bindPartiesIntoTeams(Set<UUID> onlinePlayers, int maxSize) {
        Optional<PartyService> psOpt = plugin.hollowPartyService();
        if (psOpt.isEmpty()) {
            return;
        }
        PartyService ps = psOpt.get();
        World world = Objects.requireNonNull(plugin.getServer().getPlayer(onlinePlayers.stream().findFirst().orElseThrow())).getWorld();
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(world);
        for (UUID id : onlinePlayers) {
            Optional<PartyView> view = ps.partyForMember(id);
            if (view.isEmpty() || !view.get().isLeader(id)) {
                continue;
            }
            PartyView party = view.get();
            if (teamManagerInstance.findPlayerTeam(id) == null) {
                String name = TeamNamingRules.resolveBindTeamName(HollowCrownHubPlugin.get(), party, teamManagerInstance);
                Color color = Arrays.stream(TeamManager.colorSwatch)
                        .filter((e) -> !teamManagerInstance.takenColors.contains(e))
                        .collect(Collectors.collectingAndThen(
                                Collectors.toCollection(ArrayList::new),
                                subList -> {
                                    Collections.shuffle(subList);
                                    return subList.stream().findFirst().orElse(null);
                                }
                        ));
                if(color == null)
                    color = Color.WHITE;
                //plugin.teams().createTeam(name, color, id);
                new TeamManager.Team(name, color, teamManagerInstance).members.add(id);


            }
            var team = teamManagerInstance.findPlayerTeam(id);
            if (team == null) continue;
            for (UUID member : party.membersJoinOrder()) {
                if (!onlinePlayers.contains(member)) continue;
                if (teamManagerInstance.findPlayerTeam(member) != null) continue;
                if (team.members.size() >= maxSize) break;
                try {
                    team.members.add(member);
                } catch (Exception ignored) {
                }
            }
        }
        teamManagerInstance.sendAllFakeTeamPackets();
    }
}
