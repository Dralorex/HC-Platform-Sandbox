package com.hollowcrown.hub.command;

import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.api.platform.GamePlatform;
import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.api.queue.MatchQueueService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.lobby.HubLobbyInstanceRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class HubDebugWhy {

    private HubDebugWhy() {
    }

    public static boolean handle(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.admin")) {
            sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch debug why <player>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not online: " + args[2], NamedTextColor.RED));
            return true;
        }
        UUID id = target.getUniqueId();
        World world = target.getWorld();

        List<String> lines = new ArrayList<>();
        lines.add("player=" + target.getName());
        lines.add("uuid=" + id);
        lines.add("world=" + (world == null ? "null" : world.getName()));
        lines.add("worldUuid=" + (world == null ? "null" : world.getUID()));

        GamePlatform platform = plugin.platform();
        Optional<HubSpawn> hub = platform.getHubSpawn();
        lines.add("hubSpawnConfigured=" + hub.isPresent());
        hub.ifPresent(h -> lines.add("hubSpawnWorldUuid=" + h.worldUuid()));

        plugin.partyService().partyForMember(id).ifPresentOrElse(
                p -> lines.addAll(describeParty(p)),
                () -> lines.add("party=none"));

        MatchQueueService mq = plugin.matchQueueService();
        Optional<String> queued = mq.queuedTemplateFor(id);
        lines.add("queuedTemplate=" + queued.orElse("none"));
        queued.ifPresent(tk -> {
            lines.add("queueDescribe(" + tk + "):");
            for (String row : mq.describeQueue(tk)) {
                lines.add("  " + row);
            }
            lines.add("queuedUnitCount=" + mq.queuedUnitCount(tk));
        });

        String lobbyHit = describeLobbyInstance(plugin, world);
        lines.add("lobbyInstance=" + lobbyHit);

        lines.add("registeredMechanics=" + String.join(",", platform.registeredMechanicIdStrings()));

        sender.sendMessage(Component.text("— Hollow Crown /hch debug why —", NamedTextColor.GOLD));
        for (String line : lines) {
            sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }

        String logLine = String.join(" ", lines).replace("\n", " ");
        HubLog.info(plugin, "action=hch_debug_why", "target=" + target.getName(), "sender=" + sender.getName(), "detail=" + logLine.substring(0, Math.min(900, logLine.length())));
        return true;
    }

    private static List<String> describeParty(PartyView p) {
        List<String> out = new ArrayList<>();
        out.add("partyId=" + p.partyId());
        out.add("partyLeader=" + p.leaderId());
        out.add("partyMembers=" + p.memberCount());
        out.add("partyDisplay=" + p.displayName());
        return out;
    }

    private static String describeLobbyInstance(HollowCrownHubPlugin plugin, World world) {
        if (world == null) {
            return "n/a";
        }
        UUID u = world.getUID();
        for (HubLobbyInstanceRecord r : plugin.lobbyService().instances().all()) {
            if (r.worldUuid().equals(u)) {
                return "id=" + r.id() + " template=" + r.templateKey() + " state=" + r.state().name().toLowerCase(Locale.ROOT);
            }
        }
        return "none";
    }
}
