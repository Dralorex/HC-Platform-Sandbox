package com.hollowcrown.api.integration;

/**
 * Snapshot of one Iron Legacy template row for Hub admin GUIs (mirrors worlds.yml + merged Hub keys).
 */
public record IronLegacyTemplateRow(
        String key,
        String worldName,
        String displayName,
        String status,
        boolean enabled,
        int maxPlayers,
        int teamSizeMax,
        String iconKey,
        int version
) {
}
