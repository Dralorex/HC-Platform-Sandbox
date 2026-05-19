package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.template.HubTemplateEntry;
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

/** Paginated read-only view of Hub {@code templates.yml} rows. */
public final class HubHubTemplateBrowserMenu extends HubMenuView {

    private static final int PAGE = 45;

    private final HollowCrownHubPlugin plugin;
    private final List<HubTemplateEntry> all;
    private final int page;
    private final Inventory inv;
    private final List<HubTemplateEntry> slice = new ArrayList<>();

    public HubHubTemplateBrowserMenu(HollowCrownHubPlugin plugin, int page) {
        this.plugin = plugin;
        this.all = new ArrayList<>(plugin.templates().list());
        int maxPg = Math.max(0, (all.size() + PAGE - 1) / PAGE - 1);
        this.page = Math.min(Math.max(0, page), maxPg);
        this.inv = Bukkit.createInventory(this, 54,
                Component.text("Hub · templates.yml · " + (this.page + 1), NamedTextColor.AQUA));
        build();
    }

    private void build() {
        inv.clear();
        slice.clear();
        int from = page * PAGE;
        int to = Math.min(from + PAGE, all.size());
        int slot = 0;
        for (int i = from; i < to; i++) {
            HubTemplateEntry e = all.get(i);
            slice.add(e);
            ItemStack book = new ItemStack(Material.BOOK);
            ItemMeta im = book.getItemMeta();
            if (im != null) {
                im.displayName(Component.text(e.effectiveDisplayName(), NamedTextColor.YELLOW));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Key: " + e.templateKey(), NamedTextColor.GRAY));
                lore.add(Component.text("Mechanic: " + e.mechanicId(), NamedTextColor.GRAY));
                lore.add(Component.text("Source world: " + (e.sourceWorldName().isEmpty() ? "(unset)" : e.sourceWorldName()), NamedTextColor.GRAY));
                lore.add(Component.text("Queue max unit: " + e.queueMaxUnitSize()
                        + " · soloOnly=" + e.queueSoloOnly()
                        + " · merge=" + e.queueSoloAutoMerge(), NamedTextColor.DARK_GRAY));
                lore.add(Component.text("Join prefers queue first: " + e.joinQueueFirst(), NamedTextColor.DARK_GRAY));
                lore.add(Component.empty());
                lore.add(Component.text("Tip: /hch template get " + e.templateKey(), NamedTextColor.GREEN));
                im.lore(lore);
                book.setItemMeta(im);
            }
            inv.setItem(slot++, book);
        }

        int totalPg = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta pm = prev.getItemMeta();
        if (pm != null) {
            pm.displayName(Component.text("Previous page", NamedTextColor.YELLOW));
            prev.setItemMeta(pm);
        }
        ItemStack next = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta nm = next.getItemMeta();
        if (nm != null) {
            nm.displayName(Component.text("Next page", NamedTextColor.YELLOW));
            next.setItemMeta(nm);
        }
        ItemStack home = new ItemStack(Material.NETHER_STAR);
        ItemMeta hm = home.getItemMeta();
        if (hm != null) {
            hm.displayName(Component.text("Admin home", NamedTextColor.LIGHT_PURPLE));
            hm.lore(List.of(Component.text("Back to /hch gui admin", NamedTextColor.GRAY)));
            home.setItemMeta(hm);
        }
        inv.setItem(45, prev);
        inv.setItem(49, home);
        inv.setItem(53, next);

        ItemStack tag = new ItemStack(Material.NAME_TAG);
        ItemMeta tm = tag.getItemMeta();
        if (tm != null) {
            tm.displayName(Component.text("Page " + (page + 1) + "/" + totalPg, NamedTextColor.GOLD));
            tm.lore(List.of(Component.text("Entries: " + all.size(), NamedTextColor.GRAY)));
            tag.setItemMeta(tm);
        }
        inv.setItem(47, tag);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        if (cm != null) {
            cm.displayName(Component.text("Close", NamedTextColor.RED));
            close.setItemMeta(cm);
        }
        inv.setItem(52, close);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot == 52) {
            p.closeInventory();
            return;
        }
        if (slot == 49) {
            plugin.adminGui().openShell(p);
            return;
        }
        if (slot == 45 && page > 0) {
            p.openInventory(new HubHubTemplateBrowserMenu(plugin, page - 1).getInventory());
            return;
        }
        int totalPg = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        if (slot == 53 && page + 1 < totalPg) {
            p.openInventory(new HubHubTemplateBrowserMenu(plugin, page + 1).getInventory());
            return;
        }
        if (slot >= 0 && slot < slice.size()) {
            HubTemplateEntry row = slice.get(slot);
            p.sendMessage(Component.text("Template ", NamedTextColor.GRAY)
                    .append(Component.text(row.templateKey(), NamedTextColor.WHITE))
                    .append(Component.text(" → mechanic ", NamedTextColor.GRAY))
                    .append(Component.text(row.mechanicId(), NamedTextColor.WHITE))
                    .append(Component.text(" — ", NamedTextColor.GRAY))
                    .append(Component.text("/hch template get " + row.templateKey(), NamedTextColor.YELLOW)));
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}
