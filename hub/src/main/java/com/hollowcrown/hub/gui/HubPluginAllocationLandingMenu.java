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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 4-row landing for plugin/match allocation:
 *  · row 2 tabs: Hub, Lobby, Match
 *  · row 4 nav: Info (28), Back (31), Close (34)
 *
 * Hub/Lobby open the existing scope-locked {@link HubPluginPolicyAdminMenu}; Match opens the
 * template list, then drills into per-template configuration.
 */
public final class HubPluginAllocationLandingMenu extends HubMenuView {

    private static final int SIZE = 36;
    private static final int SLOT_TAB_HUB = 11;
    private static final int SLOT_TAB_LOBBY = 13;
    private static final int SLOT_TAB_MATCH = 15;
    private static final int SLOT_INFO = 28;
    private static final int SLOT_BACK = 31;
    private static final int SLOT_CLOSE = 34;

    private final HollowCrownHubPlugin plugin;
    private final Inventory inv;

    public HubPluginAllocationLandingMenu(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, SIZE,
                Component.text("HCH · Plugin Allocation", NamedTextColor.LIGHT_PURPLE));
        build();
    }

    private void build() {
        inv.clear();

        inv.setItem(SLOT_TAB_HUB, btn(Material.GRASS_BLOCK,
                Component.text("Hub", NamedTextColor.GREEN),
                List.of(
                        Component.text("Plugin allowlist / blocklist", NamedTextColor.GRAY),
                        Component.text("for the hub world.", NamedTextColor.GRAY))));

        inv.setItem(SLOT_TAB_LOBBY, btn(Material.END_PORTAL_FRAME,
                Component.text("Lobby", NamedTextColor.YELLOW),
                List.of(
                        Component.text("Plugin allowlist / blocklist", NamedTextColor.GRAY),
                        Component.text("while waiting for matches.", NamedTextColor.GRAY))));

        inv.setItem(SLOT_TAB_MATCH, btn(Material.IRON_SWORD,
                Component.text("Match", NamedTextColor.RED),
                List.of(
                        Component.text("Per-template settings + per-template", NamedTextColor.GRAY),
                        Component.text("plugin allowlist / blocklist.", NamedTextColor.GRAY))));

        inv.setItem(SLOT_INFO, btn(Material.IRON_BARS,
                Component.text("How it works", NamedTextColor.AQUA),
                List.of(
                        Component.text("Whitelist: only listed plugins are usable here.", NamedTextColor.GRAY),
                        Component.text("Blacklist: listed plugins are blocked here.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Hub + Foundation are always allowed.", NamedTextColor.DARK_AQUA),
                        Component.text("Per-template Match policy overrides", NamedTextColor.DARK_GRAY),
                        Component.text("the global Match list.", NamedTextColor.DARK_GRAY))));

        inv.setItem(SLOT_BACK, btn(Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to admin home", NamedTextColor.GRAY))));

        inv.setItem(SLOT_CLOSE, btn(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED),
                List.of()));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        switch (slot) {
            case SLOT_TAB_HUB ->
                    p.openInventory(new HubPluginPolicyAdminMenu(plugin, HubPluginPolicyService.Scope.HUB).getInventory());
            case SLOT_TAB_LOBBY ->
                    p.openInventory(new HubPluginPolicyAdminMenu(plugin, HubPluginPolicyService.Scope.LOBBY).getInventory());
            case SLOT_TAB_MATCH ->
                    p.openInventory(new HubMatchTemplateListMenu(plugin, 0).getInventory());
            case SLOT_BACK -> plugin.adminGui().openShell(p);
            case SLOT_CLOSE -> p.closeInventory();
            default -> {
            }
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
