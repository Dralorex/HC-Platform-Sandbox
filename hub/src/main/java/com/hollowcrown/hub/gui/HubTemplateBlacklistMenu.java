package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.policy.HubPluginPolicyService;
import com.hollowcrown.hub.policy.HubTemplatePolicyService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-template plugin allocation list. Matches the global policy menu in spirit but reads/writes
 * {@link HubTemplatePolicyService} for this template only. Overrides the global Match policy when
 * a player is in a match world of this template.
 */
public final class HubTemplateBlacklistMenu extends HubMenuView {

    private static final int SIZE = 54;

    private static final int SLOT_TAB_GENERAL = 0;
    private static final int SLOT_TAB_BLACKLIST = 2;
    private static final int SLOT_TOGGLE_MODE = 4;
    private static final int SLOT_PAGE_PREV = 6;
    private static final int SLOT_PAGE_NEXT = 7;
    private static final int SLOT_BACK = 8;

    private final HollowCrownHubPlugin plugin;
    private final String templateKey;
    private final Inventory inv;
    private int page;

    public String templateKey() {
        return templateKey;
    }

    public HubTemplateBlacklistMenu(HollowCrownHubPlugin plugin, String templateKey, int page) {
        this.plugin = plugin;
        this.templateKey = templateKey;
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, SIZE,
                Component.text("HCH · " + templateKey + " · Plugins", NamedTextColor.LIGHT_PURPLE));
        rebuild();
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= inv.getSize()) return;
        e.setCancelled(true);

        if (slot == SLOT_BACK) {
            p.openInventory(new HubTemplateLandingMenu(plugin, templateKey).getInventory());
            return;
        }
        if (slot == SLOT_TAB_GENERAL) {
            p.openInventory(new HubTemplateGeneralSettingsMenu(plugin, templateKey).getInventory());
            return;
        }
        if (slot == SLOT_TAB_BLACKLIST) {
            return; // already here
        }
        if (slot == SLOT_TOGGLE_MODE) {
            plugin.templatePolicies().toggleMode(templateKey);
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

        if (slot >= 9 && slot <= 44) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType() != Material.BOOK) return;
            ItemMeta im = it.getItemMeta();
            if (im == null || im.displayName() == null) return;
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(im.displayName());
            String norm = name == null ? null : name.trim().toLowerCase(java.util.Locale.ROOT);
            if (norm == null || norm.isBlank()) return;
            if (plugin.pluginPolicies().alwaysAllowedPluginNames().contains(norm)) return;

            HubTemplatePolicyService.TemplateConfig cfg = plugin.templatePolicies().configFor(templateKey);
            if (cfg.pluginPolicyList().contains(norm)) {
                plugin.templatePolicies().removePluginFromList(templateKey, norm);
            } else {
                plugin.templatePolicies().addPluginToList(templateKey, norm);
            }
            rebuild();
        }
    }

    private void rebuild() {
        inv.clear();
        HubTemplatePolicyService.TemplateConfig cfg = plugin.templatePolicies().configFor(templateKey);

        inv.setItem(SLOT_TAB_GENERAL, btn(Material.GRAY_STAINED_GLASS_PANE,
                Component.text("General Settings", NamedTextColor.GRAY),
                List.of(Component.text("Click to switch", NamedTextColor.DARK_GRAY))));
        inv.setItem(SLOT_TAB_BLACKLIST, btn(Material.LIME_STAINED_GLASS_PANE,
                Component.text("Plugin Allocation", NamedTextColor.GREEN),
                List.of(Component.text("You are here", NamedTextColor.DARK_GREEN))));

        inv.setItem(SLOT_TOGGLE_MODE, modeBtn(cfg.pluginPolicyMode()));
        inv.setItem(SLOT_BACK, btn(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW), List.of()));
        inv.setItem(SLOT_PAGE_PREV, btn(Material.PAPER, Component.text("Prev", NamedTextColor.GRAY), List.of()));
        inv.setItem(SLOT_PAGE_NEXT, btn(Material.PAPER, Component.text("Next", NamedTextColor.GRAY), List.of()));

        List<String> plugins = new ArrayList<>(plugin.pluginPolicies().installedPluginNamesSorted());
        Set<String> always = plugin.pluginPolicies().alwaysAllowedPluginNames();
        plugins.removeIf(always::contains);

        int pageSize = 36;
        int start = page * pageSize;
        if (start >= plugins.size() && page > 0) {
            page = 0;
            start = 0;
        }
        int end = Math.min(plugins.size(), start + pageSize);
        int slot = 9;
        LinkedHashSet<String> listed = cfg.pluginPolicyList();
        for (int i = start; i < end; i++) {
            String pl = plugins.get(i);
            inv.setItem(slot++, pluginBook(pl, listed.contains(pl), cfg.pluginPolicyMode()));
        }

        inv.setItem(45, btn(Material.IRON_BARS,
                Component.text("How it works", NamedTextColor.AQUA),
                List.of(
                        Component.text("Whitelist: only listed plugins work in", NamedTextColor.GRAY),
                        Component.text("matches of this template.", NamedTextColor.GRAY),
                        Component.text("Blacklist: listed plugins are blocked.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Overrides the global Match policy.", NamedTextColor.DARK_AQUA))));
    }

    private ItemStack modeBtn(HubPluginPolicyService.Mode mode) {
        boolean whitelist = mode == HubPluginPolicyService.Mode.WHITELIST;
        Material wool = whitelist ? Material.WHITE_WOOL : Material.BLACK_WOOL;
        Component title = Component.text(whitelist ? "Mode: WHITELIST" : "Mode: BLACKLIST",
                whitelist ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY);
        List<Component> lore = List.of(
                Component.text("Click to toggle", NamedTextColor.YELLOW),
                Component.empty(),
                Component.text(whitelist ? "Listed = allowed" : "Listed = blocked", NamedTextColor.GRAY));
        return btn(wool, title, lore);
    }

    private ItemStack pluginBook(String pluginName, boolean listed, HubPluginPolicyService.Mode mode) {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Component.text(pluginName, listed ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            List<Component> lore = new ArrayList<>();
            if (mode == HubPluginPolicyService.Mode.WHITELIST) {
                lore.add(Component.text(listed ? "Allowed" : "Not allowed",
                        listed ? NamedTextColor.GREEN : NamedTextColor.RED));
            } else {
                lore.add(Component.text(listed ? "Blocked" : "Allowed",
                        listed ? NamedTextColor.RED : NamedTextColor.GREEN));
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
