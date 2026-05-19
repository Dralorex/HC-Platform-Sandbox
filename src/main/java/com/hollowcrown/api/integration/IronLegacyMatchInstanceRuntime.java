package com.hollowcrown.api.integration;

import java.util.Optional;

/** Hub-owned runtime match instance lifecycle for Iron Legacy. */
public interface IronLegacyMatchInstanceRuntime {

    record MatchInstance(
            String id,
            String templateKey,
            String templateWorldName,
            String worldName,
            long createdAt
    ) {}

    Optional<MatchInstance> createMatchInstance(String templateKey);

    boolean destroyMatchInstanceByWorld(String worldName);
}

