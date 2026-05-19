package com.hollowcrown.api.mechanic;

/**
 * Minigame SPI: Hub calls these on the main thread unless documented otherwise.
 * Expand as template/lobby/match lifecycle is migrated out of individual plugins.
 */
public interface MinigameMechanic {

    default void onLobbyInstanceCreated(MechanicContext ctx) {
    }

    default void onLobbyInstanceReleased(MechanicContext ctx) {
    }
}
