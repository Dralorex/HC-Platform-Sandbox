package com.hollowcrown.hub.command;

import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.api.platform.GamePlatform;
import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Player {@code /hub} — teleports to configured Hollow Crown hub spawn.
 * Party leaders bring all eligible online members to the same spawn (P3.6).
 */
public final class HubPlayerCommand implements CommandExecutor {

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
        GamePlatform platform = plugin.platform();
        Optional<HubSpawn> spawnOpt = platform.getHubSpawn();
        if (spawnOpt.isEmpty()) {
            player.sendMessage(Component.text("Hub spawn is not set yet. Ask an admin to run /hch sethub.", NamedTextColor.YELLOW));
            return true;
        }
        HubSpawn s = spawnOpt.get();
        World world = Bukkit.getWorld(s.worldUuid());
        if (world == null) {
            player.sendMessage(Component.text("Hub world is not loaded on this server.", NamedTextColor.RED));
            HubLog.warn(plugin, "action=hub_teleport_fail", "reason=world_unloaded", "worldUuid=" + s.worldUuid());
            return true;
        }
        Location loc = new Location(world, s.x(), s.y(), s.z(), s.yaw(), s.pitch());

        Optional<PartyView> partyOpt = plugin.partyService().partyForMember(player.getUniqueId());
        if (partyOpt.isPresent() && partyOpt.get().isLeader(player.getUniqueId()) && partyOpt.get().memberCount() > 1) {
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
                HubWorldApi hubWorld = plugin.hubWorld();
                target.teleportAsync(loc).thenAccept(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        hubWorld.applySurvivalHubLoadoutAfterTemplate(target);
                        HubLog.info(plugin, "action=hub_teleport", "player=" + memberId, "result=ok", "partyPull=1");
                    } else {
                        HubLog.warn(plugin, "action=hub_teleport", "player=" + memberId, "result=fail", "partyPull=1");
                    }
                });
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
                    "pulled=" + pulled, "skipped=" + skipped);
            return true;
        }

        HubWorldApi hubWorld = plugin.hubWorld();
        player.teleportAsync(loc).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                hubWorld.applySurvivalHubLoadoutAfterTemplate(player);
                HubLog.info(plugin, "action=hub_teleport", "player=" + player.getUniqueId(), "result=ok");
            } else {
                HubLog.warn(plugin, "action=hub_teleport", "player=" + player.getUniqueId(), "result=fail");
            }
        });
        return true;
    }
}
