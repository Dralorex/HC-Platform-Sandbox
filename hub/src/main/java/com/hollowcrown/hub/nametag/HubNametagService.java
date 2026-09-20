package com.hollowcrown.hub.nametag;

import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.policy.HubTemplatePolicyService;
import com.hollowcrown.hub.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/**
 * A stripped version of the old HubNametagService used only to color chat messages.
 */
//* Per-viewer nametag/TAB renderer driven by team membership, allies, and designated enemies.
// *
// * <p>Each viewer in a Hub-managed match world gets a private {@link Scoreboard}. On that scoreboard
// * every other player visible to the viewer is placed in a uniquely-named team carrying a viewer-
// * specific prefix ({@code [TeamName] }) and player-name color (green/red/blue/orange / legacy color).
// * The team also controls the name-tag visibility (used for the "sneak hides name tag" feature).</p>
// *
// * <p>The rendering rules:</p>
// * <ul>
// *   <li><b>TEAM mode:</b> prefix carries the team's RGB color, the player name is colored by the
// *       viewer's relationship (teammate=green, allied=blue, designated-enemy=orange, else=red).</li>
// *   <li><b>LEGACY mode:</b> both prefix and player name use the team's legacy {@link ChatColor}
// *       uniformly. No relationship-based recoloring.</li>
// *   <li>Players outside a match world / not in a known template fall back to the server scoreboard.</li>
// * </ul>
// *
// * <p>TAB list inclusion is controlled via {@link #applyTabVisibility(Player)} (Paper {@code setListed}
// * when available). Chat and death messages are prefixed by {@link HubNametagChatListener}.</p>
public final class HubNametagService {

    private final HollowCrownHubPlugin plugin;

    public HubNametagService(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    private Optional<HubTemplatePolicyService.TemplateConfig> configFor(Player player) {
        if (player == null) {
            return Optional.empty();
        } else {
            player.getWorld();
        }
        String tk = limboTemplateKey(player);
        if (tk == null || tk.isBlank()) {
            tk = plugin.limboLayout().worldsYaml()
                    .resolveTemplateKeyForWorld(player.getWorld().getName())
                    .orElse(null);
        }
        if (tk == null || tk.isBlank()) return Optional.empty();
        return Optional.of(plugin.templatePolicies().configFor(tk));
    }

    private static String limboTemplateKey(Player player) {
        RegisteredServiceProvider<PreMatchLimboService> reg =
                Bukkit.getServicesManager().getRegistration(PreMatchLimboService.class);
        if (reg == null) {
            return null;
        }
        return reg.getProvider().limboTemplateKey(player);
    }

    private boolean useRelationshipColors(HubTemplatePolicyService.TemplateConfig cfg, World world) {
        if (world == null) return false;
        if (isInLimboWorld(world)) return true;
        return cfg.colorMode() == HubTemplatePolicyService.ColorMode.TEAM;
    }

    private boolean isInLimboWorld(World world) {
        RegisteredServiceProvider<PreMatchLimboService> reg =
                Bukkit.getServicesManager().getRegistration(PreMatchLimboService.class);
        return reg != null && reg.getProvider().isInLimboWorld(world);
    }

    /**
     * Renders a player's name with the team prefix using the viewer-relationship rule. Used by
     * chat + death formatting.
     */
    public Component renderPlayerName(Player viewer, Player target) {
        Optional<HubTemplatePolicyService.TemplateConfig> cfgOpt = configFor(target);
        if (cfgOpt.isEmpty()) {
            return Component.text(target.getName(), NamedTextColor.WHITE);
        }
        HubTemplatePolicyService.TemplateConfig cfg = cfgOpt.get();
        Player context = viewer == null ? target : viewer;
        boolean relationshipColors = useRelationshipColors(cfg, context.getWorld());
        TeamManager.Team vTeam = viewer == null ? null : TeamManager.getOrCreateWorldInstance(viewer.getWorld()).findPlayerTeam(viewer);
        TeamManager.Team tTeam = target == null ? null : TeamManager.getOrCreateWorldInstance(target.getWorld()).findPlayerTeam(target);
        if(tTeam == null)
            return Component.text(target.getName(), NamedTextColor.WHITE);
        Component result = Component.text("[" + tTeam.getName() + "] ", TextColor.color(tTeam.color.asRGB()));
        return result.append(Component.text(target.getName(), vTeam == null || !relationshipColors ? NamedTextColor.WHITE : (vTeam == tTeam ? NamedTextColor.GREEN : (vTeam.allies.contains(tTeam) ? NamedTextColor.AQUA : (vTeam.rivals.contains(tTeam) ? NamedTextColor.GOLD : NamedTextColor.RED)))));
    }
}
