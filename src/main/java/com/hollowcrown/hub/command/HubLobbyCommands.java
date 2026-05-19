package com.hollowcrown.hub.command;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.lobby.HubLobbyInstanceRecord;
import com.hollowcrown.hub.lobby.HubLobbyService;
import com.hollowcrown.hub.gui.LobbyAdminMenu;
import com.hollowcrown.hub.lobby.LobbyInstanceState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;

/**
 * {@code /hch lobby …} admin commands for lobby instance worlds.
 */
public final class HubLobbyCommands {

    private HubLobbyCommands() {
    }

    /**
     * @param args argv with {@code args[0] == "lobby"}
     */
    public static boolean handle(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.admin")) {
            sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /hch lobby list | gui | create … | destroy … | forget … | tp …",
                    NamedTextColor.YELLOW));
            return true;
        }
        HubLobbyService svc = plugin.lobbyService();
        String sub = args[1].toLowerCase();
        return switch (sub) {
            case "list" -> list(sender, svc);
            case "gui" -> gui(plugin, sender, args);
            case "create" -> create(plugin, sender, svc, args);
            case "destroy" -> destroy(plugin, sender, svc, args);
            case "forget" -> forget(sender, svc, args);
            case "tp" -> tp(sender, svc, args);
            default -> {
                sender.sendMessage(Component.text("Unknown lobby subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private static boolean gui(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        int page = 0;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]) - 1;
            } catch (NumberFormatException ignored) {
                sender.sendMessage(Component.text("Invalid page number.", NamedTextColor.RED));
                return true;
            }
        }
        LobbyAdminMenu.open(player, plugin, page);
        return true;
    }

    private static boolean list(CommandSender sender, HubLobbyService svc) {
        boolean op = sender.isOp();
        var rows = svc.listVisibleTo(op);
        sender.sendMessage(Component.text("Lobby instances (" + rows.size() + "):", NamedTextColor.GOLD));
        if (rows.isEmpty()) {
            sender.sendMessage(Component.text("  (none)", NamedTextColor.DARK_GRAY));
            return true;
        }
        for (HubLobbyInstanceRecord r : rows) {
            String extra = r.state() == LobbyInstanceState.BROKEN ? " [BROKEN]" : "";
            int players = r.loadedWorld() != null ? r.loadedWorld().getPlayerCount() : 0;
            sender.sendMessage(Component.text(
                    "  " + r.id() + " | " + r.templateKey() + " | " + r.worldName() + " | players=" + players + extra,
                    NamedTextColor.GRAY));
        }
        return true;
    }

    private static boolean create(HollowCrownHubPlugin plugin, CommandSender sender, HubLobbyService svc, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch lobby create <templateKey>", NamedTextColor.YELLOW));
            return true;
        }
        try {
            String id = svc.createLobby(args[2]);
            sender.sendMessage(Component.text("Created lobby instance id=" + id, NamedTextColor.GREEN));
            HubLog.info(plugin, "action=hch_lobby_create_cmd", "sender=" + sender.getName(), "id=" + id);
        } catch (IOException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private static boolean destroy(HollowCrownHubPlugin plugin, CommandSender sender, HubLobbyService svc, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch lobby destroy <id>", NamedTextColor.YELLOW));
            return true;
        }
        String msg = svc.destroyLobby(args[2], true);
        sender.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
        HubLog.info(plugin, "action=hch_lobby_destroy_cmd", "sender=" + sender.getName(), "target=" + args[2]);
        return true;
    }

    private static boolean forget(CommandSender sender, HubLobbyService svc, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch lobby forget <id> (BROKEN rows only)", NamedTextColor.YELLOW));
            return true;
        }
        if (svc.forgetBroken(args[2])) {
            sender.sendMessage(Component.text("Registry row removed. Delete the folder manually if it still exists.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("No BROKEN instance with that id.", NamedTextColor.RED));
        }
        return true;
    }

    private static boolean tp(CommandSender sender, HubLobbyService svc, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch lobby tp <id>", NamedTextColor.YELLOW));
            return true;
        }
        svc.teleportAdmin(player, args[2]);
        return true;
    }
}
