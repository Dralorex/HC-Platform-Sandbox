package com.hollowcrown.hub.template;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.api.integration.IronLegacyTemplateRow;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves Hub mechanic selection for an Iron Legacy worlds.yml key before publish.
 */
public final class HubTemplatePublishGate {

    private HubTemplatePublishGate() {
    }

    public static Optional<HubTemplateEntry> resolveHubRow(HollowCrownHubPlugin plugin,
                                                            IronLegacyTemplateAdmin admin,
                                                            String canonicalIlKey) {
        if (canonicalIlKey == null || canonicalIlKey.isBlank()) {
            return Optional.empty();
        }
        String canon = canonicalIlKey.trim();
        Optional<HubTemplateEntry> direct = plugin.templates().get(canon);
        if (direct.isPresent()) {
            return direct;
        }
        String worldName = admin.listTemplates().stream()
                .filter(r -> r.key().equalsIgnoreCase(canon))
                .map(IronLegacyTemplateRow::worldName)
                .findFirst()
                .orElse(canon);
        for (HubTemplateEntry e : plugin.templates().list()) {
            if (e.templateKey().equalsIgnoreCase(canon)) {
                return Optional.of(e);
            }
            if (e.sourceWorldName() != null && e.sourceWorldName().equalsIgnoreCase(worldName)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    public static boolean canPublish(HollowCrownHubPlugin plugin,
                                     IronLegacyTemplateAdmin admin,
                                     String canonicalIlKey) {
        Optional<HubTemplateEntry> row = resolveHubRow(plugin, admin, canonicalIlKey);
        if (row.isEmpty()) {
            return true;
        }
        HubTemplateEntry e = row.get();
        return e.hasMechanicSelected() || e.isVanillaMechanic();
    }

    public static void sendMechanicRequired(CommandSender sender) {
        sender.sendMessage(Component.text(
                "No game mechanic has been selected.", NamedTextColor.RED));
        sender.sendMessage(Component.text(
                "Type yes in chat if you want a vanilla world, or use:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(
                "  /hch template worlds mechanic <world|key> <mechanicName>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "  /hch worldsgui", NamedTextColor.GRAY));
    }

    public static boolean applyVanillaIfYes(HollowCrownHubPlugin plugin,
                                            CommandSender sender,
                                            IronLegacyTemplateAdmin admin,
                                            String canonicalIlKey,
                                            String chatMessage) {
        if (chatMessage == null || !chatMessage.trim().equalsIgnoreCase("yes")) {
            return false;
        }
        Optional<HubTemplateEntry> row = resolveHubRow(plugin, admin, canonicalIlKey);
        if (row.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Link this world in Hub templates.yml first (setsource), then set mechanic or vanilla.",
                    NamedTextColor.RED));
            return false;
        }
        try {
            plugin.templates().setMechanic(row.get().templateKey(), HubMechanics.VANILLA);
            sender.sendMessage(Component.text(
                    "Set " + row.get().templateKey() + " to vanilla mechanic.", NamedTextColor.GREEN));
            return true;
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return false;
        }
    }
}
