package com.hollowcrown.api.platform;

import com.hollowcrown.api.HollowCrownApiVersion;
import com.hollowcrown.api.ids.MechanicId;
import com.hollowcrown.api.mechanic.MinigameMechanic;

import java.util.Optional;
import java.util.Set;

/**
 * Hub-owned entry point for minigames. Register via {@link #registerMechanic(MechanicId, MinigameMechanic)}.
 */
public interface GamePlatform {

    static int apiSchema() {
        return HollowCrownApiVersion.SCHEMA;
    }

    void registerMechanic(MechanicId id, MinigameMechanic mechanic);

    void unregisterMechanic(MechanicId id);

    MinigameMechanic getMechanic(MechanicId id);

    /**
     * Configured global hub spawn (e.g. after {@code /hch sethub}).
     */
    Optional<HubSpawn> getHubSpawn();

    /**
     * Persist hub spawn to Hub storage (YAML/SQL later).
     */
    void setHubSpawn(HubSpawn spawn);

    /**
     * Remove hub spawn from config (admin).
     */
    void clearHubSpawn();

    /**
     * Registered minigame mechanic ids (for admin/debug).
     */
    default Set<String> registeredMechanicIdStrings() {
        return Set.of();
    }

    /**
     * When Hub and the minigame share this server, Hub may create a fresh match world (folder copy + load)
     * from its template registry. Empty when unsupported, unknown template, or wrong mechanic.
     */
    default Optional<MatchInstanceReservation> createMatchWorld(String templateKey) {
        return Optional.empty();
    }
}
