package com.hollowcrown.hub.command;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.gui.HubIlTemplateListMenu;
import com.hollowcrown.hub.gui.LobbyAdminMenu;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * {@code /hch gui …} — opens Hollow Crown Hub admin GUIs (multi-tab shell + subsystem screens).
 */
public final class HubGuiCommands {

    private HubGuiCommands() {
    }

    public static boolean handle(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.admin")) {
            sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("GUI commands are in-game only.", NamedTextColor.RED));
            return true;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "admin";
        if (sub.equals("worldsgui") || sub.equals("worldgui") || sub.equals("worlds")) {
            return openWorldsGui(plugin, sender);
        }
        return switch (sub) {
            case "help", "?" -> {
                help(sender);
                yield true;
            }
            case "admin", "home", "shell" -> {
                plugin.adminGui().openShell(p);
                yield true;
            }
            case "maps", "ironlegacy", "il" -> {
                HubIronLegacySupport.ilAdmin().ifPresentOrElse(
                        admin -> p.openInventory(new HubIlTemplateListMenu(plugin, admin, 0).getInventory()),
                        () -> p.sendMessage(Component.text(
                                "Iron Legacy admin API unavailable on this server.",
                                NamedTextColor.RED)));
                yield true;
            }
            case "templates", "hubtemplates", "tpl" -> {
                plugin.adminGui().openHubTemplatesBrowser(p);
                yield true;
            }
            case "queues", "queue", "q" -> {
                plugin.adminGui().openQueueOverview(p);
                yield true;
            }
            case "lobby", "lobbies", "l" -> {
                LobbyAdminMenu.open(p, plugin, 0);
                yield true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown GUI. /hch gui help", NamedTextColor.RED));
                yield true;
            }
        };
    }

    public static boolean openWorldsGui(HollowCrownHubPlugin plugin, CommandSender sender) {
        if (!sender.hasPermission("hollowcrownhub.admin")) {
            sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        var il = com.hollowcrown.hub.integration.HubIronLegacySupport.ilAdmin();
        if (il.isEmpty()) {
            sender.sendMessage(Component.text("Iron Legacy admin API unavailable.", NamedTextColor.RED));
            return true;
        }
        p.openInventory(new HubIlTemplateListMenu(plugin, il.get(), 0).getInventory());
        return true;
    }

    private static void help(CommandSender sender) {
        sender.sendMessage(Component.text("Hub admin GUIs (/hch gui)", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" worldsgui — Iron Legacy template list & actions", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" admin — multi-tab home (Iron Legacy, templates, queues, lobby)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" maps — same as worldsgui (aliases: ironlegacy, il)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" templates — browse Hub templates.yml rows", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" queues — live queue depth per template", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" lobby — lobby instance admin chest", NamedTextColor.GRAY));
    }

    public static void tabGui(String[] args, java.util.List<String> out) {
        if (args.length == 2) {
            String p = args[1].toLowerCase(Locale.ROOT);
            for (String c : java.util.List.of("help", "admin", "maps", "ironlegacy", "templates", "queues", "lobby")) {
                if (c.startsWith(p)) {
                    out.add(c);
                }
            }
        }
    }
}
