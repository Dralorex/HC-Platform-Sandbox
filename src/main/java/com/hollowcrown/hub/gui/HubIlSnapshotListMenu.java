package com.hollowcrown.hub.gui;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Paginated snapshot list with rollback on click. */
public final class HubIlSnapshotListMenu extends HubMenuView {

    private static final int PAGE = 45;

    private final HollowCrownHubPlugin plugin;
    private final IronLegacyTemplateAdmin admin;
    private final String templateKey;
    private final int snapPage;
    private final int listPageReturn;
    private final Inventory inv;
    private final List<String> idsOnPage = new ArrayList<>();

    public HubIlSnapshotListMenu(HollowCrownHubPlugin plugin, IronLegacyTemplateAdmin admin,
                                 String templateKey, int snapPage, int listPageReturn) {
        this.plugin = plugin;
        this.admin = admin;
        this.templateKey = templateKey;
        this.listPageReturn = listPageReturn;
        List<String> all = admin.listSnapshotIds(templateKey);
        int maxPg = Math.max(0, (all.size() + PAGE - 1) / PAGE - 1);
        this.snapPage = Math.min(Math.max(0, snapPage), maxPg);
        this.inv = org.bukkit.Bukkit.createInventory(this, 54,
                Component.text("Snapshots · " + templateKey, NamedTextColor.AQUA));
        build(all);
    }

    private void build(List<String> all) {
        inv.clear();
        idsOnPage.clear();
        int from = snapPage * PAGE;
        int to = Math.min(from + PAGE, all.size());
        int slot = 0;
        for (int i = from; i < to; i++) {
            String id = all.get(i);
            idsOnPage.add(id);
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta im = paper.getItemMeta();
            if (im != null) {
                im.displayName(LegacyComponentSerializer.legacySection().deserialize("§f" + id));
                im.lore(List.of(
                        LegacyComponentSerializer.legacySection().deserialize("§7Rollback world to this snapshot"),
                        LegacyComponentSerializer.legacySection().deserialize("§eClick to rollback")));
                paper.setItemMeta(im);
            }
            inv.setItem(slot++, paper);
        }
        int totalPg = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta pm = prev.getItemMeta();
        if (pm != null) {
            pm.displayName(Component.text("Prev", NamedTextColor.YELLOW));
            prev.setItemMeta(pm);
        }
        ItemStack next = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta nm = next.getItemMeta();
        if (nm != null) {
            nm.displayName(Component.text("Next", NamedTextColor.YELLOW));
            next.setItemMeta(nm);
        }
        ItemStack back = new ItemStack(Material.OAK_DOOR);
        ItemMeta bm = back.getItemMeta();
        if (bm != null) {
            bm.displayName(Component.text("Back", NamedTextColor.GRAY));
            back.setItemMeta(bm);
        }
        inv.setItem(45, prev);
        inv.setItem(49, back);
        inv.setItem(53, next);
        // filler lore page indicator on slot 47
        ItemStack info = new ItemStack(Material.NAME_TAG);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.displayName(Component.text("Page " + (snapPage + 1) + "/" + totalPg, NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Snapshots: " + all.size(), NamedTextColor.GRAY));
            im.lore(lore);
            info.setItemMeta(im);
        }
        inv.setItem(47, info);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot == 49) {
            p.openInventory(new HubIlTemplateDetailMenu(plugin, admin, templateKey, listPageReturn).getInventory());
            return;
        }
        if (slot == 45 && snapPage > 0) {
            p.openInventory(new HubIlSnapshotListMenu(plugin, admin, templateKey, snapPage - 1, listPageReturn).getInventory());
            return;
        }
        int total = admin.listSnapshotIds(templateKey).size();
        int totalPg = Math.max(1, (total + PAGE - 1) / PAGE);
        if (slot == 53 && snapPage + 1 < totalPg) {
            p.openInventory(new HubIlSnapshotListMenu(plugin, admin, templateKey, snapPage + 1, listPageReturn).getInventory());
            return;
        }
        if (slot >= 0 && slot < idsOnPage.size()) {
            String snapId = idsOnPage.get(slot);
            if (admin.rollback(templateKey, snapId)) {
                p.sendMessage(Component.text("Rolled back to " + snapId, NamedTextColor.GREEN));
            } else {
                p.sendMessage(Component.text("Rollback failed (players inside world?).", NamedTextColor.RED));
            }
            p.openInventory(new HubIlTemplateDetailMenu(plugin, admin, templateKey, listPageReturn).getInventory());
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}
