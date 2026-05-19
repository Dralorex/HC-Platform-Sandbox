package com.hollowcrown.api.mechanic;

import com.hollowcrown.api.ids.LobbyInstanceId;
import com.hollowcrown.api.ids.TemplateKey;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot Hub passes to a minigame on hot paths (UUID-only for proxy sync).
 */
public record MechanicContext(
        TemplateKey templateKey,
        LobbyInstanceId lobbyInstanceId,
        UUID lobbyWorldUuid
) {
    public MechanicContext {
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(lobbyInstanceId, "lobbyInstanceId");
        Objects.requireNonNull(lobbyWorldUuid, "lobbyWorldUuid");
    }
}
