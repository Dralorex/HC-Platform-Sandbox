package com.hollowcrown.hub.policy;

import com.hollowcrown.hub.gui.HubTemplateBlacklistMenu;
import com.hollowcrown.hub.gui.HubTemplateGeneralSettingsMenu;
import com.hollowcrown.hub.gui.HubTemplateLandingMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Saves deferred template-policy edits when an operator closes the policy GUI stack.
 */
public final class HubTemplatePolicyCloseListener implements Listener {

    private final com.hollowcrown.hub.HollowCrownHubPlugin plugin;

    public HubTemplatePolicyCloseListener(com.hollowcrown.hub.HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder closed = event.getInventory().getHolder();
        String templateKey = templateKeyFrom(closed);
        if (templateKey == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (stillInPolicyGui(player)) {
                return;
            }
            if (plugin.templatePolicies().flushDeferred(templateKey)) {
                plugin.lobbyService().refreshLobbiesAfterPolicyChange(templateKey);
            }
        }, 1L);
    }

    private static boolean stillInPolicyGui(Player player) {
        InventoryHolder h = player.getOpenInventory().getTopInventory().getHolder();
        return h instanceof HubTemplateGeneralSettingsMenu
                || h instanceof HubTemplateBlacklistMenu
                || h instanceof HubTemplateLandingMenu;
    }

    private static String templateKeyFrom(InventoryHolder holder) {
        if (holder instanceof HubTemplateGeneralSettingsMenu g) {
            return g.templateKey();
        }
        if (holder instanceof HubTemplateBlacklistMenu b) {
            return b.templateKey();
        }
        if (holder instanceof HubTemplateLandingMenu l) {
            return l.templateKey();
        }
        return null;
    }
}
