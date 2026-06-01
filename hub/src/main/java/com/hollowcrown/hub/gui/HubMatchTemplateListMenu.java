package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Alphabetical, paginated list of templates registered with the Hub registry. Clicking a row opens
 * the per-template landing ({@link HubTemplateLandingMenu}). 4 rows total; nav row mirrors:
 *
 * <ul>
 *   <li>slot 27: Prev (only when page > 0)</li>
 *   <li>slot 31: Back to landing</li>
 *   <li>slot 35: Next (only when more templates remain)</li>
 * </ul>
 */
public final class HubMatchTemplateListMenu extends HubMenuView {

    private static final int SIZE = 36;
    private static final int CONTENT_SLOTS = 27;
    private static final int SLOT_PREV = 27;
    private static final int SLOT_BACK = 31;
    private static final int SLOT_NEXT = 35;

    private final HollowCrownHubPlugin plugin;
    private final int page;
    private final Inventory inv;
    private final Map<Integer, String> slotToTemplate = new HashMap<>();

    public HubMatchTemplateListMenu(HollowCrownHubPlugin plugin, int page) {
        this.plugin = plugin;
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, SIZE,
                Component.text("HCH · Match Templates", NamedTextColor.LIGHT_PURPLE));
        build();
    }

    private List<String> sortedTemplateKeys() {
        List<String> out = new ArrayList<>();
        plugin.templates().list().forEach(e -> out.add(e.templateKey()));
        plugin.templatePolicies().snapshot().keySet().forEach(k -> {
            if (!out.contains(k)) out.add(k);
        });
        out.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));
        return out;
    }

    private void build() {
        inv.clear();
        slotToTemplate.clear();
        List<String> all = sortedTemplateKeys();

        int start = page * CONTENT_SLOTS;
        if (start >= all.size() && page > 0) {
            start = 0;
        }
        int end = Math.min(all.size(), start + CONTENT_SLOTS);

        int slot = 0;
        for (int i = start; i < end; i++) {
            String key = all.get(i);
            String displayName = plugin.templates().get(key).map(e -> e.displayName()).orElse(key);
            inv.setItem(slot, templateBtn(key, displayName));
            slotToTemplate.put(slot, key);
            slot++;
        }

        if (page > 0) {
            inv.setItem(SLOT_PREV, btn(Material.ARROW,
                    Component.text("Previous page", NamedTextColor.GRAY),
                    List.of(Component.text("Page " + page, NamedTextColor.DARK_GRAY))));
        }
        inv.setItem(SLOT_BACK, btn(Material.BARRIER,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Plugin allocation home", NamedTextColor.GRAY))));
        if (end < all.size()) {
            inv.setItem(SLOT_NEXT, btn(Material.ARROW,
                    Component.text("Next page", NamedTextColor.GRAY),
                    List.of(Component.text("Page " + (page + 2), NamedTextColor.DARK_GRAY))));
        }
    }

    private ItemStack templateBtn(String key, String displayName) {
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Component.text(displayName == null || displayName.isBlank() ? key : displayName,
                    NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Key: " + key, NamedTextColor.DARK_AQUA));
            lore.add(Component.empty());
            lore.add(Component.text("Click to configure this template", NamedTextColor.GREEN));
            im.lore(lore);
            it.setItemMeta(im);
        }
        return it;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == SLOT_BACK) {
            p.openInventory(new HubPluginAllocationLandingMenu(plugin).getInventory());
            return;
        }
        if (slot == SLOT_PREV && page > 0) {
            p.openInventory(new HubMatchTemplateListMenu(plugin, page - 1).getInventory());
            return;
        }
        if (slot == SLOT_NEXT) {
            p.openInventory(new HubMatchTemplateListMenu(plugin, page + 1).getInventory());
            return;
        }
        String key = slotToTemplate.get(slot);
        if (key != null) {
            p.openInventory(new HubTemplateLandingMenu(plugin, key).getInventory());
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    static ItemStack btn(Material m, Component name, List<Component> lore) {
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
}
