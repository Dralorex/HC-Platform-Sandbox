package com.hollowcrown.api.chat;

import java.util.Objects;

/**
 * Minimal team display data for chat mods; no Adventure dependency.
 */
public record TeamChatProfile(String legacyPrefix, String legacyColorCode) {

    public TeamChatProfile {
        Objects.requireNonNull(legacyPrefix, "legacyPrefix");
        Objects.requireNonNull(legacyColorCode, "legacyColorCode");
    }
}
