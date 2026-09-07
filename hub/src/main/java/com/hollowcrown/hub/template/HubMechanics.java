package com.hollowcrown.hub.template;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Well-known mechanic ids for Hub template listings and lobby compass grouping.
 */
public final class HubMechanics {

    /** Worlds with no minigame plugin — lobby/explore only unless another system handles match flow. */
    public static final String VANILLA = "vanilla";

    private HubMechanics() {
    }

    public static boolean isVanilla(String mechanicId) {
        return mechanicId != null && VANILLA.equalsIgnoreCase(mechanicId.trim());
    }

    public static boolean hasMechanicSelected(String mechanicId) {
        return mechanicId != null && !mechanicId.isBlank();
    }

    /** Display label for compass tabs and messages. */
    public static String displayLabel(String mechanicId) {
        if (!hasMechanicSelected(mechanicId)) {
            return "Unassigned";
        }
        if (isVanilla(mechanicId)) {
            return "Vanilla";
        }
        return mechanicId;
    }

    /**
     * Sorted mechanic ids present on at least one Hub template row, always including {@link #VANILLA}.
     */
    public static List<String> sortedMechanicIds(Iterable<HubTemplateEntry> templates) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(VANILLA);
        for (HubTemplateEntry e : templates) {
            if (e == null) {
                continue;
            }
            if (!hasMechanicSelected(e.mechanicId())) {
                continue;
            }
            ids.add(e.mechanicId().trim().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(ids);
    }
}
