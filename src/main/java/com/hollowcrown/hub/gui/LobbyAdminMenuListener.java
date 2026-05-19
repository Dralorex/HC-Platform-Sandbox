package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HubLog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * OP-only lobby admin menu clicks.
 */
public final class LobbyAdminMenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder raw = event.getView().getTopInventory().getHolder();
        if (!(raw instanceof LobbyAdminMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("hollowcrownhub.admin")) {
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
        var actKey = holder.plugin().lobbyAdminActionKey();
        var idKey = holder.plugin().lobbyAdminIdKey();
        if (!pdc.has(actKey, PersistentDataType.STRING)) {
            return;
        }
        String action = pdc.get(actKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        switch (action) {
            case "prev" -> LobbyAdminMenu.open(player, holder.plugin(), holder.page() - 1);
            case "next" -> LobbyAdminMenu.open(player, holder.plugin(), holder.page() + 1);
            case "close" -> player.closeInventory();
            case "info" -> { /* no-op */ }
            case "forget" -> {
                String id = pdc.get(idKey, PersistentDataType.STRING);
                if (id != null && holder.plugin().lobbyService().forgetBroken(id)) {
                    player.sendMessage(Component.text("Registry row removed.", NamedTextColor.GREEN));
                    HubLog.info(holder.plugin(), "action=lobby_admin_forget_gui", "player=" + player.getName(), "id=" + id);
                    LobbyAdminMenu.open(player, holder.plugin(), holder.page());
                } else {
                    player.sendMessage(Component.text("Forget failed (must be BROKEN).", NamedTextColor.RED));
                }
            }
            case "open" -> {
                String id = pdc.get(idKey, PersistentDataType.STRING);
                if (id == null) {
                    return;
                }
                if (event.isShiftClick()) {
                    String msg = holder.plugin().lobbyService().destroyLobby(id, true);
                    player.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
                    HubLog.info(holder.plugin(), "action=lobby_admin_destroy_gui", "player=" + player.getName(), "id=" + id);
                    LobbyAdminMenu.open(player, holder.plugin(), holder.page());
                } else {
                    holder.plugin().lobbyService().teleportAdmin(player, id);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder h = event.getView().getTopInventory().getHolder();
        if (h instanceof LobbyAdminMenuHolder) {
            event.setCancelled(true);
        }
    }
}
