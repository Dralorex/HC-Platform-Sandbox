package com.hollowcrown.hungergames.listeners;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.managers.ReplenishModeManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * IL replenish chests: unbreakable like bedrock unless the breaker holds the replenish wand
 * (then spawn data is cleared by {@link ReplenishModeManager}).
 */
public class ReplenishChestProtectionListener implements Listener {
    private final HungerGamesPlugin plugin;

    public ReplenishChestProtectionListener(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean protects(Block b) {
        if (b == null || b.getType() != Material.CHEST) return false;
        return plugin.replenish().isProtectedReplenishChest(b.getLocation());
    }

    private static boolean holdingReplenishWand(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        return ReplenishModeManager.isReplenishWand(main) || ReplenishModeManager.isReplenishWand(off);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!protects(e.getBlock())) return;
        Player p = e.getPlayer();
        // Even OPs must hold the replenish wand to break a tracked loot chest.
        if (holdingReplenishWand(p)) {
            return;
        }
        e.setCancelled(true);
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                "\u00A7cHold the Replenish Wand to break custom loot chests."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::protects);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::protects);
    }
}
