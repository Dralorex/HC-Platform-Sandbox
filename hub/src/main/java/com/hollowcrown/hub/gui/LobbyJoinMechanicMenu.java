package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.template.HubMechanics;
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
 * Top level of the game lobby compass: one icon per mechanic (including Vanilla).
 */
public final class LobbyJoinMechanicMenu {

    private static final Material[] ICONS = {
            Material.IRON_SWORD,
            Material.GOLDEN_SWORD,
            Material.NETHERITE_SWORD,
            Material.BOW,
            Material.CROSSBOW,
            Material.TRIDENT,
            Material.GRASS_BLOCK,
            Material.OAK_BOAT,
    };

    private LobbyJoinMechanicMenu() {
    }

    public static void open(Player player, HollowCrownHubPlugin plugin, int page) {
        List<String> mechanics = HubMechanics.sortedMechanicIds(plugin.templates().list());
        int totalPages = Math.max(1, (mechanics.size() + LobbyJoinMechanicMenuHolder.PAGE_SIZE - 1)
                / LobbyJoinMechanicMenuHolder.PAGE_SIZE);
        int p = Math.min(Math.max(0, page), totalPages - 1);
        int from = p * LobbyJoinMechanicMenuHolder.PAGE_SIZE;
        int to = Math.min(from + LobbyJoinMechanicMenuHolder.PAGE_SIZE, mechanics.size());

        LobbyJoinMechanicMenuHolder holder = new LobbyJoinMechanicMenuHolder(plugin, p, totalPages);
        var inv = holder.getInventory();

        for (int i = 0; i < LobbyJoinMechanicMenuHolder.PAGE_SIZE; i++) {
            int idx = from + i;
            if (idx >= to) {
                break;
            }
            inv.setItem(i, mechanicItem(plugin, mechanics.get(idx)));
        }

        for (int s = 36; s <= 44; s++) {
            inv.setItem(s, filler());
        }
        if (p > 0) {
            inv.setItem(45, nav(plugin, "jm_prev", Material.ARROW, "Previous page"));
        }
        inv.setItem(49, nav(plugin, "jm_info", Material.BOOK, "Page " + (p + 1) + " / " + totalPages));
        if (p < totalPages - 1) {
            inv.setItem(53, nav(plugin, "jm_next", Material.ARROW, "Next page"));
        }
        inv.setItem(47, nav(plugin, "jm_close", Material.BARRIER, "Close"));

        player.openInventory(inv);
    }

    private static ItemStack mechanicItem(HollowCrownHubPlugin plugin, String mechanicId) {
        Material mat = ICONS[Math.floorMod(mechanicId.hashCode(), ICONS.length)];
        int worlds = countTemplates(plugin, mechanicId);
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m == null) {
            return it;
        }
        m.displayName(Component.text(HubMechanics.displayLabel(mechanicId), NamedTextColor.GREEN));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Worlds: " + worlds, NamedTextColor.AQUA));
        lore.add(Component.empty());
        lore.add(Component.text("Left / Right click: browse worlds", NamedTextColor.WHITE));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        m.getPersistentDataContainer().set(plugin.lobbyJoinActionKey(), PersistentDataType.STRING, "jm_pick");
        m.getPersistentDataContainer().set(plugin.lobbyJoinMechanicKey(), PersistentDataType.STRING, mechanicId);
        it.setItemMeta(m);
        return it;
    }

    private static int countTemplates(HollowCrownHubPlugin plugin, String mechanicId) {
        int n = 0;
        for (HubTemplateEntry e : plugin.templates().list()) {
            if (mechanicId.equalsIgnoreCase(HubMechanics.VANILLA)) {
                if (!HubMechanics.hasMechanicSelected(e.mechanicId()) || HubMechanics.isVanilla(e.mechanicId())) {
                    n++;
                }
            } else if (mechanicId.equalsIgnoreCase(e.mechanicId())) {
                n++;
            }
        }
        return n;
    }

    static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(" "));
            it.setItemMeta(m);
        }
        return it;
    }

    static ItemStack nav(HollowCrownHubPlugin plugin, String action, Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(name, NamedTextColor.YELLOW));
            m.getPersistentDataContainer().set(plugin.lobbyJoinActionKey(), PersistentDataType.STRING, action);
            it.setItemMeta(m);
        }
        return it;
    }
}
