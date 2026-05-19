package com.hollowcrown.api.world;

import java.util.Optional;
import java.util.UUID;

/**
 * Registered by Iron Legacy (or another minigame) so environmental hazards can tag traps with template scope.
 */
public interface GameWorldScopeResolver {

    Optional<GameWorldScope> resolve(UUID worldUuid, String worldName);
}
