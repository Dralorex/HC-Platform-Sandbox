package com.hollowcrown.hub.command;

import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.platform.HubHubRouting;
import com.hollowcrown.hub.platform.HubHubSpawns;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Player {@code /hub} — default hub spawn, or {@code /hub join <n>} for a numbered hub instance.
 * Party leaders on plain {@code /hub} pull eligible online members to hub #1.
 */
public final class HubPlayerCommand implements CommandExecutor, TabCompleter {

    private final HollowCrownHubPlugin plugin;

    public HubPlayerCommand(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("hollowcrownhub.use")) {
            player.sendMessage(Component.text("You cannot use /hub here.", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("join")) {
            return handleJoin(player, args);
        }
        if (args.length >= 1) {
            player.sendMessage(Component.text("Usage: /hub  |  /hub join <hubNumber>", NamedTextColor.YELLOW));
            return true;
        }

        return teleportPartyOrSolo(player, 1, true);
    }

    private boolean handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            List<Integer> hubs = HubHubSpawns.configuredNumbers(plugin);
            if (hubs.isEmpty()) {
                player.sendMessage(Component.text(
                        "No hub spawns are configured. Ask an admin to run /hch sethub.",
                        NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text(
                        "Available hubs: " + hubs + ". Usage: /hub join <number>",
                        NamedTextColor.AQUA));
            }
            return true;
        }
        int hubNumber;
        try {
            hubNumber = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(Component.text("Hub number must be an integer.", NamedTextColor.RED));
            return true;
        }
        if (hubNumber < 1) {
            player.sendMessage(Component.text("Hub number must be at least 1.", NamedTextColor.RED));
            return true;
        }
        if (plugin.sendPlayerToHubSpawn(player, hubNumber)) {
            player.sendMessage(Component.text("Teleported to hub #" + hubNumber + ".", NamedTextColor.GREEN));
            HubLog.info(plugin, "action=hub_join", "player=" + player.getUniqueId(), "hubNumber=" + hubNumber);
        } else {
            HubLog.warn(plugin, "action=hub_join", "player=" + player.getUniqueId(), "hubNumber=" + hubNumber, "result=fail");
        }
        return true;
    }

    private boolean teleportPartyOrSolo(Player player, int hubNumber, boolean useRouting) {
        Optional<PartyView> partyOpt = plugin.partyService().partyForMember(player.getUniqueId());
        if (useRouting && partyOpt.isPresent() && partyOpt.get().isLeader(player.getUniqueId()) && partyOpt.get().memberCount() > 1) {
            PartyView party = partyOpt.get();
            List<Player> group = new ArrayList<>();
            for (UUID memberId : party.membersJoinOrder()) {
                Player target = Bukkit.getPlayer(memberId);
                if (target != null && target.isOnline() && target.hasPermission("hollowcrownhub.use")) {
                    group.add(target);
                }
            }
            if (group.size() > 1) {
                boolean ok = HubHubRouting.sendGroupToHub(plugin, group);
                player.sendMessage(Component.text(
                        "Bringing your party to the hub (" + group.size() + " online).",
                        NamedTextColor.GREEN));
                HubLog.info(plugin, "action=hub_party_pull", "leader=" + player.getName(),
                        "partyId=" + party.partyId(), "pulled=" + group.size(), "routing=capacity");
                return ok;
            }
        }
        if (partyOpt.isPresent() && partyOpt.get().isLeader(player.getUniqueId()) && partyOpt.get().memberCount() > 1 && !useRouting) {
            PartyView party = partyOpt.get();
            int pulled = 0;
            int skipped = 0;
            for (UUID memberId : party.membersJoinOrder()) {
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) {
                    skipped++;
                    continue;
                }
                if (!target.hasPermission("hollowcrownhub.use")) {
                    skipped++;
                    continue;
                }
                pulled++;
                boolean isLeader = memberId.equals(player.getUniqueId());
                if (plugin.sendPlayerToHubSpawn(target, hubNumber)) {
                    HubLog.info(plugin, "action=hub_teleport", "player=" + memberId, "result=ok", "partyPull=1");
                } else {
                    HubLog.warn(plugin, "action=hub_teleport", "player=" + memberId, "result=fail", "partyPull=1");
                }
                if (!isLeader) {
                    target.sendMessage(Component.text(
                            player.getName() + " brought the party to the hub.",
                            NamedTextColor.AQUA));
                }
            }
            player.sendMessage(Component.text(
                    "Bringing your party to the hub (" + pulled + " online with /hub permission"
                            + (skipped > 0 ? "; " + skipped + " skipped (offline or no permission)" : "")
                            + ").",
                    NamedTextColor.GREEN));
            HubLog.info(plugin, "action=hub_party_pull", "leader=" + player.getName(), "partyId=" + party.partyId(),
                    "pulled=" + pulled, "skipped=" + skipped, "hubNumber=" + hubNumber);
            return true;
        }

        if (plugin.sendPlayerToHubSpawn(player, hubNumber)) {
            HubLog.info(plugin, "action=hub_teleport", "player=" + player.getUniqueId(), "result=ok");
        } else {
            HubLog.warn(plugin, "action=hub_teleport", "player=" + player.getUniqueId(), "result=fail");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("join".startsWith(p)) {
                out.add("join");
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (int n : HubHubSpawns.configuredNumbers(plugin)) {
                String s = Integer.toString(n);
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }
}
