package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.policy.HubPluginPolicyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Admin GUI for Hub/Lobby/Match plugin policy lists.
 *
 * <p>Click the wool to toggle WHITELIST/BLACKLIST mode.
 * Click a plugin book to add/remove it from the list.</p>
 */
public final class HubPluginPolicyAdminMenu extends HubMenuView {

    private static final int SLOT_SCOPE_HUB = 0;
    private static final int SLOT_SCOPE_LOBBY = 1;
    private static final int SLOT_SCOPE_MATCH = 2;
    private static final int SLOT_TOGGLE_MODE = 4;
    private static final int SLOT_PAGE_PREV = 6;
    private static final int SLOT_PAGE_NEXT = 7;
    private static final int SLOT_BACK = 8;

    private final HollowCrownHubPlugin plugin;
    private final Inventory inv;
    private final boolean lockedScope;

    private HubPluginPolicyService.Scope scope = HubPluginPolicyService.Scope.HUB;
    private int page = 0;

    public HubPluginPolicyAdminMenu(HollowCrownHubPlugin plugin) {
        this(plugin, null);
    }

    public HubPluginPolicyAdminMenu(HollowCrownHubPlugin plugin, HubPluginPolicyService.Scope startingScope) {
        this.plugin = plugin;
        this.lockedScope = startingScope != null;
        if (startingScope != null) {
            this.scope = startingScope;
        }
        Component title = startingScope == null
                ? Component.text("HCH · Plugin policies", NamedTextColor.LIGHT_PURPLE)
                : Component.text("HCH · " + capitalize(startingScope.name()) + " policy", NamedTextColor.LIGHT_PURPLE);
        this.inv = Bukkit.createInventory(this, 54, title);
        rebuild();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= inv.getSize()) {
            return;
        }
        e.setCancelled(true);

        if (slot == SLOT_BACK) {
            if (lockedScope) {
                p.openInventory(new HubPluginAllocationLandingMenu(plugin).getInventory());
            } else {
                plugin.adminGui().openShell(p);
            }
            return;
        }
        if (!lockedScope) {
            if (slot == SLOT_SCOPE_HUB) {
                scope = HubPluginPolicyService.Scope.HUB;
                page = 0;
                rebuild();
                return;
            }
            if (slot == SLOT_SCOPE_LOBBY) {
                scope = HubPluginPolicyService.Scope.LOBBY;
                page = 0;
                rebuild();
                return;
            }
            if (slot == SLOT_SCOPE_MATCH) {
                scope = HubPluginPolicyService.Scope.MATCH;
                page = 0;
                rebuild();
                return;
            }
        }
        if (slot == SLOT_TOGGLE_MODE) {
            plugin.pluginPolicies().toggleMode(scope);
            rebuild();
            return;
        }
        if (slot == SLOT_PAGE_PREV) {
            page = Math.max(0, page - 1);
            rebuild();
            return;
        }
        if (slot == SLOT_PAGE_NEXT) {
            page = page + 1;
            rebuild();
            return;
        }

