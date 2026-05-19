package com.hollowcrown.hub.gui;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.api.integration.IronLegacyTemplateRow;
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
import java.util.List;

/** Paginated Iron Legacy template dashboard (was /hg game gui). */
public final class HubIlTemplateListMenu extends HubMenuView {

    private static final int PAGE_ITEMS = 45;

    private final HollowCrownHubPlugin plugin;
    private final IronLegacyTemplateAdmin admin;
    private final List<IronLegacyTemplateRow> rows;
    private final int page;
    private final Inventory inv;
    private final List<IronLegacyTemplateRow> pageSlice = new ArrayList<>();

    public HubIlTemplateListMenu(HollowCrownHubPlugin plugin, IronLegacyTemplateAdmin admin, int page) {
        this.plugin = plugin;
        this.admin = admin;
        this.rows = admin.listTemplates();
        int maxPage = Math.max(0, (rows.size() + PAGE_ITEMS - 1) / PAGE_ITEMS - 1);
        this.page = Math.min(Math.max(0, page), maxPage);
        this.inv = Bukkit.createInventory(this, 54,
                Component.text("Iron Legacy · Maps · " + (this.page + 1), NamedTextColor.GOLD));
        build();
    }

    private static ItemStack filler(Material m, Component name, List<Component> lore) {
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

    private void build() {
        inv.clear();
        pageSlice.clear();
        int from = page * PAGE_ITEMS;
        int to = Math.min(from + PAGE_ITEMS, rows.size());
        int slot = 0;
        for (int i = from; i < to; i++) {
            IronLegacyTemplateRow r = rows.get(i);
            pageSlice.add(r);
            ItemStack icon = admin.buildIconPreview(r.iconKey());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(r.displayName(), NamedTextColor.YELLOW));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Key: " + r.key(), NamedTextColor.GRAY));
                lore.add(Component.text("World: " + r.worldName(), NamedTextColor.GRAY));
                lore.add(Component.text("Status: " + r.status() + " · v" + r.version(), NamedTextColor.GRAY));
                lore.add(Component.text("Enabled: " + r.enabled(), NamedTextColor.GRAY));
                lore.add(Component.empty());
                lore.add(Component.text("Click — manage template", NamedTextColor.GREEN));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(slot++, icon);
        }

        int totalPages = Math.max(1, (rows.size() + PAGE_ITEMS - 1) / PAGE_ITEMS);
        inv.setItem(45, filler(Material.ARROW, Component.text("Previous page", NamedTextColor.YELLOW),
                List.of(Component.text("Page " + (page + 1) + "/" + totalPages, NamedTextColor.GRAY))));
        inv.setItem(46, filler(Material.EMERALD, Component.text("Reload IL caches", NamedTextColor.GREEN),
                List.of(Component.text("Configs, worlds.yml, icons", NamedTextColor.GRAY))));
        inv.setItem(47, filler(Material.BOOK, Component.text("Import from Hub YAML", NamedTextColor.AQUA),
                List.of(Component.text("Adds worlds.yml rows from Hub templates", NamedTextColor.GRAY))));
        inv.setItem(48, filler(Material.NETHER_STAR, Component.text("Admin home", NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text("Return to /hch gui admin hub", NamedTextColor.GRAY))));
        inv.setItem(49, filler(Material.SPECTRAL_ARROW, Component.text("Next page", NamedTextColor.YELLOW),
                List.of(Component.text("Page " + (page + 1) + "/" + totalPages, NamedTextColor.GRAY))));
        inv.setItem(53, filler(Material.BARRIER, Component.text("Close", NamedTextColor.RED), List.of()));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot == 53) {
            p.closeInventory();
            return;
        }
        if (slot == 45 && page > 0) {
            p.openInventory(new HubIlTemplateListMenu(plugin, admin, page - 1).getInventory());
            return;
        }
        if (slot == 49) {
            int totalPages = Math.max(1, (rows.size() + PAGE_ITEMS - 1) / PAGE_ITEMS);
            if (page + 1 < totalPages) {
                p.openInventory(new HubIlTemplateListMenu(plugin, admin, page + 1).getInventory());
            }
            return;
        }
        if (slot == 46) {
            for (String line : admin.reloadAll(p.getName())) {
                p.sendMessage(Component.text(line, NamedTextColor.GRAY));
            }
            p.sendMessage(Component.text("Reload complete.", NamedTextColor.GREEN));
            p.openInventory(new HubIlTemplateListMenu(plugin, admin, page).getInventory());
            return;
        }
        if (slot == 47) {
            var r = admin.importFromHub(false, false, null);
            p.sendMessage(Component.text("Imported new=" + r.newRowsWritten() + " metaSync=" + r.metadataSyncWritten(), NamedTextColor.GOLD));
            for (String line : r.messages()) {
                p.sendMessage(Component.text(line, NamedTextColor.DARK_GRAY));
            }
            p.openInventory(new HubIlTemplateListMenu(plugin, admin, 0).getInventory());
            return;
        }
        if (slot == 48) {
            plugin.adminGui().openShell(p);
            return;
        }
        if (slot >= 0 && slot < pageSlice.size()) {
            IronLegacyTemplateRow sel = pageSlice.get(slot);
            p.openInventory(new HubIlTemplateDetailMenu(plugin, admin, sel.key(), page).getInventory());
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}
