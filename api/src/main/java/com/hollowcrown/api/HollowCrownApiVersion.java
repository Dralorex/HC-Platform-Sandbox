package com.hollowcrown.api;

/**
 * Wire format / SPI compatibility. Bump when breaking {@link com.hollowcrown.api.platform.GamePlatform} or mechanic contracts.
 * New {@link com.hollowcrown.api.template.TemplateRegistry} services do not require a bump.
 */
public final class HollowCrownApiVersion {

    public static final int SCHEMA = 2;

    private HollowCrownApiVersion() {
    }
}
