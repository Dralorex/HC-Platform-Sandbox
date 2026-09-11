package com.hollowcrown.hungergames.listeners;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.managers.BuildRuleManager;
import com.hollowcrown.hungergames.managers.GameManager;
import com.hollowcrown.hungergames.managers.WorldGameSettings;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class RulesListener implements Listener {

    private final HungerGamesPlugin plugin;
    private final BuildRuleManager rules;

    public RulesListener(HungerGamesPlugin plugin, BuildRuleManager rules) {
        this.plugin = plugin;
        this.rules = rules;
    }

    private boolean opBypassBuildDig(Player p) {
        return p.isOp() && plugin.getConfig().getBoolean("rules.opsBypassBuildDig", true);
    }

    @EventHandler
    public void onDispenserInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
    
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.DISPENSER) {
            return;
        }
    
        if (!rules.isDispenserInteractionEnabled()) {
            event.setCancelled(true);
        }
    }
    
    private WorldGameSettings matchSettings(Player p) {
        GameManager gm = plugin.game();
        if (gm.getActiveMatchSettings() == null) return null;
        if (gm.getState() != GameManager.State.FREEZE && gm.getState() != GameManager.State.RUNNING) return null;
        if (!gm.isMatchParticipant(p.getUniqueId())) return null;
        return gm.getActiveMatchSettings();
    }

    private boolean effectiveCrafting(Player p) {
        WorldGameSettings ws = matchSettings(p);
        return ws != null ? ws.craftingEnabled() : rules.isCraftingEnabled();
    }

    private boolean effectivePlace(Player p, Material type) {
        WorldGameSettings ws = matchSettings(p);
        if (ws != null) {
            if (!ws.buildEnabled()) return false;
            return rules.materialAllowedPlace(type, ws.buildMode());
        }
        return rules.canPlace(type);
    }

    private boolean effectiveBreak(Player p, Material type) {
        WorldGameSettings ws = matchSettings(p);
        if (ws != null) {
            if (!ws.digEnabled()) return false;
            return rules.materialAllowedBreak(type, ws.digMode());
        }
        return rules.canBreak(type);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (opBypassBuildDig(p)) return;
        Material type = e.getBlockPlaced().getType();
        if (!effectivePlace(p, type)) {
            e.setCancelled(true);
            p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(ChatColor.RED + "Building is not allowed for " + type.name() + "."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (opBypassBuildDig(p)) return;
        Material type = e.getBlock().getType();
        if (!effectiveBreak(p, type)) {
            e.setCancelled(true);
            p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(ChatColor.RED + "Digging is not allowed for " + type.name() + "."));
        }
    }

    // Disable crafting by clearing the output if crafting is disabled
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        boolean craftingAllowed = rules.isCraftingEnabled();
        for (HumanEntity viewer : e.getViewers()) {
            if (viewer instanceof Player p) {
                craftingAllowed = effectiveCrafting(p);
                break;
            }
        }
        if (!craftingAllowed) {
            e.getInventory().setResult(null);
            for (HumanEntity viewer : e.getViewers()) {
                if (viewer instanceof Player p) {
                    p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(ChatColor.RED + "Crafting is disabled."));
                }
            }
        }
    }
}
