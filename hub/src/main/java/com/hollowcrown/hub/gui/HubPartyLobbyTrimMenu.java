package com.hollowcrown.hub.gui;

import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.lobby.HubLobbyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shown when a party leader tries to join a specific lobby that cannot fit the whole party.
 * Click member heads to remove from party (last-joined members listed first for convenience).
 */
public final class HubPartyLobbyTrimMenu extends HubMenuView {

    private static final int SIZE = 54;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_CONFIRM = 49;
    private static final int SLOT_CANCEL = 45;
    private static final int MEMBER_START = 9;
    private static final int MEMBER_SLOTS = 27;

    private final HollowCrownHubPlugin plugin;
    private final String targetLobbyInstanceId;
    private final int maxUnitSize;
    private final Inventory inv;

    public HubPartyLobbyTrimMenu(HollowCrownHubPlugin plugin, Player leader, String instanceId, int maxUnitSize) {
        this.plugin = plugin;
        this.targetLobbyInstanceId = instanceId;
        this.maxUnitSize = Math.max(1, maxUnitSize);
        this.inv = Bukkit.createInventory(this, SIZE,
                Component.text("Party too large — remove members", NamedTextColor.RED));
        build(leader);
    }

    private void build(Player leader) {
        inv.clear();
        PartyView party = plugin.partyService().partyForMember(leader.getUniqueId()).orElse(null);
        if (party == null) {
            return;
        }
        int needRemove = party.memberCount() - maxUnitSize;
        inv.setItem(SLOT_INFO, btn(Material.BOOK,
                Component.text("Remove " + needRemove + " player(s)", NamedTextColor.GOLD),
                List.of(
                        Component.text("This lobby fits at most " + maxUnitSize + " from your party.", NamedTextColor.GRAY),
                        Component.text("Click heads to kick (you cannot remove yourself).", NamedTextColor.GRAY),
                        Component.text("Last-joined members are listed first.", NamedTextColor.DARK_GRAY))));

        List<UUID> order = new ArrayList<>(party.membersJoinOrder());
        // Last joined first for trim suggestions
        for (int i = 0; i < order.size() / 2; i++) {
            int j = order.size() - 1 - i;
            UUID tmp = order.get(i);
            order.set(i, order.get(j));
            order.set(j, tmp);
        }
        int slot = MEMBER_START;
        for (UUID memberId : order) {
            if (slot >= MEMBER_START + MEMBER_SLOTS) {
                break;
            }
            if (memberId.equals(leader.getUniqueId())) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            String name = member != null ? member.getName() : memberId.toString().substring(0, 8);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(memberId));
                meta.displayName(Component.text(name, NamedTextColor.YELLOW));
                meta.lore(List.of(
                        Component.text("Click to remove from party", NamedTextColor.GRAY)));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        inv.setItem(SLOT_CONFIRM, btn(Material.LIME_WOOL,
                Component.text("Try join again", NamedTextColor.GREEN),
                List.of(Component.text("When your party is small enough", NamedTextColor.GRAY))));
        inv.setItem(SLOT_CANCEL, btn(Material.BARRIER,
                Component.text("Cancel", NamedTextColor.RED), List.of()));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot == SLOT_CANCEL) {
            p.closeInventory();
            return;
        }
        if (slot == SLOT_CONFIRM) {
            p.closeInventory();
            HubLobbyService svc = plugin.lobbyService();
            svc.teleportUnitIntoOpenLobby(p, targetLobbyInstanceId);
            return;
        }
        if (slot >= MEMBER_START && slot < MEMBER_START + MEMBER_SLOTS) {
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() != Material.PLAYER_HEAD) {
                return;
            }
            SkullMeta meta = (SkullMeta) it.getItemMeta();
            if (meta == null || meta.getOwningPlayer() == null) {
                return;
            }
            UUID targetId = meta.getOwningPlayer().getUniqueId();
            PartyView party = plugin.partyService().partyForMember(p.getUniqueId()).orElse(null);
            if (party == null || !party.isLeader(p.getUniqueId())) {
                return;
            }
            if (targetId.equals(p.getUniqueId())) {
                p.sendMessage(Component.text("You cannot remove yourself — leave the party instead.", NamedTextColor.RED));
                return;
            }
            plugin.partyService().kick(p.getUniqueId(), targetId);
            p.openInventory(new HubPartyLobbyTrimMenu(plugin, p, targetLobbyInstanceId, maxUnitSize).getInventory());
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    private static ItemStack btn(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(name);
            if (lore != null) {
                im.lore(lore);
            }
            it.setItemMeta(im);
        }
        return it;
    }
}
