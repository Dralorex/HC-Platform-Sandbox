package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import com.hollowcrown.hub.template.HubMechanics;
import com.hollowcrown.hub.template.HubTemplateRegistry;
import com.hollowcrown.hub.template.HubTemplateWorldBootstrap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * OP chat prompts from the lobby join template menu to create a new template world.
 */
public final class LobbyJoinTemplateCreateFlow {

    private LobbyJoinTemplateCreateFlow() {
    }

    public static void start(Player player, HollowCrownHubPlugin plugin, String mechanicId) {
        if (player == null || plugin == null) {
            return;
        }
        if (!player.isOp()) {
            player.sendMessage(Component.text("OP is required to create templates.", NamedTextColor.RED));
            return;
        }
        String mech = mechanicId == null ? "" : mechanicId.trim();
        player.closeInventory();
        plugin.chatInput().prompt(
                player,
                "Create template — enter file name (letters, numbers, _, -). Type cancel to abort.",
                rawKey -> onFileName(player, plugin, mech, rawKey),
                () -> player.sendMessage(Component.text("Template create cancelled.", NamedTextColor.GRAY))
        );
    }

    private static void onFileName(Player player, HollowCrownHubPlugin plugin, String mechanicId, String rawKey) {
        if (isCancel(rawKey)) {
            player.sendMessage(Component.text("Template create cancelled.", NamedTextColor.GRAY));
            return;
        }
        final String normalized;
        try {
            normalized = HubTemplateRegistry.validateTemplateKeySyntax(rawKey);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return;
        }
        if (plugin.templates().get(normalized).isPresent()) {
            player.sendMessage(Component.text(
                    "Template '" + normalized + "' already exists. Pick another file name.",
                    NamedTextColor.RED));
            return;
        }
        plugin.chatInput().prompt(
                player,
                "Enter lobby display name / name tag (shown in menus). Type cancel to abort.",
                display -> onDisplayName(player, plugin, mechanicId, normalized, display),
                () -> player.sendMessage(Component.text("Template create cancelled.", NamedTextColor.GRAY))
        );
    }

    private static void onDisplayName(
            Player player,
            HollowCrownHubPlugin plugin,
            String mechanicId,
            String normalizedKey,
            String displayRaw
    ) {
        if (isCancel(displayRaw)) {
            player.sendMessage(Component.text("Template create cancelled.", NamedTextColor.GRAY));
            return;
        }
        String display = displayRaw == null ? "" : displayRaw.trim();
        HubTemplateWorldBootstrap.Result r = HubTemplateWorldBootstrap.createIronLegacyEditableWorld(
                plugin, plugin.templates(), normalizedKey, display, player);
        if (!r.ok()) {
            player.sendMessage(Component.text(r.errorMessage(), NamedTextColor.RED));
            return;
        }

        String mechToSet = resolveMechanic(mechanicId);
        if (!mechToSet.isEmpty()) {
            try {
                plugin.templates().setMechanic(r.normalizedKey(), mechToSet);
            } catch (IllegalArgumentException ex) {
                player.sendMessage(Component.text(
                        "World created, but mechanic could not be set: " + ex.getMessage(),
                        NamedTextColor.YELLOW));
            }
        }

        if (HubTemplateWorldBootstrap.IRON_LEGACY_MECHANIC_ID.equalsIgnoreCase(mechToSet)) {
            HubIronLegacySupport.ilAdmin().ifPresent(admin -> {
                admin.ensureWorldTemplateRow(r.normalizedKey());
                if (!display.isEmpty()) {
                    admin.setDisplayName(r.normalizedKey(), display);
                }
            });
        }

        String label = display.isEmpty() ? r.normalizedKey() : display;
        player.sendMessage(Component.text(
                "Created template \"" + r.normalizedKey() + "\" (" + label + ") under "
                        + HubMechanics.displayLabel(mechToSet.isEmpty() ? mechanicId : mechToSet)
                        + ". Creative editor — publish when ready.",
                NamedTextColor.GREEN));
        HubLog.info(plugin, "action=lobby_join_tpl_create",
                "player=" + player.getName(),
                "templateKey=" + r.normalizedKey(),
                "mechanicId=" + mechToSet);
    }

    private static String resolveMechanic(String mechanicId) {
        if (mechanicId == null || mechanicId.isBlank()) {
            return "";
        }
        if (HubMechanics.isVanilla(mechanicId)) {
            return HubMechanics.VANILLA;
        }
        return mechanicId.trim().toLowerCase();
    }

    private static boolean isCancel(String input) {
        return input == null || input.isBlank() || input.equalsIgnoreCase("cancel");
    }
}
