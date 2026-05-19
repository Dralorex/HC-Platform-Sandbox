package com.hollowcrown.hub.party;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Party invite paper, disconnect marks, and periodic party maintenance.
 */
public final class HubPartyListener implements Listener {

    private final HollowCrownHubPlugin plugin;
    private final HubPartyService parties;

    public HubPartyListener(HollowCrownHubPlugin plugin, HubPartyService parties) {
        this.plugin = plugin;
        this.parties = parties;
    }

    public static ItemStack createInvitePaper(HollowCrownHubPlugin plugin, String partyId) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Party Invite", NamedTextColor.GREEN));
            meta.lore(List.of(
                    Component.text("Left-click: accept", NamedTextColor.GREEN),
                    Component.text("Right-click: decline", NamedTextColor.RED)
            ));
            meta.getPersistentDataContainer().set(
                    plugin.partyInviteKey(),
                    PersistentDataType.STRING,
                    partyId);
            paper.setItemMeta(meta);
        }
        return paper;
    }

    public static String readInvitePartyId(HollowCrownHubPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(
                plugin.partyInviteKey(),
                PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        parties.markDisconnected(id);
        plugin.matchQueueService().leaveQueue(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        parties.clearDisconnectMark(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPaperInteract(PlayerInteractEvent event) {
        Action a = event.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("hollowcrownhub.party")) {
            return;
        }
        ItemStack hand = event.getItem();
        String partyId = readInvitePartyId(plugin, hand);
        if (partyId == null) {
            return;
        }
        event.setCancelled(true);
        if (left) {
            if (parties.acceptInvite(player.getUniqueId(), partyId)) {
                player.sendMessage(Component.text("Joined party.", NamedTextColor.GREEN));
                parties.partyForMember(player.getUniqueId()).ifPresent(v -> {
                    Player leader = Bukkit.getPlayer(v.leaderId());
                    if (leader != null && leader.isOnline() && !leader.getUniqueId().equals(player.getUniqueId())) {
                        leader.showTitle(Title.title(
                                Component.text("Party", NamedTextColor.GREEN),
                                Component.text(player.getName() + " joined", NamedTextColor.GREEN),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(400))));
                    }
                });
                consumeOnePaper(player, event.getHand(), hand);
            } else {
                player.sendMessage(Component.text("That invite is no longer valid.", NamedTextColor.RED));
            }
            return;
        }
        if (parties.declineInvite(player.getUniqueId(), partyId)) {
            notifyLeaderDeclined(player, partyId);
            consumeOnePaper(player, event.getHand(), hand);
        } else {
            player.sendMessage(Component.text("That invite is no longer valid.", NamedTextColor.RED));
        }
    }

    private static void consumeOnePaper(Player player, EquipmentSlot hand, ItemStack stack) {
        int amt = stack.getAmount();
        if (amt <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        } else {
            stack.setAmount(amt - 1);
        }
    }

    private void notifyLeaderDeclined(Player chooser, String partyId) {
        parties.partyById(partyId).ifPresent(view -> {
            Player leader = Bukkit.getPlayer(view.leaderId());
            if (leader != null && leader.isOnline()) {
                leader.showTitle(Title.title(
                        Component.empty(),
                        Component.text(chooser.getName() + " declined your invite", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(400))));
            }
        });
    }
}
