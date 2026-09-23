package com.hollowcrown.hungergames.managers;

import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Routes hub returns through Hollow Crown Hub spawn ({@code /hch sethub}) + shard offsets.
 * Physical hub protections and hub hotbar live in HollowCrownHub ({@link HubWorldApi}).
 */
public final class LobbyManager {

    private final HungerGamesPlugin plugin;

    public LobbyManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<HubWorldApi> hubWorld() {
        return plugin.hubWorldApi();
    }

    public boolean hasLobby() {
        return plugin.hollowGamePlatform() != null && plugin.hollowGamePlatform().getHubSpawn().isPresent();
    }

    public Location getLobbySpawn() {
        return hubWorld().flatMap(HubWorldApi::hubCenterLocation).orElse(null);
    }

    public void sendToLobby(Player p) {
        sendToLobby(p, true, true);
    }

    public void sendToLobby(Player p, boolean wipeInv, boolean selectCompass) {
        Location center = getLobbySpawn();
        if (center == null) return;
        Location spawn = resolveShardSpawn(p, center);
        p.teleport(spawn);
        plugin.packs().applyIfEnabled(p);
        hubWorld().ifPresent(h -> h.applyHubUtilityLoadout(p, wipeInv, selectCompass));
    }

    public void sendToLobbyExact(Player p, boolean wipeInv, boolean selectCompass) {
        plugin.templates().rememberResumeSpotLeavingTemplate(p);
        plugin.templates().saveEditorInventoryIfTemplate(p);
        if (hubWorld().map(h -> h.sendToHubSpawn(p)).orElse(false)) {
            plugin.packs().applyIfEnabled(p);
            plugin.templates().restoreLobbyAfterLeavingTemplate(p);
            return;
        }
        plugin.limbo().leaveLimbo(p);
        Location center = getLobbySpawn();
        if (center == null) {
            p.sendMessage(Component.text("Hub spawn is not set. Ask an admin to run /hch sethub.", NamedTextColor.RED));
            return;
        }
        Location spawn = resolveShardSpawn(p, center);
        p.teleport(HollowCrownHubPlugin.safeHubStandLocation(spawn));
        plugin.packs().applyIfEnabled(p);
        hubWorld().ifPresent(h -> h.applyHubUtilityLoadout(p, wipeInv, selectCompass));
        plugin.templates().restoreLobbyAfterLeavingTemplate(p);
    }

    private Location resolveShardSpawn(Player p, Location center) {
        int shard = plugin.lobbyShards().assignPlayer(p);
        if (shard < 0) {
            p.sendMessage(Component.text("All hub shards are currently full. Try again shortly.", NamedTextColor.RED));
            return HollowCrownHubPlugin.safeHubStandLocation(center);
        }
        int total = Math.max(1, plugin.lobbyShards().shardCount());
        double spacing = Math.max(8.0, plugin.files().gameflow().getDouble("lobbies.shardSpacing", 48.0));
        if (total == 1 || shard == 0) {
            return HollowCrownHubPlugin.safeHubStandLocation(center);
        }
        int ringIndex = shard - 1;
        int ringSlots = Math.max(1, total - 1);
        double angle = (2 * Math.PI * ringIndex) / ringSlots;
        Location out = center.clone();
        out.setX(center.getX() + (Math.cos(angle) * spacing));
        out.setZ(center.getZ() + (Math.sin(angle) * spacing));
        return HollowCrownHubPlugin.safeHubStandLocation(out);
    }

    public void ensureLobbyLoadout(Player p, boolean selectCompass) {
        hubWorld().ifPresent(h -> {
            if (h.isInHubProtection(p)) {
                h.applyHubUtilityLoadout(p, false, selectCompass);
            }
        });
    }

    public void prepareForLimboJoin(Player p) {
        plugin.limbo().prepareForLimboJoin(p);
    }

    public void applyLimboLoadout(Player p) {
        plugin.limbo().applyLimboLoadout(p);
    }

    public void applyLobbyAfterTemplateExitWithoutPriorBuildMode(Player p) {
        hubWorld().ifPresent(h -> h.applySurvivalHubLoadoutAfterTemplate(p));
    }

    public void setBuildMode(Player p, boolean on) {
        hubWorld().ifPresent(h -> h.setHubBuildMode(p, on));
    }

    public boolean isBuildMode(Player p) {
        return hubWorld().map(h -> h.isHubBuildMode(p)).orElse(false);
    }

    public boolean isInLobby(Player p) {
        return hubWorld().map(h -> h.isInHubProtection(p)).orElse(false);
    }

    public void setLobbySize(double size) {
        hubWorld().ifPresent(h -> h.setProtectionRadiusBlocks(size));
    }

    public double getLobbySize() {
        return hubWorld().map(HubWorldApi::protectionRadiusBlocks).orElse(64.0);
    }

    /** Limbo worlds only (pre-match service registered by HollowCrownHub). */
    public boolean allowHostileMobSpawns() {
        return plugin.limbo().allowHostileMobSpawns();
    }

    /** Limbo worlds only (pre-match service registered by HollowCrownHub). */
    public boolean allowPassiveMobSpawns() {
        return plugin.limbo().allowPassiveMobSpawns();
    }

    public void setAllowHostileMobSpawns(boolean value) {
        plugin.limbo().setAllowHostileMobSpawns(value);
    }

    public void setAllowPassiveMobSpawns(boolean value) {
        plugin.limbo().setAllowPassiveMobSpawns(value);
    }

    public void givePvpPadItem(Player p) {
        hubWorld().ifPresent(h -> h.givePvpPadItem(p));
    }
}
