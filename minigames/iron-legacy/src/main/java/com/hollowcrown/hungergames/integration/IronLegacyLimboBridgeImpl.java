package com.hollowcrown.hungergames.integration;

import com.hollowcrown.api.integration.IronLegacyLimboBridge;
import com.hollowcrown.api.integration.IronLegacyWorldsCatalog;
import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.TeamNamingRules;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Engine hooks for Hub-owned pre-match limbo (templates, match start, teams, lobby routing).
 */
public final class IronLegacyLimboBridgeImpl implements IronLegacyLimboBridge {

    private final HungerGamesPlugin plugin;
    private final List<String> randomTeamNames = List.of(
            "Ravens", "Sentinels", "Valkyries", "Outriders", "Wardens",
            "Nomads", "Phantoms", "Cyclones", "Titans", "Gladiators"
    );

    public IronLegacyLimboBridgeImpl(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        migrateLimboLobbyYamlDefaultsIfNeeded();
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
    public Optional<LimboZoneCorners> limboZoneCorners(String templateKey) {
        var cat = plugin.worldsCatalog().flatMap(c -> c.limboZoneCorners(templateKey));
        if (cat.isPresent()) {
            var z = cat.get();
            return Optional.of(new LimboZoneCorners(z.x1(), z.y1(), z.z1(), z.x2(), z.y2(), z.z2()));
        }
        return plugin.templates().limboZone(templateKey).map(z -> new LimboZoneCorners(
                z.pos1().getX(), z.pos1().getY(), z.pos1().getZ(),
                z.pos2().getX(), z.pos2().getY(), z.pos2().getZ()));
    }

    @Override
    public Location resolveLimboSpawn(TemplateRow info, String sessionWorldName) {
        // Prefer Hub catalog (reads worlds.yml fresh from disk) even when session world is set.
        IronLegacyWorldsCatalog.LimboSpawn spawn = plugin.worldsCatalog()
                .flatMap(c -> c.limboSpawn(info.key()))
                .orElse(null);

        String limboWorldName = sessionWorldName;
        if (limboWorldName == null || limboWorldName.isBlank()) {
            if (spawn != null && spawn.worldName() != null && !spawn.worldName().isBlank()) {
                limboWorldName = spawn.worldName();
            } else {
                String base = "worlds." + info.key() + ".limbo";
                limboWorldName = plugin.templates().config().getString(base + ".world", "");
            }
        }
        World w = null;
        if (limboWorldName != null && !limboWorldName.isBlank()) w = Bukkit.getWorld(limboWorldName);
        if (w == null && info.worldName() != null) w = Bukkit.getWorld(info.worldName());
        if (w == null && info.worldName() != null) w = Bukkit.createWorld(new WorldCreator(info.worldName()));
        if (w == null) return plugin.lobby().getLobbySpawn();

        if (spawn != null) {
            return new Location(w, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        }
        String base = "worlds." + info.key() + ".limbo";
        if (plugin.templates().config().contains(base + ".x")) {
            double x = plugin.templates().config().getDouble(base + ".x");
            double y = plugin.templates().config().getDouble(base + ".y");
            double z = plugin.templates().config().getDouble(base + ".z");
            float yaw = (float) plugin.templates().config().getDouble(base + ".yaw", 0.0);
            float pitch = (float) plugin.templates().config().getDouble(base + ".pitch", 0.0);
            return new Location(w, x, y, z, yaw, pitch);
        }
        plugin.getLogger().warning("No limbo spawn for template '" + info.key()
                + "' — falling back to world spawn. Set via /hch template limbo spawn.");
        return w.getSpawnLocation().clone().add(0.5, 0, 0.5);
    }

    @Override
    public boolean startGameOnPreparedInstance(World instanceWorld, Set<UUID> players, boolean fromLimbo) {
        return plugin.game().startGameOnPreparedInstance(instanceWorld, players, fromLimbo);
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
    public void assignTeamsForLimboLaunch(String templateKey, int teamSizeMax, Set<UUID> onlinePlayerIds) {
        boolean randomize = plugin.files().gameflow().getBoolean("teams.randomizeDefault", false);
        int maxSize = Math.max(1, teamSizeMax);
        if (randomize) {
            autoCreateRandomTeams(new ArrayList<>(onlinePlayerIds), maxSize);
            return;
        }

        for (UUID id : onlinePlayerIds) {
            if (plugin.teams().getTeamOf(id) != null) continue;
            var candidate = plugin.teams().getTeams().stream()
                    .filter(t -> t.getMembers().size() < maxSize)
                    .findFirst().orElse(null);
            if (candidate != null) {
                try {
                    plugin.teams().addMember(candidate, id);
                } catch (Exception ignored) {
                }
            }
        }

        List<UUID> unteamed = onlinePlayerIds.stream().filter(id -> plugin.teams().getTeamOf(id) == null).toList();
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
    public boolean limboHostileMobSpawns() {
        return plugin.files().lobby().getBoolean("limbo.mobSpawns.hostile", false);
    }

    @Override
    public boolean limboPassiveMobSpawns() {
        return plugin.files().lobby().getBoolean("limbo.mobSpawns.passive", false);
    }

    @Override
    public void setLimboHostileMobSpawns(boolean value) {
        plugin.files().lobby().set("limbo.mobSpawns.hostile", value);
        plugin.files().saveLobby();
    }

    @Override
    public void setLimboPassiveMobSpawns(boolean value) {
        plugin.files().lobby().set("limbo.mobSpawns.passive", value);
        plugin.files().saveLobby();
    }

    @Override
    public String limboPvpSwordMaterialName() {
        return plugin.files().lobby().getString("limbo.pvpSwordMaterial", "IRON_SWORD");
    }

    @Override
    public void migrateLimboLobbyYamlDefaultsIfNeeded() {
        FileConfiguration c = plugin.files().lobby();
        boolean dirty = false;
        if (!c.contains("limbo.mobSpawns.hostile") && c.contains("lobby.mobSpawns.hostile")) {
            c.set("limbo.mobSpawns.hostile", c.getBoolean("lobby.mobSpawns.hostile"));
            dirty = true;
        }
        if (!c.contains("limbo.mobSpawns.passive") && c.contains("lobby.mobSpawns.passive")) {
            c.set("limbo.mobSpawns.passive", c.getBoolean("lobby.mobSpawns.passive"));
            dirty = true;
        }
        if (!c.contains("limbo.pvpSwordMaterial") && c.contains("lobby.pvpSwordMaterial")) {
            c.set("limbo.pvpSwordMaterial", c.getString("lobby.pvpSwordMaterial", "IRON_SWORD"));
            dirty = true;
        }
        if (!c.contains("limbo.mobSpawns.hostile")) {
            c.set("limbo.mobSpawns.hostile", false);
            dirty = true;
        }
        if (!c.contains("limbo.mobSpawns.passive")) {
            c.set("limbo.mobSpawns.passive", false);
            dirty = true;
        }
        if (!c.contains("limbo.pvpSwordMaterial")) {
            c.set("limbo.pvpSwordMaterial", "IRON_SWORD");
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
        int idx = 0;
        int nameIdx = 0;
        while (idx < pool.size()) {
            UUID owner = pool.get(idx++);
            if (plugin.teams().getTeamOf(owner) != null) continue;
            String base = randomTeamNames.get(nameIdx % randomTeamNames.size());
            nameIdx++;
            String teamName = base;
            int bump = 2;
            while (plugin.teams().nameTaken(teamName)) teamName = base + " " + bump++;
            ChatColor color = ChatColor.values()[Math.floorMod(teamName.hashCode(), ChatColor.values().length)];
            if (!color.isColor()) color = ChatColor.WHITE;
            var team = plugin.teams().createTeam(teamName, color, owner);
            int filled = 1;
            while (idx < pool.size() && filled < maxSize) {
                UUID id = pool.get(idx++);
                if (plugin.teams().getTeamOf(id) != null) continue;
                try {
                    plugin.teams().addMember(team, id);
                    filled++;
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void bindPartiesIntoTeams(Set<UUID> onlinePlayers, int maxSize) {
        Optional<PartyService> psOpt = plugin.hollowPartyService();
        if (psOpt.isEmpty()) {
            return;
        }
        PartyService ps = psOpt.get();
        for (UUID id : onlinePlayers) {
            Optional<PartyView> view = ps.partyForMember(id);
            if (view.isEmpty() || !view.get().isLeader(id)) {
                continue;
            }
            PartyView party = view.get();
            if (plugin.teams().getTeamOf(id) == null) {
                String name = TeamNamingRules.resolveBindTeamName(plugin, party, plugin.teams());
                ChatColor color = ChatColor.values()[Math.floorMod(name.hashCode(), ChatColor.values().length)];
                if (!color.isColor()) color = ChatColor.WHITE;
                plugin.teams().createTeam(name, color, id);
            }
            var team = plugin.teams().getTeamOf(id);
            if (team == null) continue;
            for (UUID member : party.membersJoinOrder()) {
                if (!onlinePlayers.contains(member)) continue;
                if (plugin.teams().getTeamOf(member) != null) continue;
                if (team.getMembers().size() >= maxSize) break;
                try {
                    plugin.teams().addMember(team, member);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
