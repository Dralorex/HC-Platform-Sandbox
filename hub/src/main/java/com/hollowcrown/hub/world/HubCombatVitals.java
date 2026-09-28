package com.hollowcrown.hub.world;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * One-shot combat vitals. No hunger locks anywhere.
 *
 * <ul>
 *   <li>PvP off / death / match countdown start → healed and full (HP, food 20, sat 6/20)</li>
 *   <li>PvP on / match → vanilla sat drain and heal; can die</li>
 * </ul>
 */
public final class HubCombatVitals {

    /** Food saturation after PvP off / death / match countdown (6/20 ≡ 3/10). */
    public static final float RESET_SATURATION = 6.0f;
    public static final int RESET_FOOD_LEVEL = 20;

    private HubCombatVitals() {
    }

    /** Healed and full: max health, food 20, saturation {@link #RESET_SATURATION}, no absorption. */
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

    /** Entering PvP: strip free absorption/regen hearts only. */
    public static void prepareForPvp(Player p) {
        clearAbsorptionAndRegen(p);
    }

    public static void clearAbsorptionAndRegen(Player p) {
        if (p == null || !p.isOnline()) return;
        p.setAbsorptionAmount(0f);
        p.removePotionEffect(PotionEffectType.ABSORPTION);
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.HEALTH_BOOST);
    }
}
