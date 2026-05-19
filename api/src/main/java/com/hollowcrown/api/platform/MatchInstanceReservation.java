package com.hollowcrown.api.platform;

import java.util.Objects;
import java.util.UUID;

/**
 * Result of Hub-provisioned match world creation on the same JVM as the minigame (optional path).
 */
public record MatchInstanceReservation(
        String templateKey,
        String worldName,
        UUID worldUuid
) {
    public MatchInstanceReservation {
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(worldUuid, "worldUuid");
        if (templateKey.isBlank() || worldName.isBlank()) {
            throw new IllegalArgumentException("templateKey and worldName must be non-blank");
        }
    }
}
