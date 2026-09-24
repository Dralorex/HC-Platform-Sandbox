package com.hollowcrown.hub.world;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Shared hub/lobby combat vitals: full health, modest saturation, no absorption.
 * Saturation {@code 6} on the 0–20 scale equals {@code 3} on a 0–10 scale.
 */
public final class HubCombatVitals {

    /** Food saturation after PvP off / death / match countdown (6/20 ≡ 3/10). */
    public static final float RESET_SATURATION = 6.0f;
    public static final int RESET_FOOD_LEVEL = 20;

    private HubCombatVitals() {
    }

    /** Full health, clear absorption/regen effects, food full, saturation {@link #RESET_SATURATION}. */
    public static void resetAfterCombat(Player p) {
        if (p == null || !p.isOnline()) return;
        clearAbsorptionAndRegen(p);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max != null ? max.getValue() : 20.0;
        p.setHealth(Math.min(maxHp, Math.max(1.0, maxHp)));
        p.setFoodLevel(RESET_FOOD_LEVEL);
        p.setSaturation(RESET_SATURATION);
        p.setExhaustion(0f);
    }

    public static void clearAbsorptionAndRegen(Player p) {
        if (p == null || !p.isOnline()) return;
        p.setAbsorptionAmount(0f);
        p.removePotionEffect(PotionEffectType.ABSORPTION);
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.HEALTH_BOOST);
    }
}
