package com.hollowcrown.hungergames.listeners;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.managers.GiftDropManager;
import com.hollowcrown.hungergames.managers.EnvoyManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Listener for gift drop block interactions. */
public class GiftDropListener implements Listener {
    private final GiftDropManager giftDrops;
    private final EnvoyManager envoys;

    public GiftDropListener(GiftDropManager giftDrops, EnvoyManager envoys) {
        this.giftDrops = giftDrops;
        this.envoys = envoys;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Location loc = b.getLocation();
        
        // Check if it's a gift drop
        GiftDropManager.GiftDropData data = giftDrops.getData(loc);
        if (data != null) {
            e.setCancelled(true);
            giveRewards(e.getPlayer(), data, loc);
            giftDrops.removeDrop(loc);
            return;
        }
        
        // Check if it's a regular envoy package (will implement later)
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Location loc = b.getLocation();
        GiftDropManager.GiftDropData data = giftDrops.getData(loc);
        if (data != null) {
            e.setCancelled(true);
            giveRewards(e.getPlayer(), data, loc);
            giftDrops.removeDrop(loc);
        }
    }

    private void giveRewards(Player p, GiftDropManager.GiftDropData data, Location blockLoc) {
        List<ItemStack> drops = envoys.rollPackageDropsForType(data.type, data.tier, blockLoc);
        for (ItemStack it : drops) {
            HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(it);
            overflow.values().forEach(rem -> {
                var dropped = p.getWorld().dropItemNaturally(p.getLocation(), rem);
                HungerGamesPlugin.get().pickupGuard().guard(dropped, p.getUniqueId(), 15000L);
            });
        }
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aOpened §e" + data.packageName + "§a!"));
    }
}

