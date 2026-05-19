package com.hollowcrown.hungergames.listeners;

import com.hollowcrown.hungergames.managers.EnvoyManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class EnvoyListener implements Listener {
    private final EnvoyManager envoys;

    public EnvoyListener(EnvoyManager envoys) {
        this.envoys = envoys;
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (!(e.getRightClicked() instanceof ArmorStand)) return;
        ArmorStand as = (ArmorStand) e.getRightClicked();
        if (as.customName() == null || !as.isMarker()) return;
        Player p = e.getPlayer();
        Location base = as.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        if (envoys.tryOpen(p, base)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractBlock(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Location loc = b.getLocation();
        if (envoys.tryOpen(e.getPlayer(), loc)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Location loc = b.getLocation();
        if (envoys.isActiveEnvoyPackage(loc)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(bl -> envoys.isActiveEnvoyPackage(bl.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(bl -> envoys.isActiveEnvoyPackage(bl.getLocation()));
    }
}
