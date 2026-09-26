package com.hollowcrown.hub.command;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.api.platform.GamePlatform;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import com.hollowcrown.hub.template.HubTemplateEntry;
import com.hollowcrown.hub.template.HubTemplateRegistry;
import com.hollowcrown.hub.template.HubTemplateWorldBootstrap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.StringJoiner;

/**
 * {@code /hch template …} admin subcommands.
 */
public final class HubTemplateCommands {

    private HubTemplateCommands() {
    }

    /**
     * @param args full argv; {@code args[0]} is {@code template}
     */
    public static boolean handle(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.admin")) {
            sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Template commands (/hch template)", NamedTextColor.GOLD));
            sender.sendMessage(Component.text(
                    " list · create · get · remove · worlds … · lobby … · set … · setsource · setqueue",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text(
                    " worlds list|reload|gui|importhub|edit|publish|delete|rollback|snapshots|toggle|iconpreview … — Iron Legacy worlds.yml",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text(
                    " set <key> <mechanicId> [display…] — Hub templates.yml (game listing / queue)",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text(
                    " set <key> <display|enabled|maxplayers|teamsize|icon|minimumpercentage|gamerule> … — IL worlds.yml when key exists in /hch template worlds list",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text(
                    " lobby wand|spawn|help — circle lobby zone + spawn (writes IL worlds.yml)",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text(
                    " setsource <key> <folder> · setqueue <key> …",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text(
                    "Template key: Hub row id from /hch template list; Iron Legacy maps: Key column from /hch template worlds list.",
                    NamedTextColor.DARK_GRAY));
            return true;
        }
        String sub = args[1].toLowerCase();
        HubTemplateRegistry reg = plugin.templates();
        return switch (sub) {
            case "list" -> list(sender, reg);
            case "create" -> createWorld(sender, plugin, reg, args);
            case "get" -> get(sender, reg, args);
            case "set" -> routeTemplateSet(sender, reg, args, plugin.platform());
            case "setsource" -> setSource(sender, reg, args);
            case "setqueue" -> setQueue(sender, reg, args);
            case "lobby" -> HubTemplateLobbyCommands.handle(plugin, sender, args);
            case "worlds" -> HubTemplateWorldsCommands.handle(plugin, sender, args);
            case "remove" -> remove(sender, reg, args);
            default -> {
                sender.sendMessage(Component.text("Unknown template subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private static boolean createWorld(CommandSender sender, HollowCrownHubPlugin plugin, HubTemplateRegistry reg, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /hch template create <templateKey> [display name…]",
                    NamedTextColor.YELLOW));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Run template create in-game (creates world + teleports you).", NamedTextColor.RED));
            return true;
        }
        if (!p.isOp()) {
            sender.sendMessage(Component.text("OP is required for template create.", NamedTextColor.RED));
            return true;
        }
        String rawKey = args[2];
        String display = joinArgs(args, 3);
        HubTemplateWorldBootstrap.Result r =
                HubTemplateWorldBootstrap.createIronLegacyEditableWorld(plugin, reg, rawKey, display, p);
        if (!r.ok()) {
            sender.sendMessage(Component.text(r.errorMessage(), NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text(
                "Created Iron Legacy template \"" + r.normalizedKey() + "\" (" + r.world().getName() + "). "
                        + "Creative mode — edit, then publish from HCIronLegacy.",
                NamedTextColor.GREEN));
        return true;
    }

    private static boolean list(CommandSender sender, HubTemplateRegistry reg) {
        List<HubTemplateEntry> all = reg.list();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("No templates registered. Use /hch template set <key> <mechanicId> [display]", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("Templates (" + all.size() + "):", NamedTextColor.GOLD));
        for (HubTemplateEntry e : all) {
            String src = e.sourceWorldName().isEmpty() ? "(no source folder)" : e.sourceWorldName();
            sender.sendMessage(Component.text(
                    "  " + e.templateKey() + " → " + e.mechanicId() + " — " + e.effectiveDisplayName()
                            + " | src=" + src + " | q≤" + e.queueMaxUnitSize()
                            + (e.queueSoloOnly() ? " soloQ" : "")
                            + (e.queueSoloAutoMerge() ? " merge" : "")
                            + (e.joinQueueFirst() ? " joinQ" : ""),
                    NamedTextColor.GRAY));
        }
        return true;
    }

    private static boolean get(CommandSender sender, HubTemplateRegistry reg, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch template get <key>", NamedTextColor.YELLOW));
            return true;
        }
        return reg.get(args[2]).map(e -> {
            sender.sendMessage(Component.text("Template: " + e.templateKey(), NamedTextColor.GOLD));
            sender.sendMessage(Component.text("  mechanicId: " + e.mechanicId(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  displayName: " + (e.displayName().isEmpty() ? "(none)" : e.displayName()), NamedTextColor.GRAY));
            sender.sendMessage(Component.text(
                    "  sourceWorldName: " + (e.sourceWorldName().isEmpty() ? "(none)" : e.sourceWorldName()),
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  queueMaxUnitSize: " + e.queueMaxUnitSize(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  queueSoloOnly: " + e.queueSoloOnly(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  queueSoloAutoMerge: " + e.queueSoloAutoMerge(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  joinQueueFirst: " + e.joinQueueFirst(), NamedTextColor.GRAY));
            return true;
        }).orElseGet(() -> {
            sender.sendMessage(Component.text("Unknown template key.", NamedTextColor.RED));
            return true;
        });
    }

    private static boolean routeTemplateSet(CommandSender sender, HubTemplateRegistry reg, String[] args, GamePlatform platform) {
        var il = HubIronLegacySupport.ilAdmin();
        if (args.length >= 4 && il.isPresent()) {
            String candKey = args[2];
            IronLegacyTemplateAdmin admin = il.get();
            if (HubIlWorldInput.matchesAnyTemplate(admin, candKey) && HubIlWorldSetCommand.isIlWorldField(args[3])) {
                return HubIlWorldSetCommand.applyWorldFields(sender, admin, args);
            }
        }
        return hubMechanicTemplateSet(sender, reg, args, platform);
    }

    private static boolean hubMechanicTemplateSet(CommandSender sender, HubTemplateRegistry reg, String[] args, GamePlatform platform) {
        if (args.length < 4) {
            sender.sendMessage(Component.text(
                    "Usage: /hch template set <key> <mechanicId> [display name…]  — Hub listing row",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(
                    "Or (IL world folder or key): /hch template set <world|key> display|enabled|… …",
                    NamedTextColor.GRAY));
            return true;
        }
        String key = args[2];
        String mechanicId = args[3];
        String display = joinArgs(args, 4);
        try {
            if (platform.getMechanic(com.hollowcrown.api.ids.MechanicId.of(mechanicId)) == null) {
                sender.sendMessage(Component.text(
                        "Warning: no mechanic registered for id \"" + mechanicId + "\" yet (minigame may load later).",
                        NamedTextColor.YELLOW));
            }
        } catch (IllegalArgumentException ignored) {
            sender.sendMessage(Component.text("Invalid mechanic id.", NamedTextColor.RED));
            return true;
        }
        String preserveSource = reg.get(key).map(HubTemplateEntry::sourceWorldName).orElse("");
        try {
            reg.upsert(key, mechanicId, display, preserveSource);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("Template saved.", NamedTextColor.GREEN));
        return true;
    }

    private static boolean setSource(CommandSender sender, HubTemplateRegistry reg, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /hch template setsource <key> <worldFolderName>", NamedTextColor.YELLOW));
            return true;
        }
        try {
            reg.setSourceWorld(args[2], args[3]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("Source world folder saved.", NamedTextColor.GREEN));
        return true;
    }

    private static boolean setQueue(CommandSender sender, HubTemplateRegistry reg, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(Component.text(
                    "Usage: /hch template setqueue <key> maxUnitSize <n> | soloOnly <t|f> | soloAutoMerge <t|f> | joinQueueFirst <t|f>",
                    NamedTextColor.YELLOW));
            return true;
        }
        String key = args[2];
        String field = args[3].toLowerCase();
        String val = args[4].toLowerCase();
        try {
            switch (field) {
                case "maxunitsize", "maxunit" -> {
                    int n = Integer.parseInt(args[4]);
                    reg.updateQueuePolicy(key, n, null, null, null);
                }
                case "soloonly" -> {
                    boolean b = parseBool(val);
                    reg.updateQueuePolicy(key, null, b, null, null);
                }
                case "soloautomerge", "merge" -> {
                    boolean b = parseBool(val);
                    reg.updateQueuePolicy(key, null, null, b, null);
                }
                case "joinqueuefirst", "joinq" -> {
                    boolean b = parseBool(val);
                    reg.updateQueuePolicy(key, null, null, null, b);
                }
                default -> {
                    sender.sendMessage(Component.text(
                            "Unknown field. Use maxUnitSize, soloOnly, soloAutoMerge, or joinQueueFirst.",
                            NamedTextColor.RED));
                    return true;
                }
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
            return true;
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("Queue policy updated.", NamedTextColor.GREEN));
        return true;
    }

    private static boolean parseBool(String val) {
        if ("true".equals(val) || "1".equals(val) || "yes".equals(val)) {
            return true;
        }
        if ("false".equals(val) || "0".equals(val) || "no".equals(val)) {
            return false;
        }
        throw new IllegalArgumentException("Expected true or false.");
    }

    private static boolean remove(CommandSender sender, HubTemplateRegistry reg, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch template remove <key>", NamedTextColor.YELLOW));
            return true;
        }
        if (reg.remove(args[2])) {
            sender.sendMessage(Component.text("Removed.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("No such template.", NamedTextColor.RED));
        }
        return true;
    }

    private static String joinArgs(String[] args, int from) {
        if (from >= args.length) {
            return "";
        }
        StringJoiner j = new StringJoiner(" ");
        for (int i = from; i < args.length; i++) {
            j.add(args[i]);
        }
        return j.toString();
    }
}
