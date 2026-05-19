package com.hollowcrown.api.minigame;

import com.hollowcrown.api.HollowCrownApiVersion;
import com.hollowcrown.api.ids.MechanicId;

import java.util.Objects;

/**
 * Canonical id for a Hollow Crown minigame: ties Hub template rows, {@link com.hollowcrown.api.platform.GamePlatform}
 * mechanic registration, and match provisioning to the same string.
 *
 * <p><b>Wiring checklist</b> (see package {@link com.hollowcrown.api.minigame}):
 * <ul>
 *   <li>{@code plugin.yml}: {@code depend: [HC-Foundation, HollowCrownHub]} (or your chosen stack ids).</li>
 *   <li>Hub template / storage: {@code mechanicId} must equal {@link #mechanicId()} {@link MechanicId#value()}.</li>
 *   <li>On enable: resolve {@link com.hollowcrown.api.platform.GamePlatform} from the ServicesManager, then
 *       {@link MinigameBootstrap#registerMechanic}.</li>
 *   <li>On disable: {@link MinigameBootstrap#unregisterMechanic}.</li>
 * </ul>
 */
public record MinigamePluginKey(
        MechanicId mechanicId,
        String displayName,
        String foundationPluginName,
        String hubPluginName,
        int minimumApiSchema
) {

    public MinigamePluginKey {
        Objects.requireNonNull(mechanicId, "mechanicId");
        if (foundationPluginName == null || foundationPluginName.isBlank()) {
            throw new IllegalArgumentException("foundationPluginName blank");
        }
        if (hubPluginName == null || hubPluginName.isBlank()) {
            throw new IllegalArgumentException("hubPluginName blank");
        }
        if (minimumApiSchema < 1) {
            throw new IllegalArgumentException("minimumApiSchema");
        }
        displayName = displayName == null || displayName.isBlank()
                ? mechanicId.value()
                : displayName.trim();
    }

    /**
     * Standard Paper stack: {@code HC-Foundation} + {@code HollowCrownHub}, API schema at least current
     * {@link HollowCrownApiVersion#SCHEMA}.
     */
    public static MinigamePluginKey hollowCrown(String mechanicId, String displayName) {
        return new MinigamePluginKey(
                MechanicId.of(mechanicId),
                displayName,
                "HC-Foundation",
                "HollowCrownHub",
                HollowCrownApiVersion.SCHEMA
        );
    }

    /** Same as {@link MechanicId#value()} — use in Hub YAML / SQL template rows. */
    public String templateMechanicId() {
        return mechanicId.value();
    }
}
