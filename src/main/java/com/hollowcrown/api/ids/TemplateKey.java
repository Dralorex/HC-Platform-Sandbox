package com.hollowcrown.api.ids;

import java.util.Objects;

public record TemplateKey(String value) {

    public TemplateKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("template key blank");
        }
    }

    public static TemplateKey of(String value) {
        return new TemplateKey(value.trim());
    }
}
