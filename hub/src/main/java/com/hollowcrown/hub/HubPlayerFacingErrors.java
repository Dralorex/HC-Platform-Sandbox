package com.hollowcrown.hub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Player-facing failure text: ops get technical detail; everyone else gets a fixed oops line.
 */
public final class HubPlayerFacingErrors {

    public static final String OOPS =
            "OOPS... something messed up! Please inform a Team Member!";

    private HubPlayerFacingErrors() {
    }

    public static boolean maySeeTechnicalDetail(CommandSender sender) {
        return sender != null && sender.isOp();
    }

    /** Sends {@link #OOPS} to non-ops; ops get {@code detailed} (or OOPS if null/blank). */
    public static void sendFailure(CommandSender sender, String detailed) {
        if (sender == null) {
            return;
        }
        String line = maySeeTechnicalDetail(sender) && detailed != null && !detailed.isBlank()
                ? detailed
                : OOPS;
        sender.sendMessage(Component.text(line, NamedTextColor.RED));
    }

    public static void sendFailure(Player player, String detailed) {
        sendFailure((CommandSender) player, detailed);
    }
}
