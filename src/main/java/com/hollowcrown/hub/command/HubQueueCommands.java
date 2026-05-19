package com.hollowcrown.hub.command;

import com.hollowcrown.api.queue.MatchEnqueueResult;
import com.hollowcrown.api.queue.MatchQueueService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /hch queue …} — template-scoped match queue (whole party units + solo merge).
 */
public final class HubQueueCommands {

    private HubQueueCommands() {
    }

    public static boolean handle(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.queue")) {
            sender.sendMessage(Component.text("You cannot use the match queue here.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            usage(sender);
            return true;
        }
        MatchQueueService mq = plugin.matchQueueService();
        String sub = args[1].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "join" -> join(plugin, sender, mq, args);
            case "leave" -> leave(sender, mq);
            case "status" -> status(sender, mq, args);
            default -> {
                sender.sendMessage(Component.text("Unknown queue subcommand.", NamedTextColor.RED));
                usage(sender);
                yield true;
            }
        };
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Queue: /hch queue join <templateKey> | leave | status [templateKey]", NamedTextColor.YELLOW));
    }

    private static boolean join(HollowCrownHubPlugin plugin, CommandSender sender, MatchQueueService mq, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch queue join <templateKey>", NamedTextColor.YELLOW));
            return true;
        }
        String template = args[2];
        MatchEnqueueResult r = mq.enqueue(player.getUniqueId(), template);
        if (r.success()) {
            player.sendMessage(Component.text("Queued for " + template + " (position ~" + r.positionOneBased() + ").", NamedTextColor.GREEN));
            HubLog.info(plugin, "action=queue_cmd_join", "player=" + player.getName(), "templateKey=" + template);
            return true;
        }
        player.sendMessage(Component.text(MatchEnqueueMessages.playerDenyMessage(r.status()), NamedTextColor.RED));
        return true;
    }

    private static boolean leave(CommandSender sender, MatchQueueService mq) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!mq.leaveQueue(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not in a queue.", NamedTextColor.YELLOW));
            return true;
        }
        player.sendMessage(Component.text("Left the queue.", NamedTextColor.GREEN));
        return true;
    }

    private static boolean status(CommandSender sender, MatchQueueService mq, String[] args) {
        String template = args.length >= 3 ? args[2] : null;
        if (template == null || template.isBlank()) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Usage: /hch queue status <templateKey> (or join a queue as a player for self-status)", NamedTextColor.YELLOW));
                return true;
            }
            mq.queuedTemplateFor(player.getUniqueId()).ifPresentOrElse(
                    tk -> {
                        player.sendMessage(Component.text("You are queued for: " + tk, NamedTextColor.AQUA));
                        printUnits(sender, mq, tk);
                    },
                    () -> player.sendMessage(Component.text("You are not in any queue.", NamedTextColor.GRAY))
            );
            return true;
        }
        sender.sendMessage(Component.text("Queue " + template + " (" + mq.queuedUnitCount(template) + " units):", NamedTextColor.GOLD));
        printUnits(sender, mq, template);
        return true;
    }

    private static void printUnits(CommandSender sender, MatchQueueService mq, String template) {
        List<String> lines = mq.describeQueue(template);
        if (lines.isEmpty()) {
            sender.sendMessage(Component.text("  (empty)", NamedTextColor.DARK_GRAY));
        } else {
            for (String line : lines) {
                sender.sendMessage(Component.text("  " + line, NamedTextColor.GRAY));
            }
        }
    }
}
