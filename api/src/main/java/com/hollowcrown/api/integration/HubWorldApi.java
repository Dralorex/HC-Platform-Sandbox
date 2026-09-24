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
     * PDC key for hub + lobby locked utility items (sword, compass, party, etc.).
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
     * Locked hotbar for Iron Legacy lobby: hub sword + compass + party item + leave item (see {@code hub-world.yml}
     * {@code items.lobby.leave}). Uses the same {@link #lockedLobbyUtilityItemKey()} as the hub disk.
     */
    void applyLobbyUtilityLoadout(Player player, int hotbarSlotAfterApply);

    /** True if the stack matches the configured lobby “leave queue” utility item. */
    boolean isLobbyLeaveUtilityItem(ItemStack stack);

    /** True if the stack is the lobby “match team” utility item (hotbar slot 3 by default). */
    boolean isLobbyTeamUtilityItem(Player player, ItemStack stack);

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

    /** After leaving lobby queue, avoid opening the game lobby GUI from a hotbar refresh. */
    void suppressGameLobbyGuiBriefly(Player player, long durationMs);

    boolean isGameLobbyGuiSuppressed(Player player);

    /**
     * Leave pre-match lobby if needed and teleport to configured hub spawn ({@code /hch sethub}).
     * When the player is in a party, routes the whole online party together and splits across numbered
     * hub instances when one hub would exceed {@code hub.instanceCapacity}.
     * @return false when hub spawn is missing or the world is not loaded
     */
    boolean sendToHubSpawn(Player player);
}
