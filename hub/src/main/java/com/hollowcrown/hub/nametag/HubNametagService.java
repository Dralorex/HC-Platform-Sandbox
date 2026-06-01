package com.hollowcrown.hub.nametag;

import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.api.team.TeamView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.alliance.HubAllianceService;
import com.hollowcrown.hub.policy.HubTemplatePolicyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-viewer nametag/TAB renderer driven by team membership, allies, and designated enemies.
 *
 * <p>Each viewer in a Hub-managed match world gets a private {@link Scoreboard}. On that scoreboard
 * every other player visible to the viewer is placed in a uniquely-named team carrying a viewer-
 * specific prefix ({@code [TeamName] }) and player-name color (green/red/blue/orange / legacy color).
 * The team also controls the name-tag visibility (used for the "sneak hides name tag" feature).</p>
 *
 * <p>The rendering rules:</p>
 * <ul>
 *   <li><b>TEAM mode:</b> prefix carries the team's RGB color, the player name is colored by the
 *       viewer's relationship (teammate=green, allied=blue, designated-enemy=orange, else=red).</li>
 *   <li><b>LEGACY mode:</b> both prefix and player name use the team's legacy {@link ChatColor}
 *       uniformly. No relationship-based recoloring.</li>
 *   <li>Players outside a match world / not in a known template fall back to the server scoreboard.</li>
 * </ul>
 *
 * <p>TAB list inclusion is controlled via {@link #applyTabVisibility(Player)} (Paper {@code setListed}
 * when available). Chat and death messages are prefixed by {@link HubNametagChatListener}.</p>
 */
public final class HubNametagService {

    private static final NamedTextColor COLOR_TEAMMATE = NamedTextColor.GREEN;
    private static final NamedTextColor COLOR_ALLY = NamedTextColor.BLUE;
    private static final NamedTextColor COLOR_ENEMY = NamedTextColor.RED;
    private static final NamedTextColor COLOR_DESIGNATED = NamedTextColor.GOLD;

    private final HollowCrownHubPlugin plugin;
    private final HubAllianceService alliances;

    /** viewer UUID → scoreboard owned by that viewer. */
    private final Map<UUID, Scoreboard> viewerScoreboards = new ConcurrentHashMap<>();

    public HubNametagService(HollowCrownHubPlugin plugin, HubAllianceService alliances) {
        this.plugin = plugin;
        this.alliances = alliances;
    }

    public void onJoinOrWorldChange(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        applyTabVisibility(viewer);
        refreshViewer(viewer);
        refreshAllViewersOfTarget(viewer);
    }

