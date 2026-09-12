package com.hollowcrown.hub.gui;

import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.api.queue.MatchEnqueueResult;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.command.MatchEnqueueMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Player lobby join GUIs — template grid and per-template instance pages.
 */
public final class LobbyJoinMenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder raw = event.getView().getTopInventory().getHolder();
        if (raw instanceof LobbyJoinMechanicMenuHolder holderMech) {
            handleMechanicClick(event, holderMech);
            return;
        }
        if (raw instanceof LobbyJoinTemplateMenuHolder holderTpl) {
            handleTemplateClick(event, holderTpl);
            return;
        }
        if (raw instanceof LobbyJoinInstanceMenuHolder holderInst) {
            handleInstanceClick(event, holderInst);
        }
    }

    private static void handleMechanicClick(InventoryClickEvent event, LobbyJoinMechanicMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("hollowcrownhub.join")) {
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        var actKey = holder.plugin().lobbyJoinActionKey();
        if (!pdc.has(actKey, PersistentDataType.STRING)) {
            return;
        }
        String action = pdc.get(actKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        switch (action) {
            case "jm_prev" -> LobbyJoinMechanicMenu.open(player, holder.plugin(), holder.page() - 1);
            case "jm_next" -> LobbyJoinMechanicMenu.open(player, holder.plugin(), holder.page() + 1);
            case "jm_close" -> player.closeInventory();
            case "jm_pick" -> {
                String mech = pdc.get(holder.plugin().lobbyJoinMechanicKey(), PersistentDataType.STRING);
                if (mech != null) {
                    LobbyJoinTemplateMenu.open(player, holder.plugin(), mech, 0);
                }
            }
            default -> {
            }
        }
    }

    private static void handleTemplateClick(InventoryClickEvent event, LobbyJoinTemplateMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("hollowcrownhub.join")) {
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        var actKey = holder.plugin().lobbyJoinActionKey();
        if (!pdc.has(actKey, PersistentDataType.STRING)) {
            return;
        }
        String action = pdc.get(actKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        switch (action) {
            case "jtpl_prev" -> LobbyJoinTemplateMenu.open(player, holder.plugin(), holder.mechanicId(), holder.page() - 1);
            case "jtpl_next" -> LobbyJoinTemplateMenu.open(player, holder.plugin(), holder.mechanicId(), holder.page() + 1);
            case "jtpl_back" -> LobbyJoinMechanicMenu.open(player, holder.plugin(), 0);
            case "jtpl_close" -> player.closeInventory();
            case "jtpl_info" -> { /* no-op */ }
            case "jtpl_create" -> {
                if (!player.isOp()) {
                    player.sendMessage(Component.text("OP is required to create templates.", NamedTextColor.RED));
                    return;
                }
                LobbyJoinTemplateCreateFlow.start(player, holder.plugin(), holder.mechanicId());
            }
            case "jtpl_pick" -> {
                String tk = pdc.get(holder.plugin().lobbyJoinTemplateKey(), PersistentDataType.STRING);
                if (tk == null) {
                    return;
                }
                ClickType ct = event.getClick();
                if (ct == ClickType.RIGHT || ct == ClickType.SHIFT_RIGHT) {
                    LobbyJoinInstanceMenu.open(player, holder.plugin(), tk, holder.mechanicId(), 0);
                } else if (ct.isLeftClick()) {
                    boolean queueFirst = holder.plugin().templates().get(tk).map(e -> e.joinQueueFirst()).orElse(false);
                    if (queueFirst) {
                        MatchEnqueueResult r = holder.plugin().matchQueueService().enqueue(player.getUniqueId(), tk);
                        if (r.success()) {
                            player.closeInventory();
                            player.sendMessage(Component.text(
                                    "Queued for " + tk + " (position ~" + r.positionOneBased() + ").",
                                    NamedTextColor.GREEN));
                            HubLog.info(holder.plugin(), "action=lobby_join_tpl_queue", "player=" + player.getName(), "templateKey=" + tk);
                        } else {
                            player.sendMessage(Component.text(
                                    MatchEnqueueMessages.playerDenyMessage(r.status()),
                                    NamedTextColor.RED));
                        }
                    } else if (holder.plugin().lobbyService().joinOldestOpenForTemplate(player, tk)) {
                        player.closeInventory();
                        HubLog.info(holder.plugin(), "action=lobby_join_tpl_left", "player=" + player.getName(), "templateKey=" + tk);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void handleInstanceClick(InventoryClickEvent event, LobbyJoinInstanceMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("hollowcrownhub.join")) {
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        var actKey = holder.plugin().lobbyJoinActionKey();
        if (!pdc.has(actKey, PersistentDataType.STRING)) {
            return;
        }
        String action = pdc.get(actKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        switch (action) {
            case "jinst_prev" -> LobbyJoinInstanceMenu.open(player, holder.plugin(), holder.templateKey(),
                    holder.templateMechanicId(), holder.page() - 1);
            case "jinst_next" -> LobbyJoinInstanceMenu.open(player, holder.plugin(), holder.templateKey(),
                    holder.templateMechanicId(), holder.page() + 1);
            case "jinst_back" -> LobbyJoinTemplateMenu.open(player, holder.plugin(), holder.templateMechanicId(), 0);
            case "jinst_close" -> player.closeInventory();
            case "jinst_info" -> { /* no-op */ }
            case "jinst_join" -> {
                String id = pdc.get(holder.plugin().lobbyJoinInstanceIdKey(), PersistentDataType.STRING);
                if (id == null) {
                    return;
                }
                if (holder.plugin().lobbyService().teleportUnitIntoOpenLobby(player, id)) {
                    player.closeInventory();
                    HubLog.info(holder.plugin(), "action=lobby_join_instance_click", "player=" + player.getName(), "id=" + id);
                }
            }
            case "jinst_leave" -> {
                Optional<HubSpawn> spawnOpt = holder.plugin().platform().getHubSpawn();
                if (spawnOpt.isEmpty()) {
                    player.sendMessage(Component.text("Hub spawn is not set.", NamedTextColor.RED));
                    return;
                }
                var plug = holder.plugin();
                Bukkit.getScheduler().runTask(plug, () -> {
                    player.closeInventory();
                    if (plug.sendPlayerToHubSpawn(player)) {
                        player.sendMessage(Component.text("Returned to the hub.", NamedTextColor.GREEN));
                        HubLog.info(plug, "action=lobby_leave_gui", "player=" + player.getName());
                    }
                });
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder h = event.getView().getTopInventory().getHolder();
        if (h instanceof LobbyJoinMechanicMenuHolder
                || h instanceof LobbyJoinTemplateMenuHolder
                || h instanceof LobbyJoinInstanceMenuHolder
                || h instanceof HubPartyLobbyTrimMenu) {
            event.setCancelled(true);
        }
    }
}
