package com.hollowcrown.hub.gui;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.api.integration.IronLegacyTemplateRow;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.command.HubIlAdminHints;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import java.util.Optional;

/** Per-template actions (publish, rollback path, teleport). */
public final class HubIlTemplateDetailMenu extends HubMenuView {

    private final HollowCrownHubPlugin plugin;
    private final IronLegacyTemplateAdmin admin;
    private final String templateKey;
    private final int listPageReturn;
    private final Inventory inv;

    public HubIlTemplateDetailMenu(HollowCrownHubPlugin plugin, IronLegacyTemplateAdmin admin, String templateKey, int listPageReturn) {
        this.plugin = plugin;
        this.admin = admin;
        this.templateKey = templateKey;
        this.listPageReturn = listPageReturn;
        this.inv = Bukkit.createInventory(this, 54,
                Component.text("IL · " + templateKey, NamedTextColor.GOLD));
        build();
    }

    private Optional<IronLegacyTemplateRow> row() {
        return admin.listTemplates().stream()
                .filter(r -> r.key().equalsIgnoreCase(templateKey))
                .findFirst();
    }

    private static ItemStack btn(Material m, String name, List<String> loreLines) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            if (loreLines != null) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(LegacyComponentSerializer.legacySection().deserialize(line));
                }
                im.lore(lore);
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private void build() {
        inv.clear();
        Optional<IronLegacyTemplateRow> inf = row();
        ItemStack head = admin.buildIconPreview(inf.map(IronLegacyTemplateRow::iconKey).orElse("default"));
        ItemMeta hm = head.getItemMeta();
        if (hm != null) {
            hm.displayName(Component.text(inf.map(IronLegacyTemplateRow::displayName).orElse(templateKey), NamedTextColor.YELLOW));
            List<Component> lore = new ArrayList<>();
            inf.ifPresent(r -> {
                lore.add(Component.text("World: " + r.worldName(), NamedTextColor.GRAY));
                lore.add(Component.text("Status: " + r.status() + " · v" + r.version(), NamedTextColor.GRAY));
                lore.add(Component.text("Enabled: " + r.enabled(), NamedTextColor.GRAY));
            });
            hm.lore(lore);
            head.setItemMeta(hm);
        }
        inv.setItem(13, head);

        inv.setItem(29, btn(Material.ENDER_PEARL, "§aTeleport editor",
                List.of("§7Creative + IL inventory rules")));
        inv.setItem(31, btn(Material.EMERALD_BLOCK, "§aPublish",
                List.of("§7Snapshot + mark PUBLISHED")));
        inv.setItem(33, btn(Material.TNT, "§cDelete template",
                List.of("§7Requires empty world")));
        inv.setItem(38, btn(Material.CLOCK, "§eSnapshots / rollback",
                List.of("§7Browse snapshot IDs")));
        inv.setItem(40, btn(Material.LEVER, "§eToggle enabled",
                List.of("§7Flip worlds.yml enabled")));
        inv.setItem(42, btn(Material.PAINTING, "§bIcon preview",
                List.of("§7Opens preview chest")));
        inv.setItem(43, btn(Material.GOLDEN_HOE, "§6Lobby circle wand",
                List.of("§7Same as §f/hch template lobby wand",
                        "§7Center + edge in §fthis§7 template world")));
        inv.setItem(44, btn(Material.RECOVERY_COMPASS, "§bLimbo spawn here",
                List.of("§7Same as §f/hch template limbo spawn",
                        "§7Stand §binside§7 saved zone")));
        inv.setItem(48, btn(Material.ARROW, "§7Back to list", List.of()));
        inv.setItem(53, btn(Material.BARRIER, "§cClose", List.of()));
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
        if (slot == 48) {
            p.openInventory(new HubIlTemplateListMenu(plugin, admin, listPageReturn).getInventory());
            return;
        }
        if (slot == 29) {
            if (admin.teleportToEditor(p, templateKey)) {
                p.sendMessage(Component.text("Editor: " + templateKey, NamedTextColor.GREEN));
            } else {
                p.sendMessage(Component.text("Teleport failed.", NamedTextColor.RED));
            }
            p.closeInventory();
            return;
        }
        if (slot == 31) {
            if (admin.publish(templateKey)) {
                p.sendMessage(Component.text("Published.", NamedTextColor.GREEN));
            } else {
                p.sendMessage(Component.text("Publish failed.", NamedTextColor.RED));
                HubIlAdminHints.explainPublishFailure(p, admin, templateKey);
            }
            refresh(p);
            return;
        }
        if (slot == 33) {
            if (admin.delete(templateKey)) {
                p.sendMessage(Component.text("Deleted.", NamedTextColor.GREEN));
                p.openInventory(new HubIlTemplateListMenu(plugin, admin, listPageReturn).getInventory());
            } else {
                p.sendMessage(Component.text("Delete failed.", NamedTextColor.RED));
            }
            return;
        }
        if (slot == 38) {
            p.openInventory(new HubIlSnapshotListMenu(plugin, admin, templateKey, 0, listPageReturn).getInventory());
            return;
        }
        if (slot == 40) {
            boolean cur = row().map(IronLegacyTemplateRow::enabled).orElse(true);
            admin.setEnabled(templateKey, !cur);
            refresh(p);
            return;
        }
        if (slot == 42) {
            p.openInventory(new HubIlIconPreviewMenu(admin.buildIconPreview(row().map(IronLegacyTemplateRow::iconKey).orElse("default")))
                    .getInventory());
            return;
        }
        if (slot == 43) {
            if (plugin.limboLayout().giveLimboWand(p)) {
                p.sendMessage(Component.text(
                        "Lobby circle wand added. Left=center, Right=edge, Shift+scroll cycles, Shift-right saves.",
                        NamedTextColor.GREEN));
            } else {
                p.sendMessage(Component.text("Could not give wand (invalid key or inventory full?).", NamedTextColor.RED));
            }
            return;
        }
        if (slot == 44) {
            if (plugin.limboLayout().setLimboSpawnHere(p)) {
                p.sendMessage(Component.text("Limbo spawn saved for this template.", NamedTextColor.GREEN));
            } else {
                p.sendMessage(Component.text(
                        "Failed — teleport into this template world and stand inside the saved limbo zone.",
                        NamedTextColor.RED));
            }
            return;
        }
    }

    private void refresh(Player p) {
        p.openInventory(new HubIlTemplateDetailMenu(plugin, admin, templateKey, listPageReturn).getInventory());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}
