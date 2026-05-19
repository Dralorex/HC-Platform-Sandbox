package com.hollowcrown.api.ids;

import java.util.Objects;

/**
 * Stable id for a minigame implementation (e.g. {@code iron-legacy}).
 */
public record MechanicId(String value) {

    public MechanicId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("mechanic id blank");
        }
    }

    public static MechanicId of(String value) {
        return new MechanicId(value.trim());
    }
}
