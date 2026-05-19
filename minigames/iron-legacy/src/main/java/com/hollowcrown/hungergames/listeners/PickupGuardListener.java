package com.hollowcrown.hungergames.listeners;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

import java.util.*;

/** Prevent others from picking overflow drops for a short duration. */
public class PickupGuardListener implements Listener {
    private final Map<UUID, Guard> guarded = new HashMap<>();

    public void guard(Item item, UUID owner, long millis) {
        if (item == null) return;
        guarded.put(item.getUniqueId(), new Guard(owner, System.currentTimeMillis() + millis));
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent e) {
        Item it = e.getItem();
        Guard g = guarded.get(it.getUniqueId());
        if (g == null) return;
        if (System.currentTimeMillis() > g.until) { guarded.remove(it.getUniqueId()); return; }
        Player p = e.getPlayer();
        if (!p.getUniqueId().equals(g.owner)) {
            e.setCancelled(true);
        }
    }

    private record Guard(UUID owner, long until) {}
}


