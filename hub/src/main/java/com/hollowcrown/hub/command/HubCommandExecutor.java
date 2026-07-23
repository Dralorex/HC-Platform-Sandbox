package com.hollowcrown.hub.command;

import com.hollowcrown.api.platform.GamePlatform;
import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import com.hollowcrown.hub.platform.HubHubSpawns;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class HubCommandExecutor implements CommandExecutor, TabCompleter {

    private final HollowCrownHubPlugin plugin;

    public HubCommandExecutor(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("worldsgui") || args[0].equalsIgnoreCase("worldgui")) {
            return HubGuiCommands.openWorldsGui(plugin, sender);
        }
        if (args[0].equalsIgnoreCase("gui")) {
            return HubGuiCommands.handle(plugin, sender, args);
        }
        if (args[0].equalsIgnoreCase("template")) {
            return HubTemplateCommands.handle(plugin, sender, args);
        }
        if (args[0].equalsIgnoreCase("lobby")) {
            return HubLobbyCommands.handle(plugin, sender, args);
        }
        if (args[0].equalsIgnoreCase("join")) {
            return HubJoinCommands.handle(plugin, sender, args);
        }
        if (args[0].equalsIgnoreCase("party")) {
            return HubPartyCommands.handle(plugin, sender, args);
        }
        if (args[0].equalsIgnoreCase("queue")) {
            return HubQueueCommands.handle(plugin, sender, args);
        }
        if (args[0].equalsIgnoreCase("debug") && args.length >= 2 && args[1].equalsIgnoreCase("mechanics")) {
            if (!sender.hasPermission("hollowcrownhub.admin")) {
                sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
                return true;
            }
            List<String> ids = new ArrayList<>(plugin.platform().registeredMechanicIdStrings());
            Collections.sort(ids);
            sender.sendMessage(Component.text("Registered mechanics (" + ids.size() + "):", NamedTextColor.GOLD));
            if (ids.isEmpty()) {
                sender.sendMessage(Component.text("  (none)", NamedTextColor.DARK_GRAY));
            } else {
                for (String id : ids) {
                    sender.sendMessage(Component.text("  " + id, NamedTextColor.GRAY));
                }
            }
            HubLog.info(plugin, "action=hch_debug_mechanics", "sender=" + sender.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("debug") && args.length >= 2 && args[1].equalsIgnoreCase("why")) {
            return HubDebugWhy.handle(plugin, sender, args);
        }
        if (args[0].equalsIgnoreCase("version")) {
            sender.sendMessage(Component.text("HollowCrownHub " + plugin.getPluginMeta().getVersion(), NamedTextColor.GREEN));
            sender.sendMessage(Component.text("API schema " + GamePlatform.apiSchema(), NamedTextColor.AQUA));
            HubLog.info(plugin, "action=hch_version", "sender=" + sender.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("sethub")) {
            if (!sender.hasPermission("hollowcrownhub.admin")) {
                sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Run sethub in-game.", NamedTextColor.RED));
                return true;
            }
            int hubNumber = 1;
            if (args.length >= 2) {
                try {
                    hubNumber = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Usage: /hch sethub [hubNumber]", NamedTextColor.YELLOW));
                    return true;
                }
                if (hubNumber < 1) {
                    sender.sendMessage(Component.text("Hub number must be at least 1.", NamedTextColor.RED));
                    return true;
                }
            }
            Location loc = player.getLocation();
            HubSpawn spawn = new HubSpawn(
                    loc.getWorld().getUID(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    loc.getYaw(),
                    loc.getPitch()
            );
            HubHubSpawns.persist(plugin, hubNumber, spawn);
            sender.sendMessage(Component.text(
                    "Hub #" + hubNumber + " spawn set to this location. Players can use /hub"
                            + (hubNumber <= 1 ? "" : " join " + hubNumber) + ".",
                    NamedTextColor.GREEN));
            HubLog.info(plugin, "action=hch_sethub", "sender=" + sender.getName(),
                    "hubNumber=" + hubNumber, "worldUuid=" + spawn.worldUuid());
            return true;
        }
        if (args[0].equalsIgnoreCase("clearhub")) {
            if (!sender.hasPermission("hollowcrownhub.admin")) {
                sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
                return true;
            }
            int hubNumber = 1;
            if (args.length >= 2) {
                try {
                    hubNumber = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Usage: /hch clearhub [hubNumber]", NamedTextColor.YELLOW));
                    return true;
                }
                if (hubNumber < 1) {
                    sender.sendMessage(Component.text("Hub number must be at least 1.", NamedTextColor.RED));
                    return true;
                }
            }
            HubHubSpawns.clear(plugin, hubNumber);
            sender.sendMessage(Component.text("Hub #" + hubNumber + " spawn cleared.", NamedTextColor.YELLOW));
            HubLog.info(plugin, "action=hch_clearhub", "sender=" + sender.getName(), "hubNumber=" + hubNumber);
            return true;
        }
        if (args[0].equalsIgnoreCase("hubworld")) {
            return HubWorldAdminCommands.handle(plugin, sender, args);
        }
        if (args[0].equalsIgnoreCase("ironlegacy")) {
            sender.sendMessage(Component.text(
                    "/hch ironlegacy was removed. Use /hch template worlds … or /hch gui maps.",
                    NamedTextColor.YELLOW));
            return true;
        }
        sender.sendMessage(Component.text("Unknown subcommand. Try /hch help", NamedTextColor.RED));
        return true;
    }

    private static void sendHelp(CommandSender sender) {
        boolean admin = sender.hasPermission("hollowcrownhub.admin");
        sender.sendMessage(Component.text("Hollow Crown Hub — /hch", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /hch help — this help", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /hch party … — parties (create, invite, leave, …)", NamedTextColor.GRAY));
        if (admin) {
            sender.sendMessage(Component.text("  /hch version — API + Hub version", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /hch sethub [n] — set hub spawn #1 or numbered instance", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /hch clearhub [n] — clear hub spawn #1 or numbered instance", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /hch hubworld … — hub protection, build mode, PvP pads", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /hch template … — Hub listings + worlds + limbo", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /hch lobby … — lobby instance worlds (admin)", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /hch join — join-game GUI (compass uses this too)", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /hch queue … — match queue per template", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /hch debug … — mechanics list / player snapshot", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /hch gui … — admin hub GUIs", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /hch worldsgui — Iron Legacy template worlds GUI", NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text(
                "Use /hub for the main spawn; /hub join <n> for another hub instance. Party leaders pull the party on /hub.",
                NamedTextColor.DARK_GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String p = args[0].toLowerCase();
            boolean admin = sender.hasPermission("hollowcrownhub.admin");
            List<String> cmds = admin
                    ? List.of("help", "version", "sethub", "clearhub", "hubworld", "template", "lobby", "join",
                    "party", "queue", "debug", "gui", "worldsgui", "worldgui")
                    : List.of("help", "party");
            for (String c : cmds) {
                if (c.startsWith(p)) {
                    out.add(c);
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("sethub") || args[0].equalsIgnoreCase("clearhub"))) {
            String p = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (int n : HubHubSpawns.configuredNumbers(plugin)) {
                String s = Integer.toString(n);
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
            int next = HubHubSpawns.configuredNumbers(plugin).stream().mapToInt(Integer::intValue).max().orElse(1) + 1;
            String nextStr = Integer.toString(next);
            if (nextStr.startsWith(p) && next > 1) {
                out.add(nextStr);
            }
            if ("1".startsWith(p) && !out.contains("1")) {
                out.add(0, "1");
            }
            return out;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("debug")) {
            String p = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            if ("mechanics".startsWith(p)) {
                out.add("mechanics");
            }
            if ("why".startsWith(p)) {
                out.add("why");
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("why")) {
            String p = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(pl.getName());
                }
            }
            return out;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("template")) {
            return tabTemplate(plugin, args);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("lobby")) {
            return tabLobby(plugin, args);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("join")) {
            return tabJoin(args);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("party")) {
            return tabParty(args);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("queue")) {
            return tabQueue(plugin, args);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("hubworld")) {
            return HubWorldAdminCommands.tabComplete(args);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("gui")) {
            List<String> out = new ArrayList<>();
            HubGuiCommands.tabGui(args, out);
            return out;
        }
        return List.of();
    }

    private static List<String> tabTemplate(HollowCrownHubPlugin plugin, String[] args) {
        if (args.length == 2) {
            String p = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String c : List.of("list", "create", "get", "set", "limbo", "worlds", "setsource", "setqueue", "remove")) {
                if (c.startsWith(p)) {
                    out.add(c);
                }
            }
            return out;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("limbo")) {
            String p = args[2].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String c : List.of("wand", "spawn", "help")) {
                if (c.startsWith(p)) {
                    out.add(c);
                }
            }
            return out;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("limbo") && args[2].equalsIgnoreCase("wand")) {
            String p = args[3].toLowerCase(Locale.ROOT);
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            HubIronLegacySupport.ilAdmin().ifPresent(a -> HubIlWorldInput.addCompletionTokens(a, p, keys));
            return new ArrayList<>(keys);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("worlds")) {
            String p = args[2].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String c : List.of("help", "list", "discover", "reload", "gui", "importhub", "edit", "publish", "delete",
                    "rollback", "snapshots", "toggle", "iconpreview")) {
                if (c.startsWith(p)) {
                    out.add(c);
                }
            }
            return out;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("worlds")) {
            String sub = args[2].toLowerCase(Locale.ROOT);
            boolean needsKey = switch (sub) {
                case "edit", "publish", "delete", "rollback", "snapshots", "toggle", "iconpreview" -> true;
                default -> false;
            };
            if (needsKey) {
                String p = args[3].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                HubIronLegacySupport.ilAdmin().ifPresent(a -> {
                    LinkedHashSet<String> tok = new LinkedHashSet<>();
                    HubIlWorldInput.addCompletionTokens(a, p, tok);
                    out.addAll(tok);
                });
                return out;
            }
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("worlds") && args[2].equalsIgnoreCase("rollback")) {
            String p = args[4].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            var il = HubIronLegacySupport.ilAdmin();
            if (il.isEmpty()) {
                return List.of();
            }
            Optional<String> rk = HubIlWorldInput.resolveCanonicalKey(il.get(), args[3]);
            if (rk.isEmpty()) {
                return List.of();
            }
            if ("latest".startsWith(p)) {
                out.add("latest");
            }
            for (String id : il.get().listSnapshotIds(rk.get())) {
                if (id.toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(id);
                }
            }
            return out;
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("get") || args[1].equalsIgnoreCase("remove"))) {
            String p = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (var e : plugin.templates().list()) {
                if (e.templateKey().toLowerCase().startsWith(p)) {
                    out.add(e.templateKey());
                }
            }
            return out;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("setsource")) {
            String p = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (var e : plugin.templates().list()) {
                if (e.templateKey().toLowerCase().startsWith(p)) {
                    out.add(e.templateKey());
                }
            }
            return out;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("setqueue")) {
            String p = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (var e : plugin.templates().list()) {
                if (e.templateKey().toLowerCase().startsWith(p)) {
                    out.add(e.templateKey());
                }
            }
            return out;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("setqueue")) {
            String p = args[3].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String f : List.of("maxUnitSize", "soloOnly", "soloAutoMerge", "joinQueueFirst")) {
                if (f.toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(f);
                }
            }
            return out;
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("setqueue")
                && (args[3].equalsIgnoreCase("soloOnly")
                || args[3].equalsIgnoreCase("soloAutoMerge")
                || args[3].equalsIgnoreCase("joinQueueFirst"))) {
            String p = args[4].toLowerCase();
            List<String> out = new ArrayList<>();
            if ("true".startsWith(p)) {
                out.add("true");
            }
            if ("false".startsWith(p)) {
                out.add("false");
            }
            return out;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
            String p = args[2].toLowerCase(Locale.ROOT);
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            for (var e : plugin.templates().list()) {
                if (e.templateKey().toLowerCase(Locale.ROOT).startsWith(p)) {
                    keys.add(e.templateKey());
                }
            }
            HubIronLegacySupport.ilAdmin().ifPresent(a -> HubIlWorldInput.addCompletionTokens(a, p, keys));
            return new ArrayList<>(keys);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
            String keyArg = args[2];
            String p = args[3].toLowerCase(Locale.ROOT);
            var il = HubIronLegacySupport.ilAdmin();
            if (il.isPresent() && HubIlWorldInput.matchesAnyTemplate(il.get(), keyArg)) {
                List<String> out = new ArrayList<>();
                for (String f : List.of("display", "enabled", "maxplayers", "teamsize", "icon", "minimumpercentage", "gamerule")) {
                    if (f.toLowerCase(Locale.ROOT).startsWith(p)) {
                        out.add(f);
                    }
                }
                return out;
            }
            List<String> out = new ArrayList<>();
            for (String id : plugin.platform().registeredMechanicIdStrings()) {
                if (id.toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(id);
                }
            }
            return out;
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("set")) {
            var il = HubIronLegacySupport.ilAdmin();
            if (il.isPresent() && HubIlWorldInput.matchesAnyTemplate(il.get(), args[2])
                    && "gamerule".equalsIgnoreCase(args[3])) {
                String p = args[4].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (String n : List.of("doDaylightCycle", "doWeatherCycle")) {
                    if (n.toLowerCase(Locale.ROOT).startsWith(p)) {
                        out.add(n);
                    }
                }
                return out;
            }
        }
        return List.of();
    }

    private static List<String> tabLobby(HollowCrownHubPlugin plugin, String[] args) {
        if (args.length == 2) {
            String p = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String c : List.of("list", "gui", "create", "destroy", "forget", "tp")) {
                if (c.startsWith(p)) {
                    out.add(c);
                }
            }
            return out;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("create")) {
            String p = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (var e : plugin.templates().list()) {
                if (e.templateKey().toLowerCase().startsWith(p)) {
                    out.add(e.templateKey());
                }
            }
            return out;
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("destroy")
                || args[1].equalsIgnoreCase("forget")
                || args[1].equalsIgnoreCase("tp"))) {
            String p = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (var r : plugin.lobbyService().instances().all()) {
                if (r.id().toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(r.id());
                }
            }
            return out;
        }
        return List.of();
    }

    private static List<String> tabQueue(HollowCrownHubPlugin plugin, String[] args) {
        if (args.length == 2) {
            String p = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String c : List.of("join", "leave", "status")) {
                if (c.startsWith(p)) {
                    out.add(c);
                }
            }
            return out;
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("join") || args[1].equalsIgnoreCase("status"))) {
            String p = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (var e : plugin.templates().list()) {
                if (e.templateKey().toLowerCase().startsWith(p)) {
                    out.add(e.templateKey());
                }
            }
            return out;
        }
        return List.of();
    }

    private static List<String> tabParty(String[] args) {
        if (args.length == 2) {
            String p = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String c : List.of("create", "invite", "leave", "disband", "kick", "accept", "decline", "list")) {
                if (c.startsWith(p)) {
                    out.add(c);
                }
            }
            return out;
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("invite") || args[1].equalsIgnoreCase("kick"))) {
            return HubPartyCommands.tabPlayers(args[2]);
        }
        return List.of();
    }

    private static List<String> tabJoin(String[] args) {
        if (args.length == 2) {
            String p = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            if ("gui".startsWith(p)) {
                out.add("gui");
            }
            return out;
        }
        return List.of();
    }
}
