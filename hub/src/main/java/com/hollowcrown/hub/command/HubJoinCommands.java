package com.hollowcrown.hub.command;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.gui.LobbyJoinMechanicMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /hch join …} — player lobby join GUI (no admin permission).
 */
public final class HubJoinCommands {

    private HubJoinCommands() {
    }

    /**
     * @param args argv with {@code args[0] == "join"}
     */
    public static boolean handle(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.join")) {
            sender.sendMessage(Component.text("You cannot use join here.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        int page = 0;
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("gui")) {
                if (args.length >= 3) {
                    try {
                        page = Integer.parseInt(args[2]) - 1;
                    } catch (NumberFormatException ignored) {
                        sender.sendMessage(Component.text("Invalid page number.", NamedTextColor.RED));
                        return true;
                    }
                }
            } else {
                sender.sendMessage(Component.text("Usage: /hch join | /hch join gui [page]", NamedTextColor.YELLOW));
                return true;
            }
        }
        LobbyJoinMechanicMenu.open(player, plugin, page);
        return true;
    }
}
