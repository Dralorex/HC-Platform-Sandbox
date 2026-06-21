package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.lobby.HubLobbyInstanceRecord;
import com.hollowcrown.hub.lobby.HubLobbyService;
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

/** Paginated OPEN instances for one template. */
public final class LobbyJoinInstanceMenu {

    private LobbyJoinInstanceMenu() {
    }

    public static void open(Player player, HollowCrownHubPlugin plugin, String templateKey, int page) {
        HubLobbyService svc = plugin.lobbyService();
        List<HubLobbyInstanceRecord> all = new ArrayList<>(svc.listOpenForTemplate(templateKey));
        int totalPages = Math.max(1, (all.size() + LobbyJoinInstanceMenuHolder.PAGE_SIZE - 1) / LobbyJoinInstanceMenuHolder.PAGE_SIZE);
        int p = Math.min(Math.max(0, page), totalPages - 1);
        int from = p * LobbyJoinInstanceMenuHolder.PAGE_SIZE;
        int to = Math.min(from + LobbyJoinInstanceMenuHolder.PAGE_SIZE, all.size());

        LobbyJoinInstanceMenuHolder holder = new LobbyJoinInstanceMenuHolder(plugin, templateKey, p, totalPages);
        var inv = holder.getInventory();

        for (int i = 0; i < LobbyJoinInstanceMenuHolder.PAGE_SIZE; i++) {
            int idx = from + i;
            if (idx >= to) {
                break;
            }
            inv.setItem(i, instanceRow(plugin, all.get(idx)));
        }

        for (int s = 36; s <= 44; s++) {
            inv.setItem(s, filler());
        }
        if (p > 0) {
            inv.setItem(45, navItem(plugin, "jinst_prev", Material.ARROW, "Previous page"));
        }
        inv.setItem(49, navItem(plugin, "jinst_info", Material.BOOK, "Page " + (p + 1) + " / " + totalPages));
        if (p < totalPages - 1) {
            inv.setItem(53, navItem(plugin, "jinst_next", Material.ARROW, "Next page"));
        }
        inv.setItem(47, navItem(plugin, "jinst_back", Material.OAK_DOOR, "Back to games"));
        inv.setItem(50, leaveLobbyItem(plugin));
        inv.setItem(51, navItem(plugin, "jinst_close", Material.BARRIER, "Close"));

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
            m.getPersistentDataContainer().set(plugin.lobbyJoinActionKey(), PersistentDataType.STRING, action);
            it.setItemMeta(m);
        }
        return it;
    }

    private static ItemStack leaveLobbyItem(HollowCrownHubPlugin plugin) {
        ItemStack it = new ItemStack(Material.RED_BED);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text("Leave Lobby", NamedTextColor.RED));
            m.lore(List.of(Component.text("Click to return to the hub", NamedTextColor.GRAY)));
            m.getPersistentDataContainer().set(plugin.lobbyJoinActionKey(), PersistentDataType.STRING, "jinst_leave");
            it.setItemMeta(m);
        }
        return it;
    }

    private static ItemStack instanceRow(HollowCrownHubPlugin plugin, HubLobbyInstanceRecord r) {
        ItemStack it = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m == null) {
            return it;
        }
        m.displayName(Component.text(r.worldName(), NamedTextColor.GREEN));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Players: " + (r.loadedWorld() != null ? r.loadedWorld().getPlayerCount() : 0), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Click to join this lobby", NamedTextColor.AQUA));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        var pdc = m.getPersistentDataContainer();
        pdc.set(plugin.lobbyJoinActionKey(), PersistentDataType.STRING, "jinst_join");
        pdc.set(plugin.lobbyJoinInstanceIdKey(), PersistentDataType.STRING, r.id());
        it.setItemMeta(m);
        return it;
    }
}
