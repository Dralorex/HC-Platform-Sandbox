package com.hollowcrown.hub.lobby;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** PvP opt-in state shared between {@link HubPreMatchLobbyService} and {@link HubPreMatchLobbyArenaListener}. */
public final class HubLobbyArenaState {

    private final Set<UUID> pvpActive = new HashSet<>();
    private final Map<UUID, Integer> activateCountdown = new HashMap<>();
    private final Map<UUID, Integer> deactivateCountdown = new HashMap<>();
    private final Set<UUID> pendingDeactivateOnDeath = new HashSet<>();
    private final Map<UUID, Long> pvpInvulnerableUntil = new HashMap<>();

    public void prepareForLobbyJoin(Player p) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        pvpActive.remove(id);
        activateCountdown.remove(id);
        deactivateCountdown.remove(id);
        pendingDeactivateOnDeath.remove(id);
        p.getInventory().setHeldItemSlot(4);
    }

    public Set<UUID> pvpActive() {
        return pvpActive;
    }

    public Map<UUID, Integer> activateCountdown() {
        return activateCountdown;
    }

    public Map<UUID, Integer> deactivateCountdown() {
        return deactivateCountdown;
    }

    public Set<UUID> pendingDeactivateOnDeath() {
        return pendingDeactivateOnDeath;
    }

    public Map<UUID, Long> pvpInvulnerableUntil() {
        return pvpInvulnerableUntil;
    }
}
