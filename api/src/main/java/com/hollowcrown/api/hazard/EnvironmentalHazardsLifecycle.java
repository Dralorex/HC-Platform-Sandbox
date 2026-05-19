package com.hollowcrown.api.hazard;

/**
 * Optional hooks from Iron Legacy into environmental hazards (e.g. export on publish).
 */
public interface EnvironmentalHazardsLifecycle {

    /**
     * Called after Iron Legacy successfully publishes a template (snapshot taken, status PUBLISHED).
     * Implementations may export hazard trigger metadata for that template key.
     */
    void onIronLegacyTemplatePublished(String templateKeyLowercase);
}
