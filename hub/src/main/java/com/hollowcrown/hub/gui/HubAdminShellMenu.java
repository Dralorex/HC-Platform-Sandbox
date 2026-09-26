package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
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

/**
 * Multi-system admin home: quick access to Iron Legacy, Hub templates, queues, and lobby tools.
 */
public final class HubAdminShellMenu extends HubMenuView {

    private final HollowCrownHubPlugin plugin;
    private final Inventory inv;

    public HubAdminShellMenu(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 54,
                Component.text("HCH · Admin · Home", NamedTextColor.GOLD));
        build();
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

    private void build() {
        inv.clear();
        int tplCount = plugin.templates().list().size();
        int ilAvail = HubIronLegacySupport.ilAdmin().map(a -> a.listTemplates().size()).orElse(-1);

        List<Component> heroLore = new ArrayList<>();
        heroLore.add(Component.text("Central place for Hollow Crown Hub tools.", NamedTextColor.GRAY));
        heroLore.add(Component.empty());
        heroLore.add(Component.text("Hub templates.yml rows: " + tplCount, NamedTextColor.DARK_AQUA));
        heroLore.add(Component.text(ilAvail >= 0 ? "Iron Legacy maps: " + ilAvail : "Iron Legacy: not connected", NamedTextColor.DARK_GREEN));

        inv.setItem(13, btn(Material.NETHER_STAR,
                Component.text("Hollow Crown Hub Admin", NamedTextColor.LIGHT_PURPLE),
                heroLore));

        inv.setItem(20, btn(Material.FILLED_MAP,
                Component.text("Iron Legacy · Maps", NamedTextColor.YELLOW),
                List.of(
                        Component.text("Template worlds, publish, snapshots,", NamedTextColor.GRAY),
                        Component.text("lobby (/hch template lobby), icons.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to open", NamedTextColor.GREEN))));

        inv.setItem(22, btn(Material.BOOKSHELF,
                Component.text("Hub · templates.yml", NamedTextColor.AQUA),
                List.of(
                        Component.text("Mechanic bindings, source worlds,", NamedTextColor.GRAY),
                        Component.text("queue policy per listing key.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to browse", NamedTextColor.GREEN))));

        inv.setItem(24, btn(Material.HOPPER,
                Component.text("Match queues", NamedTextColor.GOLD),
                List.of(
                        Component.text("FIFO units per template (parties + solos).", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click for live overview", NamedTextColor.GREEN))));

        inv.setItem(31, btn(Material.ENDER_PEARL,
                Component.text("Lobby instance worlds", NamedTextColor.GREEN),
                List.of(
                        Component.text("Create / destroy / TP to L# worlds.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click for lobby admin GUI", NamedTextColor.GREEN))));

        inv.setItem(33, btn(Material.IRON_BARS,
                Component.text("Plugin policies", NamedTextColor.LIGHT_PURPLE),
                List.of(
                        Component.text("Whitelist/blacklist server plugins", NamedTextColor.GRAY),
                        Component.text("in hub, lobby, and active matches.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to configure", NamedTextColor.GREEN))));

        inv.setItem(40, btn(Material.WRITABLE_BOOK,
                Component.text("CLI cheatsheet", NamedTextColor.GRAY),
                List.of(
                        Component.text("/hch gui <admin|maps|templates|queues|lobby>", NamedTextColor.DARK_GRAY),
                        Component.text("/hch template worlds …", NamedTextColor.DARK_GRAY))));

        inv.setItem(45, btn(Material.FILLED_MAP,
                Component.text("Tab: Iron Legacy", NamedTextColor.YELLOW),
                List.of(Component.text("Same as center tile", NamedTextColor.DARK_GRAY))));
        inv.setItem(46, btn(Material.BOOKSHELF,
                Component.text("Tab: Hub templates", NamedTextColor.AQUA),
                List.of()));
        inv.setItem(47, btn(Material.HOPPER,
                Component.text("Tab: Queues", NamedTextColor.GOLD),
                List.of()));
        inv.setItem(48, btn(Material.ENDER_PEARL,
                Component.text("Tab: Lobby admin", NamedTextColor.GREEN),
                List.of()));

        inv.setItem(52, btn(Material.EMERALD,
                Component.text("Refresh", NamedTextColor.GREEN),
                List.of(Component.text("Rebuild this menu", NamedTextColor.GRAY))));
        inv.setItem(53, btn(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED),
                List.of()));
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
        if (slot == 52) {
            plugin.adminGui().openShell(p);
            return;
        }
        if (slot == 20 || slot == 45) {
            plugin.adminGui().openIronLegacyTemplates(p, 0);
            return;
        }
        if (slot == 22 || slot == 46) {
            plugin.adminGui().openHubTemplatesBrowser(p);
            return;
        }
        if (slot == 24 || slot == 47) {
            plugin.adminGui().openQueueOverview(p);
            return;
        }
        if (slot == 31 || slot == 48) {
            plugin.adminGui().openLobbyAdminShortcut(p);
            return;
        }
        if (slot == 33) {
            plugin.adminGui().openPluginPolicyAdmin(p);
            return;
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }
}
