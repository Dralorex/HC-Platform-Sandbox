package com.hollowcrown.api.platform;

import java.util.Objects;
import java.util.UUID;

/**
 * Global hub rally point (UUID world id for proxy-safe sync).
 */
public record HubSpawn(UUID worldUuid, double x, double y, double z, float yaw, float pitch) {

    public HubSpawn {
        Objects.requireNonNull(worldUuid, "worldUuid");
    }
}
