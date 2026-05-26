package com.hollowcrown.api.integration;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Hollow Crown Hub: hub-world protections, utility loadout, and admin-tunable hub area settings.
 * Registered by HollowCrownHub when enabled.
 */
public interface HubWorldApi {

    /**
     * PDC key for hub + limbo locked utility items (sword, compass, party, etc.).
     * Always namespaced to HollowCrownHub so minigames share one tag.
     */
    NamespacedKey lockedLobbyUtilityItemKey();

    Optional<Location> hubCenterLocation();

    boolean isLocationInHubProtection(Location loc);

    boolean isInHubProtection(Player player);

    boolean isHubBuildMode(Player player);

    void setHubBuildMode(Player player, boolean on);

    void applyHubUtilityLoadout(Player player, boolean wipeInventory, boolean selectCompassHotbar);

    /**
     * Locked hotbar for Iron Legacy limbo: hub sword + compass + party item + leave item (see {@code hub-world.yml}
     * {@code items.limbo.leave}). Uses the same {@link #lockedLobbyUtilityItemKey()} as the hub disk.
     */
    void applyLimboUtilityLoadout(Player player, int hotbarSlotAfterApply);

    /** True if the stack matches the configured limbo “leave queue” utility item. */
    boolean isLimboLeaveUtilityItem(ItemStack stack);

    /** Survival + hub hotbar after leaving template edit when player was not in hub creative build mode. */
    void applySurvivalHubLoadoutAfterTemplate(Player player);

    double protectionRadiusBlocks();

    void setProtectionRadiusBlocks(double radius);

    boolean allowHostileMobSpawnsInHub();

    void setAllowHostileMobSpawnsInHub(boolean value);

    boolean allowPassiveMobSpawnsInHub();

    void setAllowPassiveMobSpawnsInHub(boolean value);

    boolean isHubPvpModeActive(Player player);

    /** Whether the player is currently standing inside the PvP zone. */
    boolean isInPvpZone(Player player);

    void givePvpPadItem(Player player);

    /** After leaving limbo queue, avoid opening the game lobby GUI from a hotbar refresh. */
    void suppressGameLobbyGuiBriefly(Player player, long durationMs);

    boolean isGameLobbyGuiSuppressed(Player player);
}
