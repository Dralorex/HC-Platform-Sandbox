package com.hollowcrown.hub.lobby;

public enum LobbyInstanceState {
    /** World should be loaded and usable. */
    OPEN,
    /** Teardown failed; row kept for OP visibility until /hch lobby forget. */
    BROKEN
}
