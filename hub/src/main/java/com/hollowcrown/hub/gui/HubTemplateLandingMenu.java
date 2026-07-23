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

import java.util.List;

/**
 * Per-template landing — chooses between General Settings and the per-template plugin allocation list.
 *
 * <p>Layout: row 2 = the two tab buttons; row 4 = info, back, close.</p>
 */
public final class HubTemplateLandingMenu extends HubMenuView {

    private static final int SIZE = 36;
    private static final int SLOT_TAB_GENERAL = 11;
    private static final int SLOT_TAB_BLACKLIST = 15;
    private static final int SLOT_INFO = 28;
    private static final int SLOT_BACK = 31;
    private static final int SLOT_CLOSE = 34;

    private final HollowCrownHubPlugin plugin;
    private final String templateKey;
    private final Inventory inv;

    public String templateKey() {
        return templateKey;
    }

    public HubTemplateLandingMenu(HollowCrownHubPlugin plugin, String templateKey) {
        this.plugin = plugin;
        this.templateKey = templateKey;
        String displayName = plugin.templates().get(templateKey)
                .map(e -> e.displayName())
                .orElse(templateKey);
        this.inv = Bukkit.createInventory(this, SIZE,
                Component.text("HCH · " + displayName, NamedTextColor.LIGHT_PURPLE));
        build();
    }

    private void build() {
        inv.clear();

        inv.setItem(SLOT_TAB_GENERAL, HubPluginAllocationLandingMenu.btn(
                Material.COMMAND_BLOCK,
                Component.text("General Settings", NamedTextColor.AQUA),
                List.of(
                        Component.text("Color mode, team size, min %", NamedTextColor.GRAY),
                        Component.text("TP to editor, sneak/TAB toggles,", NamedTextColor.GRAY),
                        Component.text("alliance system.", NamedTextColor.GRAY))));

        inv.setItem(SLOT_TAB_BLACKLIST, HubPluginAllocationLandingMenu.btn(
                Material.BOOKSHELF,
                Component.text("Plugin Allocation", NamedTextColor.LIGHT_PURPLE),
                List.of(
                        Component.text("Whitelist / blacklist plugins", NamedTextColor.GRAY),
                        Component.text("for matches of this template.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Overrides global Match policy.", NamedTextColor.DARK_AQUA))));

        inv.setItem(SLOT_INFO, HubPluginAllocationLandingMenu.btn(
                Material.IRON_BARS,
                Component.text("Template: " + templateKey, NamedTextColor.YELLOW),
                List.of(
                        Component.text("Choose a settings page to configure", NamedTextColor.GRAY),
                        Component.text("how this template runs.", NamedTextColor.GRAY))));

        inv.setItem(SLOT_BACK, HubPluginAllocationLandingMenu.btn(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Template list", NamedTextColor.GRAY))));

        inv.setItem(SLOT_CLOSE, HubPluginAllocationLandingMenu.btn(
                Material.BARRIER,
                Component.text("Close", NamedTextColor.RED),
                List.of()));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        switch (slot) {
            case SLOT_TAB_GENERAL ->
                    p.openInventory(new HubTemplateGeneralSettingsMenu(plugin, templateKey).getInventory());
            case SLOT_TAB_BLACKLIST ->
                    p.openInventory(new HubTemplateBlacklistMenu(plugin, templateKey, 0).getInventory());
            case SLOT_BACK ->
                    p.openInventory(new HubMatchTemplateListMenu(plugin, 0).getInventory());
            case SLOT_CLOSE -> p.closeInventory();
            default -> {
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    /** Convenience for siblings to bounce back here. */
    static ItemStack itemBtn(Material m, Component name, List<Component> lore) {
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
