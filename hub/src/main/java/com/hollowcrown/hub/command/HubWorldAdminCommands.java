package com.hollowcrown.hub.command;

import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Admin tuning for the physical hub area (/hch hubworld …).
 */
public final class HubWorldAdminCommands {

    private HubWorldAdminCommands() {
    }

    public static boolean handle(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.admin")) {
            sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
            return true;
        }
        HubWorldApi api = plugin.hubWorld();
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "size" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /hch hubworld size <blocks>", NamedTextColor.YELLOW));
                    return true;
                }
                if (!(sender instanceof Player p) || !p.isOp()) {
                    sender.sendMessage(Component.text("OP players only (in-game).", NamedTextColor.RED));
                    return true;
                }
                try {
                    double size = Math.max(1.0, Double.parseDouble(args[2]));
                    api.setProtectionRadiusBlocks(size);
                    HubLog.info(plugin, "action=hch_hubworld_size", "sender=" + sender.getName(), "size=" + size);
                    sender.sendMessage(Component.text("Hub protection radius set to " + size + " blocks.", NamedTextColor.GREEN));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Number expected.", NamedTextColor.RED));
                }
                return true;
            }
            case "buildmode", "bm" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /hch hubworld buildmode <true|false>", NamedTextColor.YELLOW));
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (!p.isOp()) {
                    sender.sendMessage(Component.text("OPs only.", NamedTextColor.RED));
                    return true;
                }
                String v = args[2].toLowerCase();
                boolean on = v.equals("true") || v.equals("t") || v.equals("on") || v.equals("1");
                boolean off = v.equals("false") || v.equals("f") || v.equals("off") || v.equals("0");
                if (!on && !off) {
                    sender.sendMessage(Component.text("Use true or false.", NamedTextColor.RED));
                    return true;
                }
                api.setHubBuildMode(p, on);
                HubLog.info(plugin, "action=hch_hubworld_buildmode", "sender=" + p.getName(), "on=" + on);
                sender.sendMessage(Component.text("Hub build mode: " + on, NamedTextColor.GREEN));
                return true;
            }
            case "pvp" -> {
                if (args.length < 3) {
                    sendPvpUsage(sender);
                    return true;
                }
                return handlePvpSub(plugin, sender, args[2].toLowerCase());
            }
            case "hostilemobs", "hostilemobspawn", "hostilemobspawns" -> {
                return mobToggle(sender, args, api, plugin, true);
            }
            case "passivemobs", "passivemobspawn", "passivemobspawns" -> {
                return mobToggle(sender, args, api, plugin, false);
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }
    }

    private static boolean handlePvpSub(HollowCrownHubPlugin plugin, CommandSender sender, String pvpSub) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!p.isOp()) {
            sender.sendMessage(Component.text("OPs only.", NamedTextColor.RED));
            return true;
        }
        switch (pvpSub) {
            case "wand" -> {
                p.getInventory().addItem(
                        com.hollowcrown.hub.world.HubPvpZoneWandListener.createWand(plugin));
                HubLog.info(plugin, "action=hch_hubworld_pvp_wand", "sender=" + p.getName());
                sender.sendMessage(Component.text("Given PvP zone wand.", NamedTextColor.GREEN));
            }
            case "setspawn" -> {
                if (plugin.hubWorld().savePvpZoneSpawn(p.getLocation())) {
                    HubLog.info(plugin, "action=hch_hubworld_pvp_setspawn", "sender=" + p.getName());
                    sender.sendMessage(Component.text("PvP zone spawn set at your location.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Failed to set PvP zone spawn.", NamedTextColor.RED));
                }
            }
            case "pad" -> {
                plugin.hubWorld().givePvpPadItem(p);
                HubLog.info(plugin, "action=hch_hubworld_pvp_pad", "sender=" + p.getName());
                sender.sendMessage(Component.text("Given PvP pad item.", NamedTextColor.GREEN));
            }
            default -> sendPvpUsage(sender);
        }
        return true;
    }

    private static boolean mobToggle(CommandSender sender, String[] args, HubWorldApi api, HollowCrownHubPlugin plugin, boolean hostile) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch hubworld " + args[1] + " <true|false>", NamedTextColor.YELLOW));
            return true;
        }
        if (!(sender instanceof Player p) || !p.isOp()) {
            sender.sendMessage(Component.text("OP players only (in-game).", NamedTextColor.RED));
            return true;
        }
        String v = args[2].toLowerCase();
        boolean on = v.equals("true") || v.equals("t") || v.equals("on") || v.equals("1");
        boolean off = v.equals("false") || v.equals("f") || v.equals("off") || v.equals("0");
        if (!on && !off) {
            sender.sendMessage(Component.text("Use true or false.", NamedTextColor.RED));
            return true;
        }
        boolean value = on;
        if (hostile) {
            api.setAllowHostileMobSpawnsInHub(value);
        } else {
            api.setAllowPassiveMobSpawnsInHub(value);
        }
        HubLog.info(plugin, "action=hch_hubworld_mobs", "sender=" + sender.getName(),
                "hostile=" + hostile, "value=" + value);
        sender.sendMessage(Component.text(
                (hostile ? "Hostile" : "Passive") + " mob spawns in hub: " + value,
                NamedTextColor.GREEN));
        return true;
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Hub world (protection / hotbar PvP area):", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /hch hubworld size <blocks>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /hch hubworld buildmode <true|false>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /hch hubworld pvp wand|setspawn|pad", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /hch hubworld hostilemobs|passivemobs <true|false>", NamedTextColor.GRAY));
    }

    private static void sendPvpUsage(CommandSender sender) {
        sender.sendMessage(Component.text("PvP zone commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /hch hubworld pvp wand", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /hch hubworld pvp setspawn", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /hch hubworld pvp pad", NamedTextColor.GRAY));
    }

    public static List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            String p = args[1].toLowerCase();
            List<String> opts = List.of("size", "buildmode", "pvp", "hostilemobs", "passivemobs");
            return opts.stream().filter(o -> o.startsWith(p)).toList();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("pvp")) {
            String p = args[2].toLowerCase();
            return List.of("wand", "setspawn", "pad").stream().filter(o -> o.startsWith(p)).toList();
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("buildmode") || args[1].equalsIgnoreCase("bm")
                || args[1].equalsIgnoreCase("hostilemobs") || args[1].equalsIgnoreCase("hostilemobspawn")
                || args[1].equalsIgnoreCase("passivemobs") || args[1].equalsIgnoreCase("passivemobspawn"))) {
            String p = args[2].toLowerCase();
            return List.of("true", "false").stream().filter(o -> o.startsWith(p)).toList();
        }
        return List.of();
    }
}
