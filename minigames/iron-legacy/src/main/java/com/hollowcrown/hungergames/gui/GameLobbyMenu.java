package com.hollowcrown.hungergames.gui;

import com.hollowcrown.api.integration.HubAdminGui;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.managers.TemplateWorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/** Player-facing game lobby world selector (right-click lobby compass). */
public class GameLobbyMenu extends MenuView {
    private final Inventory inv;
    private final List<TemplateWorldManager.TemplateInfo> entries = new ArrayList<>();
    private final boolean backToGameAdmin;

    public GameLobbyMenu() {
        this(false);
    }

    public GameLobbyMenu(boolean backToGameAdmin) {
        this.backToGameAdmin = backToGameAdmin;
        this.inv = Bukkit.createInventory(this, 54, Component.text("Game Lobby", NamedTextColor.AQUA));
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
        entries.clear();
        var all = HungerGamesPlugin.get().templates().listTemplateInfos();
        for (var info : all) {
            if (!info.enabled()) continue;
            entries.add(info);
        }

        int slot = 0;
        for (var info : entries) {
            if (slot >= 45) break;
            inv.setItem(slot, HungerGamesPlugin.get().icons().buildIcon(info.iconKey(), "§b" + info.displayName(), List.of(
                    "§7World: §f" + info.worldName(),
                    "§7Status: §f" + info.status(),
                    "§7Max Players: §f" + info.maxPlayers(),
                    "§7Version: §f" + info.version(),
                    "",
                    "§eClick to join"
            )));
            slot++;
        }

        inv.setItem(49, button(Material.BARRIER, backToGameAdmin ? "§cBack" : "§cClose", null));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 49) {
            if (backToGameAdmin) {
                var reg = Bukkit.getServicesManager().getRegistration(HubAdminGui.class);
                if (reg != null) {
                    reg.getProvider().openIronLegacyTemplates(p, 0);
                } else {
                    p.closeInventory();
                }
            }
            else p.closeInventory();
            return;
        }
        if (slot < 0 || slot >= entries.size()) return;
        var selected = entries.get(slot);
        int partyMembers = HungerGamesPlugin.get().hollowPartyService()
                .flatMap(ps -> ps.partyForMember(p.getUniqueId()))
                .map(PartyView::memberCount)
                .orElse(1);
        int maxTeamSize = Math.max(1, selected.teamSizeMax());
        if (partyMembers > maxTeamSize) {
            p.closeInventory();
            p.showTitle(Title.title(
                    Component.text("Party is to Big", NamedTextColor.RED),
                    Component.text("Max party size " + maxTeamSize, NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(150))
            ));
            return;
        }

        if (HungerGamesPlugin.get().serverRole().velocityEnabled()
                && HungerGamesPlugin.get().serverRole().isHubLike()) {
            String targetServer = HungerGamesPlugin.get().templates().resolveServerTarget(selected.key());
            boolean sent = HungerGamesPlugin.get().velocity().connectPlayer(p, targetServer);
            if (!sent) {
                p.sendMessage(Component.text("Could not route you to " + targetServer + ".", NamedTextColor.RED));
                return;
            }
            p.sendMessage(Component.text("Sending you to " + targetServer + "...", NamedTextColor.AQUA));
            p.closeInventory();
            return;
        }

        World w = Bukkit.getWorld(selected.worldName());
        if (w == null) {
            // Allow lobby routing even if target world is lazily loaded later.
            boolean queued = HungerGamesPlugin.get().preMatchLobby().sendToLobby(p, selected.key());
            if (!queued) {
                p.sendMessage(Component.text("World is currently unavailable.", NamedTextColor.RED));
                return;
            }
            p.sendMessage(Component.text("Joined lobby for " + selected.displayName(), NamedTextColor.GREEN));
            p.closeInventory();
            return;
        }
        boolean queued = HungerGamesPlugin.get().preMatchLobby().sendToLobby(p, selected.key());
        if (!queued) {
            HungerGamesPlugin.get().game().markJoinIntentCounted(p);
            p.teleport(w.getSpawnLocation().clone().add(0.5, 0.0, 0.5));
            p.sendMessage(Component.text("Joined " + selected.displayName(), NamedTextColor.GREEN));
            p.closeInventory();
            return;
        }
        p.sendMessage(Component.text("Joined lobby for " + selected.displayName(), NamedTextColor.GREEN));
        p.closeInventory();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}

