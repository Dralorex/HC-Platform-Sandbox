package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.lobby.HubLobbyService;
import com.hollowcrown.hub.template.HubTemplateEntry;
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
 * One stack per registered template; left-click oldest lobby, right-click lobby list.
 */
public final class LobbyJoinTemplateMenu {

    private static final Material[] TEMPLATE_ICONS = {
            Material.LIME_CONCRETE,
            Material.CYAN_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.ORANGE_CONCRETE,
            Material.MAGENTA_CONCRETE,
            Material.PINK_CONCRETE,
            Material.PURPLE_CONCRETE,
    };

    private LobbyJoinTemplateMenu() {
    }

    public static void open(Player player, HollowCrownHubPlugin plugin, int page) {
        List<HubTemplateEntry> all = new ArrayList<>(plugin.templates().list());
        int totalPages = Math.max(1, (all.size() + LobbyJoinTemplateMenuHolder.PAGE_SIZE - 1) / LobbyJoinTemplateMenuHolder.PAGE_SIZE);
        int p = Math.min(Math.max(0, page), totalPages - 1);
        int from = p * LobbyJoinTemplateMenuHolder.PAGE_SIZE;
        int to = Math.min(from + LobbyJoinTemplateMenuHolder.PAGE_SIZE, all.size());

        LobbyJoinTemplateMenuHolder holder = new LobbyJoinTemplateMenuHolder(plugin, p, totalPages);
        var inv = holder.getInventory();
        HubLobbyService svc = plugin.lobbyService();

        for (int i = 0; i < LobbyJoinTemplateMenuHolder.PAGE_SIZE; i++) {
            int idx = from + i;
            if (idx >= to) {
                break;
            }
            inv.setItem(i, templateItem(plugin, svc, all.get(idx), player));
        }

        for (int s = 36; s <= 44; s++) {
            inv.setItem(s, filler());
        }
        if (p > 0) {
            inv.setItem(45, navItem(plugin, "jtpl_prev", Material.ARROW, "Previous page"));
        }
        inv.setItem(49, navItem(plugin, "jtpl_info", Material.BOOK, "Page " + (p + 1) + " / " + totalPages));
        if (p < totalPages - 1) {
            inv.setItem(53, navItem(plugin, "jtpl_next", Material.ARROW, "Next page"));
        }
        inv.setItem(47, navItem(plugin, "jtpl_close", Material.BARRIER, "Close"));

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

    private static ItemStack templateItem(HollowCrownHubPlugin plugin, HubLobbyService svc, HubTemplateEntry e, Player viewer) {
        String tk = e.templateKey();
        Material mat = TEMPLATE_ICONS[Math.floorMod(tk.hashCode(), TEMPLATE_ICONS.length)];
        int open = svc.listOpenForTemplate(tk).size();
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m == null) {
            return it;
        }
        m.displayName(Component.text(e.effectiveDisplayName(), NamedTextColor.GREEN));
        List<Component> lore = new ArrayList<>();
        if (viewer.isOp()) {
            lore.add(Component.text("Template: " + tk, NamedTextColor.GRAY));
            lore.add(Component.text("Mechanic: " + e.mechanicId(), NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.text("Open lobbies: " + open, NamedTextColor.AQUA));
        lore.add(Component.empty());
        if (e.joinQueueFirst()) {
            lore.add(Component.text("Left-Click: Join Match Queue", NamedTextColor.WHITE));
        } else {
            lore.add(Component.text("Left-Click: Join a Lobby", NamedTextColor.WHITE));
        }
        lore.add(Component.text("Right-Click: Pick a Lobby", NamedTextColor.GOLD));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        var pdc = m.getPersistentDataContainer();
        pdc.set(plugin.lobbyJoinActionKey(), PersistentDataType.STRING, "jtpl_pick");
        pdc.set(plugin.lobbyJoinTemplateKey(), PersistentDataType.STRING, tk);
        it.setItemMeta(m);
        return it;
    }
}