        // plugin books start at slot 9 to 44 inclusive (36 slots)
        if (slot >= 9 && slot <= 44) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType() != Material.BOOK) {
                return;
            }
            ItemMeta im = it.getItemMeta();
            if (im == null || im.displayName() == null) {
                return;
            }
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(im.displayName());
            String norm = name == null ? null : name.trim().toLowerCase(java.util.Locale.ROOT);
            if (norm == null || norm.isBlank()) {
                return;
            }
            if (plugin.pluginPolicies().alwaysAllowedPluginNames().contains(norm)) {
                return;
            }

            HubPluginPolicyService.ScopePolicy sp = plugin.pluginPolicies().policy(scope);
            if (sp.plugins().contains(norm)) {
                plugin.pluginPolicies().removePluginFromList(scope, norm);
            } else {
                plugin.pluginPolicies().addPluginToList(scope, norm);
            }
            rebuild();
        }
    }

    private void rebuild() {
        inv.clear();
        HubPluginPolicyService.ScopePolicy sp = plugin.pluginPolicies().policy(scope);

        if (!lockedScope) {
            inv.setItem(SLOT_SCOPE_HUB, scopeBtn("Hub", scope == HubPluginPolicyService.Scope.HUB));
            inv.setItem(SLOT_SCOPE_LOBBY, scopeBtn("Lobby", scope == HubPluginPolicyService.Scope.LOBBY));
            inv.setItem(SLOT_SCOPE_MATCH, scopeBtn("Match", scope == HubPluginPolicyService.Scope.MATCH));
        }

        inv.setItem(SLOT_TOGGLE_MODE, modeBtn(sp.mode()));
        inv.setItem(SLOT_BACK, btn(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW), List.of()));

        inv.setItem(SLOT_PAGE_PREV, btn(Material.PAPER, Component.text("Prev", NamedTextColor.GRAY), List.of()));
        inv.setItem(SLOT_PAGE_NEXT, btn(Material.PAPER, Component.text("Next", NamedTextColor.GRAY), List.of()));

        // render plugin books
        List<String> plugins = new ArrayList<>(plugin.pluginPolicies().installedPluginNamesSorted());
        // hide always-allowed
        Set<String> always = plugin.pluginPolicies().alwaysAllowedPluginNames();
        plugins.removeIf(p -> always.contains(p));

        int pageSize = 36;
        int start = page * pageSize;
        if (start >= plugins.size() && page > 0) {
            page = 0;
            start = 0;
        }
        int end = Math.min(plugins.size(), start + pageSize);
        int slot = 9;
        for (int i = start; i < end; i++) {
            String pl = plugins.get(i);
            boolean listed = sp.plugins().contains(pl);
            inv.setItem(slot++, pluginBook(pl, listed, sp.mode()));
        }

        // Help text
        inv.setItem(45, btn(Material.IRON_BARS,
                Component.text("How it works", NamedTextColor.AQUA),
                List.of(
                        Component.text("Whitelist: only listed plugins are usable here.", NamedTextColor.GRAY),
                        Component.text("Blacklist: listed plugins are blocked here.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Hub + Foundation are always allowed.", NamedTextColor.DARK_AQUA),
                        Component.text("This is server-side best-effort (commands + integrations).", NamedTextColor.DARK_GRAY)
                )));
    }

    private ItemStack scopeBtn(String name, boolean active) {
        return btn(active ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                Component.text(name, active ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                List.of());
    }

    private ItemStack modeBtn(HubPluginPolicyService.Mode mode) {
        boolean whitelist = mode == HubPluginPolicyService.Mode.WHITELIST;
        Material wool = whitelist ? Material.WHITE_WOOL : Material.BLACK_WOOL;
        Component title = Component.text(whitelist ? "Mode: WHITELIST" : "Mode: BLACKLIST",
                whitelist ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY);
        List<Component> lore = List.of(
                Component.text("Click to toggle", NamedTextColor.YELLOW),
                Component.empty(),
                Component.text(whitelist ? "Listed = allowed" : "Listed = blocked", NamedTextColor.GRAY)
        );
        return btn(wool, title, lore);
    }

    private ItemStack pluginBook(String pluginName, boolean listed, HubPluginPolicyService.Mode mode) {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Component.text(pluginName, listed ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            List<Component> lore = new ArrayList<>();
            if (mode == HubPluginPolicyService.Mode.WHITELIST) {
                lore.add(Component.text(listed ? "Allowed" : "Not allowed", listed ? NamedTextColor.GREEN : NamedTextColor.RED));
            } else {
                lore.add(Component.text(listed ? "Blocked" : "Allowed", listed ? NamedTextColor.RED : NamedTextColor.GREEN));
            }
            lore.add(Component.text("Click to " + (listed ? "remove" : "add"), NamedTextColor.YELLOW));
            im.lore(lore);
            if (listed) {
                im.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private static ItemStack btn(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                im.lore(lore);
            }
            it.setItemMeta(im);
        }
        return it;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}

