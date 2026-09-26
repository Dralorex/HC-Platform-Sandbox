package com.hollowcrown.hub.policy;

import com.hollowcrown.api.integration.PreMatchLobbyService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.Optional;

/**
 * Best-effort enforcement for plugin policies.
 *
 * <p>We cannot prevent other plugins from receiving events; instead we block the user-facing
 * surface area we can reliably attribute: plugin commands.</p>
 */
public final class HubPluginPolicyEnforcer implements Listener {

    private final HollowCrownHubPlugin hub;
    private final HubPluginPolicyService policies;
    private final HubTemplatePolicyService templatePolicies;

    public HubPluginPolicyEnforcer(HollowCrownHubPlugin hub,
                                   HubPluginPolicyService policies,
                                   HubTemplatePolicyService templatePolicies) {
        this.hub = hub;
        this.policies = policies;
        this.templatePolicies = templatePolicies;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (p == null) {
            return;
        }
        String msg = e.getMessage();
        if (msg == null || msg.isBlank() || !msg.startsWith("/")) {
            return;
        }
        String raw = msg.substring(1).trim();
        if (raw.isBlank()) {
            return;
        }
        String label = raw.split("\\s+")[0].trim();
        if (label.isBlank()) {
            return;
        }

        HubPluginPolicyService.Scope scope = currentScope(p);

        Optional<String> ownerOpt = policies.resolveOwningPluginNameForCommandLabel(label);
        if (ownerOpt.isEmpty()) {
            return; // unknown command / vanilla / not plugin-owned
        }
        String owner = ownerOpt.get();
        if (owner == null || owner.isBlank()) {
            return;
        }
        // always allow hub/foundation
        if (policies.alwaysAllowedPluginNames().contains(owner)) {
            return;
        }
        boolean allowed = isOwnerAllowedForPlayer(owner, p, scope);
        if (allowed) {
            return;
        }
        e.setCancelled(true);
        p.sendMessage(Component.text(
                "That plugin is disabled here (" + scope.name().toLowerCase(Locale.ROOT) + ").",
                NamedTextColor.RED));
    }

    /**
     * Resolution order for MATCH scope: per-template policy (when the player is in a known template's
     * match world) overrides the global Match policy entirely. Falls back to global Match policy when
     * no template is associated. Hub and Lobby scopes use the global policy as before.
     */
    private boolean isOwnerAllowedForPlayer(String owner, Player p, HubPluginPolicyService.Scope scope) {
        if (scope != HubPluginPolicyService.Scope.MATCH || templatePolicies == null) {
            return policies.isPluginAllowedInScope(owner, scope);
        }
        String templateKey = resolveTemplateKeyForMatch(p);
        if (templateKey == null || templateKey.isBlank()) {
            return policies.isPluginAllowedInScope(owner, scope);
        }
        HubTemplatePolicyService.TemplateConfig cfg = templatePolicies.configFor(templateKey);
        boolean listed = cfg.pluginPolicyList().contains(owner);
        return cfg.pluginPolicyMode() == HubPluginPolicyService.Mode.WHITELIST ? listed : !listed;
    }

    private String resolveTemplateKeyForMatch(Player p) {
        if (p == null || p.getWorld() == null) return null;
        try {
            String world = p.getWorld().getName();
            return hub.lobbyLayout().worldsYaml().resolveTemplateKeyForWorld(world).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent e) {
        if (e.getWorld() == null) return;
        policies.unmarkWorldAsMatch(e.getWorld().getName());
    }

    private HubPluginPolicyService.Scope currentScope(Player p) {
        // Lobby takes precedence
        RegisteredServiceProvider<PreMatchLobbyService> lobbyReg =
                Bukkit.getServicesManager().getRegistration(PreMatchLobbyService.class);
        if (lobbyReg != null) {
            try {
                if (lobbyReg.getProvider().isInLobby(p)) {
                    return HubPluginPolicyService.Scope.LOBBY;
                }
            } catch (Throwable ignored) {
            }
        }
        // Hub protection
        try {
            if (hub.hubWorld().isInHubProtection(p)) {
                return HubPluginPolicyService.Scope.HUB;
            }
        } catch (Throwable ignored) {
        }
        // Match worlds (tracked)
        if (policies.isMatchWorld(p.getWorld())) {
            return HubPluginPolicyService.Scope.MATCH;
        }
        // Default to MATCH for non-hub/non-lobby on hub-like servers; otherwise HUB.
        return HubPluginPolicyService.Scope.MATCH;
    }
}

