package com.hollowcrown.api.ids;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies one live lobby instance (e.g. one {@code Worldname-L#} world).
 */
public record LobbyInstanceId(UUID worldUuid, int lobbyIndex) {

    public LobbyInstanceId {
        Objects.requireNonNull(worldUuid, "worldUuid");
        if (lobbyIndex < 1) {
            throw new IllegalArgumentException("lobbyIndex must be >= 1");
        }
    }
}
