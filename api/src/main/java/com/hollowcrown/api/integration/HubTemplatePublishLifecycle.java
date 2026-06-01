package com.hollowcrown.api.integration;

/**
 * Hollow Crown Hub hook when an Iron Legacy template is published (snapshot + worlds.yml).
 * Registered by HollowCrownHub; optional for HCIronLegacy.
 */
public interface HubTemplatePublishLifecycle {

    void onTemplatePublished(String templateKey);
}
