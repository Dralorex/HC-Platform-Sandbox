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

/** Live per-template queue depth + waiting units (from {@link com.hollowcrown.api.queue.MatchQueueService}). */
public final class HubQueueOverviewMenu extends HubMenuView {

    private static final int PAGE = 45;

    private final HollowCrownHubPlugin plugin;
    private final List<HubTemplateEntry> all;
    private final int page;
    private final Inventory inv;
    private final List<HubTemplateEntry> slice = new ArrayList<>();

    public HubQueueOverviewMenu(HollowCrownHubPlugin plugin, int page) {
        this.plugin = plugin;
        this.all = new ArrayList<>(plugin.templates().list());
        int maxPg = Math.max(0, (all.size() + PAGE - 1) / PAGE - 1);
        this.page = Math.min(Math.max(0, page), maxPg);
        this.inv = Bukkit.createInventory(this, 54,
                Component.text("Hub · Match queues · " + (this.page + 1), NamedTextColor.GOLD));
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
            int units = plugin.matchQueueService().queuedUnitCount(e.templateKey());
            List<String> desc = plugin.matchQueueService().describeQueue(e.templateKey());
            ItemStack hopper = new ItemStack(units > 0 ? Material.HOPPER_MINECART : Material.MINECART);
            ItemMeta im = hopper.getItemMeta();
            if (im != null) {
                im.displayName(Component.text(e.effectiveDisplayName(), NamedTextColor.YELLOW));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Key: " + e.templateKey(), NamedTextColor.GRAY));
                lore.add(Component.text("Waiting units: " + units, units > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
                lore.add(Component.empty());
                int cap = Math.min(8, desc.size());
                for (int j = 0; j < cap; j++) {
                    lore.add(Component.text(desc.get(j), NamedTextColor.DARK_AQUA));
                }
                if (desc.size() > cap) {
                    lore.add(Component.text("… +" + (desc.size() - cap) + " more", NamedTextColor.DARK_GRAY));
                }
                lore.add(Component.empty());
                lore.add(Component.text("/hch queue status " + e.templateKey(), NamedTextColor.GREEN));
                im.lore(lore);
                hopper.setItemMeta(im);
            }
            inv.setItem(slot++, hopper);
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
            tag.setItemMeta(tm);
        }
        inv.setItem(47, tag);

        ItemStack refresh = new ItemStack(Material.EMERALD);
        ItemMeta rm = refresh.getItemMeta();
        if (rm != null) {
            rm.displayName(Component.text("Refresh", NamedTextColor.GREEN));
            refresh.setItemMeta(rm);
        }
        inv.setItem(51, refresh);

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
        if (slot == 51) {
            p.openInventory(new HubQueueOverviewMenu(plugin, page).getInventory());
            return;
        }
        if (slot == 45 && page > 0) {
            p.openInventory(new HubQueueOverviewMenu(plugin, page - 1).getInventory());
            return;
        }
        int totalPg = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        if (slot == 53 && page + 1 < totalPg) {
            p.openInventory(new HubQueueOverviewMenu(plugin, page + 1).getInventory());
            return;
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}
