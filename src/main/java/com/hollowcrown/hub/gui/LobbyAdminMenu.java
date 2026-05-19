package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.lobby.HubLobbyInstanceRecord;
import com.hollowcrown.hub.lobby.HubLobbyService;
import com.hollowcrown.hub.lobby.LobbyInstanceState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the OP-only lobby instance admin GUI (barrier = BROKEN, lime pane = OPEN).
 */
public final class LobbyAdminMenu {

    private LobbyAdminMenu() {
    }

    public static void open(Player player, HollowCrownHubPlugin plugin, int page) {
        HubLobbyService svc = plugin.lobbyService();
        List<HubLobbyInstanceRecord> all = new ArrayList<>(svc.listVisibleTo(true));
        int totalPages = Math.max(1, (all.size() + LobbyAdminMenuHolder.PAGE_SIZE - 1) / LobbyAdminMenuHolder.PAGE_SIZE);
        int p = Math.min(Math.max(0, page), totalPages - 1);
        int from = p * LobbyAdminMenuHolder.PAGE_SIZE;
        int to = Math.min(from + LobbyAdminMenuHolder.PAGE_SIZE, all.size());

        LobbyAdminMenuHolder holder = new LobbyAdminMenuHolder(plugin, p, totalPages);
        var inv = holder.getInventory();

        for (int i = 0; i < LobbyAdminMenuHolder.PAGE_SIZE; i++) {
            int idx = from + i;
            if (idx >= to) {
                break;
            }
            HubLobbyInstanceRecord r = all.get(idx);
            inv.setItem(i, rowItem(plugin, r));
        }

        for (int s = 36; s <= 44; s++) {
            inv.setItem(s, filler());
        }

        if (p > 0) {
            inv.setItem(45, navItem(plugin, "prev", Material.ARROW, "Previous page"));
        }
        inv.setItem(49, navItem(plugin, "info", Material.BOOK, "Page " + (p + 1) + " / " + totalPages));
        if (p < totalPages - 1) {
            inv.setItem(53, navItem(plugin, "next", Material.ARROW, "Next page"));
        }
        inv.setItem(47, navItem(plugin, "close", Material.BARRIER, "Close"));

        player.openInventory(inv);
    }

    private static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(" "));
            it.setItemMeta(m);
        }
        return it;
    }

    private static ItemStack navItem(HollowCrownHubPlugin plugin, String action, Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(name, NamedTextColor.YELLOW));
            m.getPersistentDataContainer().set(plugin.lobbyAdminActionKey(), PersistentDataType.STRING, action);
            it.setItemMeta(m);
        }
        return it;
    }

    private static ItemStack rowItem(HollowCrownHubPlugin plugin, HubLobbyInstanceRecord r) {
        boolean broken = r.state() == LobbyInstanceState.BROKEN;
        Material mat = broken ? Material.BARRIER : Material.LIME_STAINED_GLASS_PANE;
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m == null) {
            return it;
        }
        m.displayName(Component.text((broken ? "BROKEN: " : "OPEN: ") + r.worldName(), broken ? NamedTextColor.RED : NamedTextColor.GREEN));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("id: " + r.id(), NamedTextColor.GRAY));
        lore.add(Component.text("template: " + r.templateKey(), NamedTextColor.GRAY));
        lore.add(Component.text("world: " + r.worldName(), NamedTextColor.GRAY));
        int pc = r.loadedWorld() != null ? r.loadedWorld().getPlayerCount() : 0;
        lore.add(Component.text("players: " + pc, NamedTextColor.GRAY));
        if (broken) {
            lore.add(Component.empty());
            lore.add(Component.text("Click: remove registry (forget)", NamedTextColor.YELLOW));
            lore.add(Component.text("Delete folder on disk first if needed.", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("Click: teleport to world spawn", NamedTextColor.AQUA));
            lore.add(Component.text("Shift+click: destroy instance", NamedTextColor.GOLD));
        }
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        m.getPersistentDataContainer().set(plugin.lobbyAdminIdKey(), PersistentDataType.STRING, r.id());
        m.getPersistentDataContainer().set(plugin.lobbyAdminActionKey(), PersistentDataType.STRING, broken ? "forget" : "open");
        it.setItemMeta(m);
        return it;
    }
}