    public void onQuit(Player viewer) {
        if (viewer == null) return;
        viewerScoreboards.remove(viewer.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = viewerScoreboards.get(other.getUniqueId());
            if (sb == null) continue;
            String key = teamKey(viewer.getUniqueId());
            Team t = sb.getTeam(key);
            if (t != null) {
                try {
                    t.removeEntry(viewer.getName());
                    t.unregister();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void onSneakToggle(Player target) {
        if (target == null) return;
        Optional<HubTemplatePolicyService.TemplateConfig> cfg = configFor(target);
        if (cfg.isEmpty() || !cfg.get().shiftHidesNameTag()) return;
        refreshAllViewersOfTarget(target);
    }

    public void refreshWorld(World world) {
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            refreshViewer(p);
        }
    }

    public void refreshAllViewersOfTarget(Player target) {
        if (target == null || target.getWorld() == null) return;
        for (Player viewer : target.getWorld().getPlayers()) {
            refreshOnePair(viewer, target);
        }
    }

    public void refreshViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline() || viewer.getWorld() == null) return;

        Optional<HubTemplatePolicyService.TemplateConfig> cfg = configFor(viewer);
        if (cfg.isEmpty() || !plugin.pluginPolicies().isMatchWorld(viewer.getWorld())) {
            Scoreboard prev = viewerScoreboards.remove(viewer.getUniqueId());
            if (prev != null) {
                try {
                    viewer.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                } catch (Throwable ignored) {
                }
            }
            return;
        }

        Scoreboard sb = viewerScoreboards.computeIfAbsent(viewer.getUniqueId(),
                k -> Bukkit.getScoreboardManager().getNewScoreboard());
        try {
            if (!sb.equals(viewer.getScoreboard())) {
                viewer.setScoreboard(sb);
            }
        } catch (Throwable ignored) {
        }
        for (Player target : viewer.getWorld().getPlayers()) {
            refreshOnePair(viewer, target);
        }
    }

    private void refreshOnePair(Player viewer, Player target) {
        if (viewer == null || target == null) return;
        Scoreboard sb = viewerScoreboards.get(viewer.getUniqueId());
        if (sb == null) return; // viewer not in a match world
        Optional<HubTemplatePolicyService.TemplateConfig> cfgOpt = configFor(viewer);
        if (cfgOpt.isEmpty()) return;
        HubTemplatePolicyService.TemplateConfig cfg = cfgOpt.get();

        TeamView vTeam = teamOf(viewer);
        TeamView tTeam = teamOf(target);

        String key = teamKey(target.getUniqueId());
        Team team = sb.getTeam(key);
        if (team == null) {
            try {
                team = sb.registerNewTeam(key);
            } catch (IllegalArgumentException ex) {
                team = sb.getTeam(key);
                if (team == null) return;
            }
        }

        Component prefix = prefixFor(tTeam, cfg);
        if (prefix == null) {
            team.prefix(Component.empty());
        } else {
            team.prefix(prefix);
        }

        NamedTextColor nameColor = nameColorFor(viewer, target, vTeam, tTeam, cfg);
        try {
            team.color(nameColor);
        } catch (Throwable ignored) {
        }

        boolean hidden = shouldHide(viewer, target, vTeam, tTeam, cfg);
        try {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY,
                    hidden ? Team.OptionStatus.NEVER : Team.OptionStatus.ALWAYS);
        } catch (Throwable ignored) {
        }
        try {
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        } catch (Throwable ignored) {
        }

        if (!team.hasEntry(target.getName())) {
            try {
                team.addEntry(target.getName());
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean shouldHide(Player viewer, Player target, TeamView vTeam, TeamView tTeam,
                                HubTemplatePolicyService.TemplateConfig cfg) {
        if (!cfg.shiftHidesNameTag()) return false;
        if (target == null || !target.isSneaking()) return false;
        if (vTeam != null && tTeam != null && vTeam.name().equalsIgnoreCase(tTeam.name())) {
            return false;
        }
        return true;
    }

    private Component prefixFor(TeamView tTeam, HubTemplatePolicyService.TemplateConfig cfg) {
        if (tTeam == null) return null;
        TextColor color = resolveTeamPrefixColor(tTeam, cfg);
        return Component.text("[" + tTeam.name() + "] ", color);
    }

    private TextColor resolveTeamPrefixColor(TeamView tv, HubTemplatePolicyService.TemplateConfig cfg) {
        if (cfg.colorMode() == HubTemplatePolicyService.ColorMode.LEGACY) {
            return chatColorToText(tv.legacyColor(), NamedTextColor.GRAY);
        }
        Integer rgb = tv.displayRgb();
        if (rgb != null) {
            return TextColor.color(rgb & 0xFFFFFF);
        }
        return chatColorToText(tv.legacyColor(), NamedTextColor.GRAY);
    }

    private NamedTextColor nameColorFor(Player viewer, Player target, TeamView vTeam, TeamView tTeam,
                                         HubTemplatePolicyService.TemplateConfig cfg) {
        if (cfg.colorMode() == HubTemplatePolicyService.ColorMode.LEGACY) {
            if (tTeam == null) return NamedTextColor.WHITE;
            return chatColorToNamed(tTeam.legacyColor(), NamedTextColor.WHITE);
        }
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            return NamedTextColor.WHITE;
        }
        if (vTeam == null || tTeam == null) {
            return NamedTextColor.WHITE;
        }
        if (vTeam.name().equalsIgnoreCase(tTeam.name())) {
            return COLOR_TEAMMATE;
        }
        if (alliances.isDesignatedEnemyOf(vTeam.name(), tTeam.name())) {
            return COLOR_DESIGNATED;
        }
        if (alliances.areAllied(vTeam.name(), tTeam.name())) {
            return COLOR_ALLY;
        }
        return COLOR_ENEMY;
    }

    private static TextColor chatColorToText(ChatColor cc, NamedTextColor fallback) {
        if (cc == null) return fallback;
        try {
            return NamedTextColor.NAMES.value(cc.name().toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static NamedTextColor chatColorToNamed(ChatColor cc, NamedTextColor fallback) {
        if (cc == null) return fallback;
        try {
            NamedTextColor c = NamedTextColor.NAMES.value(cc.name().toLowerCase(Locale.ROOT));
            return c == null ? fallback : c;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private TeamView teamOf(Player player) {
        if (player == null) return null;
        RegisteredServiceProvider<TeamService> reg = Bukkit.getServicesManager().getRegistration(TeamService.class);
        if (reg == null) return null;
        return reg.getProvider().teamOf(player.getUniqueId()).orElse(null);
    }

    private Optional<HubTemplatePolicyService.TemplateConfig> configFor(Player player) {
        if (player == null || player.getWorld() == null) return Optional.empty();
        String tk = plugin.limboLayout().worldsYaml().resolveTemplateKeyForWorld(player.getWorld().getName()).orElse(null);
        if (tk == null) return Optional.empty();
        return Optional.of(plugin.templatePolicies().configFor(tk));
    }

    private static String teamKey(UUID id) {
        return "hc_" + id.toString().replace("-", "").substring(0, 12);
    }

    /**
     * Push the per-template TAB visibility flag onto the given player. Called when the player joins
     * or changes worlds, and when the template config changes.
     */
    public void applyTabVisibility(Player player) {
        if (player == null || !player.isOnline()) return;
        boolean inMatch = plugin.pluginPolicies().isMatchWorld(player.getWorld());
        boolean visible = true;
        Optional<HubTemplatePolicyService.TemplateConfig> cfg = configFor(player);
        if (cfg.isPresent() && inMatch) {
            visible = cfg.get().tabVisible();
        }
        setPlayerListed(player, visible);
        if (inMatch) {
            try {
                player.playerListName(null);
                player.displayName(null);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Paper {@code Player#setListed} is not on all compile-time API revisions; invoke reflectively. */
    private static void setPlayerListed(Player player, boolean listed) {
        try {
            Player.class.getMethod("setListed", boolean.class).invoke(player, listed);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Renders a player's name with the team prefix using the viewer-relationship rule. Used by
     * chat + death formatting.
     */
    public Component renderPlayerName(Player viewer, Player target) {
        Optional<HubTemplatePolicyService.TemplateConfig> cfg = configFor(target);
        if (cfg.isEmpty()) {
            return Component.text(target.getName(), NamedTextColor.WHITE);
        }
        TeamView vTeam = teamOf(viewer);
        TeamView tTeam = teamOf(target);
        Component name = Component.text(target.getName(),
                nameColorFor(viewer == null ? target : viewer, target, vTeam, tTeam, cfg.get()));
        Component prefix = prefixFor(tTeam, cfg.get());
        if (prefix == null) return name;
        return Component.empty().append(prefix).append(name);
    }

    /** Convenience for diagnostics. */
    public Map<UUID, String> snapshotScoreboardOwners() {
        Map<UUID, String> out = new HashMap<>();
        for (UUID id : viewerScoreboards.keySet()) {
            Player p = Bukkit.getPlayer(id);
            out.put(id, p == null ? "<offline>" : p.getName());
        }
        return out;
    }
}
