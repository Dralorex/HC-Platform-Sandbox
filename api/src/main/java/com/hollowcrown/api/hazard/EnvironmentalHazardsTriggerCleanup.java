package com.hollowcrown.api.hazard;

import java.util.UUID;

/**
 * Registered by HC-Environmental-Hazards; Iron Legacy calls this when templates or match worlds go away.
 */
public interface EnvironmentalHazardsTriggerCleanup {

    void removeByWorldUuid(UUID worldUuid);

    void removeByWorldName(String worldName);

    void removeByTemplateKey(String templateKeyLowercase);
}
