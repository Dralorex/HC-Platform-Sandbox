package com.hollowcrown.hub.nametag;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Chat + death message prefixing. Chat is rendered per-viewer so the player's name colour reflects
 * the viewer's relationship to the speaker. Death messages are rewritten once (server-broadcast);
 * per-viewer death messages would need packet-level intercepts so we use the speaker's own
 * perspective for the prefix and accept that the player-name colour reads as "self".
 */
public final class HubNametagChatListener implements Listener {

    private final HubNametagService nametags;

    public HubNametagChatListener(HubNametagService nametags) {
        this.nametags = nametags;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player sender = e.getPlayer();
        Component body = e.message();
        e.renderer((source, sourceDisplayName, message, viewer) -> {
            Player viewerPlayer = viewer instanceof Player p ? p : null;
            Component named = nametags.renderPlayerName(viewerPlayer, sender);
            return Component.empty()
                    .append(named)
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(body);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent e) {
        Component dm = e.deathMessage();
        if (dm == null) return;
        Player victim = e.getEntity();
        Component prefixed = nametags.renderPlayerName(null, victim);
        Component replaced = dm.replaceText(builder ->
                builder.matchLiteral(victim.getName())
                        .replacement(prefixed)
                        .once());
        if (replaced != null) {
            e.deathMessage(replaced);
        }
    }
}
