package com.hollowcrown.hungergames.gui;

import com.hollowcrown.api.integration.HubAdminGui;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

/** Iron Legacy admin hub: Envoy, Replenish, reload, Hub link status, and IL admin history. */
public class AdminControlCenterMenu extends MenuView {
    private final Inventory inv;

    private static final int SLOT_ENVOY = 11;
    private static final int SLOT_REPLENISH = 13;
    private static final int SLOT_RELOAD = 15;
    private static final int SLOT_HUB = 22;
    private static final int SLOT_HISTORY = 31;
    private static final int SLOT_CLOSE = 49;

    public AdminControlCenterMenu() {
        this.inv = Bukkit.createInventory(this, 54, Component.text("Iron Legacy · Admin", NamedTextColor.RED));
        build();
    }

    private ItemStack button(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            if (lore != null) {
                im.lore(lore.stream()
                        .map(LegacyComponentSerializer.legacySection()::deserialize)
                        .collect(Collectors.toList()));
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private void build() {
        inv.clear();
        var plugin = HungerGamesPlugin.get();

        inv.setItem(SLOT_ENVOY, button(Material.ENDER_EYE, "§dEnvoy Admin", List.of(
                "§7Open envoy controls for the map you are standing in.",
                "",
                "§eRequires a template or match world")));
        inv.setItem(SLOT_REPLENISH, button(Material.CHEST, "§eReplenish Admin", List.of(
                "§7Open replenish chest controls for this map.",
                "",
                "§eRequires a template or match world")));
        inv.setItem(SLOT_RELOAD, button(Material.REDSTONE, "§cReload All", List.of(
                "§7Reload configs, templates, icons, rules.",
                "§7Runs stale instance recovery.",
                "",
                "§eClick to reload")));

        boolean hubConnected = plugin.hollowGamePlatform() != null;
        boolean hubWorld = plugin.hubWorldApi().isPresent();
        boolean hubAdminGui = Bukkit.getServicesManager().getRegistration(HubAdminGui.class) != null;
        inv.setItem(SLOT_HUB, button(Material.NETHER_STAR, "§6Hollow Crown Hub", List.of(
                "§7GamePlatform (HC-Hub API): " + (hubConnected ? "§aconnected" : "§cnot connected"),
                "§7Hub world API: " + (hubWorld ? "§aactive" : "§cmissing"),
                "§7Hub admin GUIs: " + (hubAdminGui ? "§aavailable" : "§cnot loaded"),
                "",
                "§7Lobby return uses Hub spawn when §f/hch sethub §7is set.",
                "",
                "§7Remote template tools: §f/hch gui admin",
                "",
                "§eClick to refresh this panel")));

        inv.setItem(SLOT_HISTORY, button(Material.BOOK, "§fRecent Admin Changes", List.of(
                "§7Iron Legacy admin history (reloads, publishes, …).",
                "",
                "§eClick to print latest entries in chat")));

        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "§cClose", null));
    }

    private void reloadAll(Player p) {
        var plugin = HungerGamesPlugin.get();
        plugin.reloadConfig();
        try { plugin.files().reloadAll(); } catch (Throwable ignored) {}
        try { plugin.serverRole().reload(); } catch (Throwable ignored) {}
        try { plugin.sharedState().reload(); } catch (Throwable ignored) {}
        plugin.spawns().load();
        try { HungerGamesPlugin.rules().load(); } catch (Throwable ignored) {}
        try { plugin.templates().reload(); } catch (Throwable ignored) {}
        var recovery = plugin.templates().recoverStaleInstances();
        try { plugin.icons().reload(); } catch (Throwable ignored) {}
        try { plugin.worldSettings().reload(); } catch (Throwable ignored) {}
        try { plugin.teamNames().reload(); } catch (Throwable ignored) {}
        var iconReport = plugin.icons().validateIcons();

        p.sendMessage(Component.text("Reload complete.", NamedTextColor.GREEN));
        p.sendMessage(Component.text(
                "Instances recovery: scanned=" + recovery.scanned() + ", cleaned=" + recovery.cleaned()
                        + ", skipped=" + recovery.skipped() + ", failed=" + recovery.failed(),
                NamedTextColor.YELLOW));
        p.sendMessage(Component.text(
                "Icons: total=" + iconReport.total() + ", valid=" + iconReport.valid() + ", warnings=" + iconReport.warnings().size(),
                NamedTextColor.YELLOW));
        plugin.history().log(p.getName(), "reload_all", "from_admin_control_center");
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        switch (slot) {
            case SLOT_ENVOY -> {
                var tk = HungerGamesPlugin.get().templates().resolveTemplateKeyForWorld(p.getWorld().getName());
                if (tk.isEmpty()) {
                    p.sendMessage(Component.text(
                            "Stand in a template or match world to open Envoy Admin.", NamedTextColor.RED));
                    break;
                }
                p.openInventory(new EnvoyAdminMenu(tk.get()).getInventory());
            }
            case SLOT_REPLENISH -> {
                var tk = HungerGamesPlugin.get().templates().resolveTemplateKeyForWorld(p.getWorld().getName());
                if (tk.isEmpty()) {
                    p.sendMessage(Component.text(
                            "Stand in a template or match world to open Replenish Admin.", NamedTextColor.RED));
                    break;
                }
                p.openInventory(new ReplenishAdminMenu(tk.get()).getInventory());
            }
            case SLOT_RELOAD -> {
                reloadAll(p);
                build();
            }
            case SLOT_HUB -> build();
            case SLOT_HISTORY -> {
                var recent = HungerGamesPlugin.get().history().recent(8);
                if (recent.isEmpty()) {
                    p.sendMessage(Component.text("No admin history entries yet.", NamedTextColor.GRAY));
                } else {
                    p.sendMessage(Component.text("Recent Iron Legacy admin changes:", NamedTextColor.AQUA));
                    for (String line : recent) {
                        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§7- §f" + line));
                    }
                }
            }
            case SLOT_CLOSE -> p.closeInventory();
            default -> { }
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
