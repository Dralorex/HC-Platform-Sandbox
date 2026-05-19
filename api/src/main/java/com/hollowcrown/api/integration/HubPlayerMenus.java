package com.hollowcrown.api.integration;

import org.bukkit.entity.Player;

/**
 * Hub-owned player GUIs (join flow, party). HollowCrownHub registers the implementation;
 * minigames resolve this from {@code ServicesManager} for limbo/hotbar actions.
 */
public interface HubPlayerMenus {

    /** Same as {@code /hch join} — template grid / lobby instances. */
    void openJoinGameMenu(Player player);

    /** Hub party GUI (create / invites / roster). */
    void openPartyMenu(Player player);
}
