package com.hollowcrown.hub.template;

import java.util.Objects;

/**
 * One row in Hub {@code templates.yml}: game listing key bound to a minigame mechanic.
 */
public final class HubTemplateEntry {

    private final String templateKey;
    private final String mechanicId;
    private final String displayName;
    /** Folder name under {@code server/} to copy when creating lobby instances (e.g. {@code hg_main}). */
    private final String sourceWorldName;
    /** Max players in one queue unit (party size cap). */
    private final int queueMaxUnitSize;
    /**
     * Solo-only queue: multi-member parties cannot enqueue unless {@code hollowcrownhub.queue.bypass}.
     * Maps roadmap “maxTeams=1 / no teams” style gate.
     */
    private final boolean queueSoloOnly;
    /** When true, consecutive solos merge into one unit up to {@link #queueMaxUnitSize}. */
    private final boolean queueSoloAutoMerge;
    /**
     * When true, {@code /hch join} left-click uses {@link com.hollowcrown.api.queue.MatchQueueService#enqueue}
     * instead of direct oldest-lobby join; right-click still opens the lobby picker.
     */
    private final boolean joinQueueFirst;

    public HubTemplateEntry(
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
        if (this.mechanicId.isEmpty()) {
            throw new IllegalArgumentException("mechanicId empty");
        }
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

    /** Legacy 4-arg ctor defaults queue policy: max 8, parties allowed, no solo merge, direct join. */
    public HubTemplateEntry(String templateKey, String mechanicId, String displayName, String sourceWorldName) {
        this(templateKey, mechanicId, displayName, sourceWorldName, 8, false, false, false);
    }

    public String templateKey() {
        return templateKey;
    }

    public String mechanicId() {
        return mechanicId;
    }

    /** May be empty; fall back to templateKey in UI. */
    public String displayName() {
        return displayName;
    }

    /** Empty until set via {@code /hch template setsource}. */
    public String sourceWorldName() {
        return sourceWorldName;
    }

    public int queueMaxUnitSize() {
        return queueMaxUnitSize;
    }

    public boolean queueSoloOnly() {
        return queueSoloOnly;
    }

    public boolean queueSoloAutoMerge() {
        return queueSoloAutoMerge;
    }

    public boolean joinQueueFirst() {
        return joinQueueFirst;
    }

    public String effectiveDisplayName() {
        return displayName.isEmpty() ? templateKey : displayName;
    }

    public HubTemplateEntry withSourceWorldName(String source) {
        String src = source == null ? "" : source.trim();
        return new HubTemplateEntry(templateKey, mechanicId, displayName, src,
                queueMaxUnitSize, queueSoloOnly, queueSoloAutoMerge, joinQueueFirst);
    }

    public HubTemplateEntry withQueuePolicy(int maxUnitSize, boolean soloOnly, boolean soloAutoMerge) {
        return new HubTemplateEntry(templateKey, mechanicId, displayName, sourceWorldName,
                maxUnitSize, soloOnly, soloAutoMerge, joinQueueFirst);
    }

    public HubTemplateEntry withJoinQueueFirst(boolean joinQueueFirst) {
        return new HubTemplateEntry(templateKey, mechanicId, displayName, sourceWorldName,
                queueMaxUnitSize, queueSoloOnly, queueSoloAutoMerge, joinQueueFirst);
    }
}
