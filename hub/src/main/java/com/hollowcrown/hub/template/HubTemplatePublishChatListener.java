package com.hollowcrown.hub.template;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.command.HubIlWorldInput;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * When publish was blocked for missing mechanic, typing {@code yes} sets vanilla and retries publish.
 */
public final class HubTemplatePublishChatListener implements Listener {

    private final HollowCrownHubPlugin plugin;

    public HubTemplatePublishChatListener(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        var pendingOpt = plugin.templatePublishPending().consumeYes(player.getUniqueId(), plain);
        if (pendingOpt.isEmpty()) {
            return;
        }
        if (!player.hasPermission("hollowcrownhub.admin")) {
            return;
        }
        var ilOpt = HubIronLegacySupport.ilAdmin();
        if (ilOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    "Iron Legacy admin API unavailable — vanilla was not applied.",
                    NamedTextColor.RED));
            return;
        }
        String canonKey = pendingOpt.get();
        var admin = ilOpt.get();
        if (!HubTemplatePublishGate.applyVanillaIfYes(plugin, player, admin, canonKey, plain)) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (admin.publish(canonKey)) {
                player.sendMessage(Component.text(
                        "Published (vanilla): " + HubIlWorldInput.folderLabel(admin, canonKey),
                        NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(
                        "Vanilla mechanic set, but publish failed. Run /hch template worlds publish "
                                + canonKey + " again.",
                        NamedTextColor.YELLOW));
            }
        });
    }
}
