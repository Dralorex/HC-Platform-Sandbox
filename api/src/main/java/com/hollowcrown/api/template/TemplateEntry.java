package com.hollowcrown.api.template;

import java.util.Objects;

/**
 * Read-only row from Hub template configuration ({@code templates.yml}).
 * Minigames use this type instead of Hub implementation classes.
 *
 * <p>{@link #mechanicId()} may be blank when the Hub listing exists but no mechanic
 * has been chosen yet (publish / join flows require one later).</p>
 */
public final class TemplateEntry {

    private final String templateKey;
    private final String mechanicId;
    private final String displayName;
    /** Source world folder name on the Hub server; may be empty until configured. */
    private final String sourceWorldName;
    private final int queueMaxUnitSize;
    private final boolean queueSoloOnly;
    private final boolean queueSoloAutoMerge;
    /** When true, join GUI queues first instead of direct oldest-lobby join. */
    private final boolean joinQueueFirst;

    public TemplateEntry(
            String templateKey,
            String mechanicId,
            String displayName,
            String sourceWorldName,
            int queueMaxUnitSize,
            boolean queueSoloOnly,
            boolean queueSoloAutoMerge,
            boolean joinQueueFirst
    ) {
        this.templateKey = Objects.requireNonNull(templateKey, "templateKey").trim();
        this.mechanicId = Objects.requireNonNull(mechanicId, "mechanicId").trim();
        this.displayName = displayName == null ? "" : displayName.trim();
        this.sourceWorldName = sourceWorldName == null ? "" : sourceWorldName.trim();
        if (this.templateKey.isEmpty()) {
            throw new IllegalArgumentException("templateKey empty");
        }
        // mechanicId may be empty until Hub admin assigns one (publish gate / join menus).
        int cap = queueMaxUnitSize;
        if (cap < 1) {
            cap = 1;
        }
        if (cap > 1024) {
            cap = 1024;
        }
        this.queueMaxUnitSize = cap;
        this.queueSoloOnly = queueSoloOnly;
        this.queueSoloAutoMerge = queueSoloAutoMerge;
        this.joinQueueFirst = joinQueueFirst;
    }

    /** Defaults queue policy to max unit 8, parties allowed, solo merge off, direct join. */
    public TemplateEntry(String templateKey, String mechanicId, String displayName, String sourceWorldName) {
        this(templateKey, mechanicId, displayName, sourceWorldName, 8, false, false, false);
    }

    public String templateKey() {
        return templateKey;
    }

    public String mechanicId() {
        return mechanicId;
    }

    /** May be empty; use {@link #effectiveDisplayName()} for UI. */
    public String displayName() {
        return displayName;
    }

    /** Empty until Hub admin runs template setsource. */
    public String sourceWorldName() {
        return sourceWorldName;
    }

    /** Max players per queue unit (party cap for enqueue). */
    public int queueMaxUnitSize() {
        return queueMaxUnitSize;
    }

    /** When true, only single-player queue units unless bypass permission. */
    public boolean queueSoloOnly() {
        return queueSoloOnly;
    }

    /** When true, Hub may merge consecutive solos into one unit up to {@link #queueMaxUnitSize}. */
    public boolean queueSoloAutoMerge() {
        return queueSoloAutoMerge;
    }

    public boolean joinQueueFirst() {
        return joinQueueFirst;
    }

    public String effectiveDisplayName() {
        return displayName.isEmpty() ? templateKey : displayName;
    }

    public boolean hasSourceWorld() {
        return !sourceWorldName.isEmpty();
    }
}
