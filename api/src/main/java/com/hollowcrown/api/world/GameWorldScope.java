package com.hollowcrown.api.world;

import java.util.Objects;

/**
 * Optional classification of a Bukkit world for cross-plugin features (hazards, hazards cleanup, etc.).
 */
public record GameWorldScope(String templateKey, String scopeKind, String worldName) {

    public static final String SCOPE_LOBBY_INSTANCE = "LOBBY_INSTANCE";
    public static final String SCOPE_MATCH_INSTANCE = "MATCH_INSTANCE";
    public static final String SCOPE_TEMPLATE_EDITOR = "TEMPLATE_EDITOR";

    public GameWorldScope {
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(scopeKind, "scopeKind");
        Objects.requireNonNull(worldName, "worldName");
        if (templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey blank");
        }
    }
}
