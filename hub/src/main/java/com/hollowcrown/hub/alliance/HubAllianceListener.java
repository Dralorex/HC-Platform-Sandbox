package com.hollowcrown.hub.alliance;

import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.api.team.TeamView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.policy.HubTemplatePolicyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-cutting hooks for the alliance system:
 *
 * <ul>
 *   <li>Cancels PvP between allied teams (and players on the same team if IL hasn't already).</li>
 *   <li>On death/quit/world-unload in a match world, checks whether only one alliance group of
 *       teams still has alive players. If so, dissolves all alliances and broadcasts.</li>
 * </ul>
 */
public final class HubAllianceListener implements Listener {

    private final HollowCrownHubPlugin plugin;
    private final HubAllianceService alliances;

    public HubAllianceListener(HollowCrownHubPlugin plugin, HubAllianceService alliances) {
        this.plugin = plugin;
        this.alliances = alliances;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (victim.getUniqueId().equals(attacker.getUniqueId())) return;
        HubTemplatePolicyService.TemplateConfig cfg = configForTemplateWorld(victim);
        if (cfg == null) return;
        TeamService teams = teamsOrNull();
        if (teams == null) return;
        TeamView vt = teams.teamOf(victim.getUniqueId()).orElse(null);
        TeamView at = teams.teamOf(attacker.getUniqueId()).orElse(null);
        if (vt == null || at == null) return;
        if (vt.name().equalsIgnoreCase(at.name()) && !cfg.teamFriendlyFireEnabled()) {
            e.setCancelled(true);
            return;
        }
        if (cfg.alliesEnabled() && !cfg.friendlyFireEnabled() && alliances.areAllied(vt.name(), at.name())) {
            e.setCancelled(true);
            attacker.sendActionBar(Component.text("Allied team — friendly fire blocked.",
                    NamedTextColor.AQUA));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        scheduleAllianceCheck(e.getEntity().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        scheduleAllianceCheck(e.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent e) {
        scheduleAllianceCheck(e.getWorld());
        new BukkitRunnable() {
            @Override
            public void run() {
                boolean anyMatchWorld = false;
                for (World w : Bukkit.getWorlds()) {
                    if (plugin.pluginPolicies().isMatchWorld(w)) {
                        anyMatchWorld = true;
                        break;
                    }
                }
                if (!anyMatchWorld) {
                    alliances.reset();
                }
            }
        }.runTaskLater(plugin, 20L);
    }

    private void scheduleAllianceCheck(World world) {
        if (world == null) return;
        if (!plugin.pluginPolicies().isMatchWorld(world)) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                checkLastGroupAlive(world);
            }
        }.runTaskLater(plugin, 5L);
    }

    private void checkLastGroupAlive(World world) {
        if (alliances.areFurtherAlliancesDisabled()) return;
        if (alliances.distinctAllianceGroups().isEmpty()) return;
        TeamService teams = teamsOrNull();
        if (teams == null) return;

        Set<String> activeTeamsLower = new HashSet<>();
        for (Player p : world.getPlayers()) {
            if (p.isDead()) continue;
            if (p.getGameMode().name().equals("SPECTATOR")) continue;
            TeamView tv = teams.teamOf(p.getUniqueId()).orElse(null);
            if (tv != null) {
                activeTeamsLower.add(tv.name().toLowerCase(Locale.ROOT));
            }
        }

        if (activeTeamsLower.size() <= 1) {
            return; // game already effectively over; let IL declare a winner
        }

        Set<String> visited = new HashSet<>();
        int distinctGroups = 0;
        Set<String> survivingAllianceGroup = null;
        for (String team : activeTeamsLower) {
            if (visited.contains(team)) continue;
            Set<String> group = new LinkedHashSet<>(alliances.connectedComponent(team));
            if (group.isEmpty()) group = Set.of(team);
            visited.addAll(group);
            distinctGroups++;
            if (distinctGroups == 1) {
                survivingAllianceGroup = group;
            } else {
                return; // more than one distinct alliance group still alive
            }
        }

        if (survivingAllianceGroup == null || survivingAllianceGroup.size() < 2) {
            return; // single team left, but no alliances to break
        }

        alliances.breakAllAlliances();
        for (Player p : world.getPlayers()) {
            p.showTitle(Title.title(
                    Component.text("Only one team will make it out alive.", NamedTextColor.RED),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            p.sendMessage(Component.text("Only one team will make it out alive.", NamedTextColor.RED));
        }
    }

    private HubTemplatePolicyService.TemplateConfig configForTemplateWorld(Player p) {
        if (p == null || p.getWorld() == null) return null;
        World w = p.getWorld();
        if (!plugin.pluginPolicies().isMatchWorld(w) && !isInLimboWorld(w)) return null;
        String tk = plugin.limboLayout().worldsYaml().resolveTemplateKeyForWorld(w.getName()).orElse(null);
        if (tk == null) return null;
        return plugin.templatePolicies().configFor(tk);
    }

    private boolean isInLimboWorld(World world) {
        RegisteredServiceProvider<PreMatchLimboService> reg =
                Bukkit.getServicesManager().getRegistration(PreMatchLimboService.class);
        return reg != null && reg.getProvider().isInLimboWorld(world);
    }

    private TeamService teamsOrNull() {
        RegisteredServiceProvider<TeamService> reg = Bukkit.getServicesManager().getRegistration(TeamService.class);
        return reg == null ? null : reg.getProvider();
    }

    @SuppressWarnings("unused")
    private void ignore(UUID id) {
    }
}
