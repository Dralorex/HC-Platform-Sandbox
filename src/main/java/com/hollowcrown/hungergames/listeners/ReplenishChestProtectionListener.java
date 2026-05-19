package com.hollowcrown.hungergames.listeners;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** IL replenish chests: indestructible like bedrock (players, explosions). */
public class ReplenishChestProtectionListener implements Listener {
    private final HungerGamesPlugin plugin;

    public ReplenishChestProtectionListener(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean protects(Block b) {
        if (b == null || b.getType() != Material.CHEST) return false;
        return plugin.replenish().isProtectedReplenishChest(b.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (protects(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> protects(b));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> protects(b));
    }
}
