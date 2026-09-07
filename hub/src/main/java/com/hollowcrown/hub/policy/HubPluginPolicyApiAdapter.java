package com.hollowcrown.hub.policy;

import com.hollowcrown.api.integration.HubPluginPolicyApi;
import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Bridges the internal {@link HubPluginPolicyService} state to the cross-plugin {@link HubPluginPolicyApi}. */
public final class HubPluginPolicyApiAdapter implements HubPluginPolicyApi {

    private final HollowCrownHubPlugin hub;
    private final HubPluginPolicyService policies;

    public HubPluginPolicyApiAdapter(HollowCrownHubPlugin hub, HubPluginPolicyService policies) {
        this.hub = hub;
        this.policies = policies;
    }

    @Override
    public boolean isPluginAllowedInScope(String pluginName, Scope scope) {
        if (scope == null) return true;
        return policies.isPluginAllowedInScope(pluginName, toInternal(scope));
    }

    @Override
    public Scope resolveScope(Player player) {
        if (player == null) {
            return Scope.MATCH;
        }
        RegisteredServiceProvider<PreMatchLimboService> limboReg =
                Bukkit.getServicesManager().getRegistration(PreMatchLimboService.class);
        if (limboReg != null) {
            try {
                if (limboReg.getProvider().isInLimbo(player)) {
                    return Scope.LIMBO;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            if (hub.hubWorld().isInHubProtection(player)) {
                return Scope.HUB;
            }
        } catch (Throwable ignored) {
        }
        if (policies.isMatchWorld(player.getWorld())) {
            return Scope.MATCH;
        }
        return Scope.MATCH;
    }

    @Override
    public Scope resolveScope(World world) {
        if (world == null) {
            return Scope.MATCH;
        }
        if (policies.isMatchWorld(world)) {
            return Scope.MATCH;
        }
        return Scope.HUB;
    }

    @Override
    public Mode mode(Scope scope) {
        HubPluginPolicyService.ScopePolicy sp = policies.policy(toInternal(scope));
        return toApi(sp.mode());
    }

    @Override
    public Set<String> listedPlugins(Scope scope) {
        HubPluginPolicyService.ScopePolicy sp = policies.policy(toInternal(scope));
        return Collections.unmodifiableSet(new LinkedHashSet<>(sp.plugins()));
    }

    @Override
    public Set<String> alwaysAllowedPluginNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(policies.alwaysAllowedPluginNames()));
    }

    private static HubPluginPolicyService.Scope toInternal(Scope scope) {
        return switch (scope) {
            case HUB -> HubPluginPolicyService.Scope.HUB;
            case LIMBO -> HubPluginPolicyService.Scope.LIMBO;
            case MATCH -> HubPluginPolicyService.Scope.MATCH;
        };
    }

    private static Mode toApi(HubPluginPolicyService.Mode mode) {
        return switch (mode) {
            case WHITELIST -> Mode.WHITELIST;
            case BLACKLIST -> Mode.BLACKLIST;
        };
    }
}
