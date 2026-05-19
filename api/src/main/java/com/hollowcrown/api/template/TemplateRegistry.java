package com.hollowcrown.api.template;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Hub-published read-only view of game templates. HollowCrownHub registers an
 * implementation in the server's plugin {@code ServicesManager}.
 */
public interface TemplateRegistry {

    /**
     * All templates in Hub-defined order (typically insertion order in {@code templates.yml}).
     */
    List<TemplateEntry> list();

    /**
     * Lookup by template key; matching is case-insensitive on the normalized key.
     */
    default Optional<TemplateEntry> findByTemplateKey(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            return Optional.empty();
        }
        String want = templateKey.trim().toLowerCase(Locale.ROOT);
        for (TemplateEntry e : list()) {
            if (e.templateKey().equalsIgnoreCase(want)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
