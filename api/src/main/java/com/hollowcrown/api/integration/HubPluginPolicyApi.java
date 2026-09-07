package com.hollowcrown.api.integration;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;

/**
 * Hollow Crown Hub: cooperative plugin policy API.
 *
 * <p>Other Hollow Crown plugins should query this service before running mechanics in a given scope
 * (hub / limbo / match). If the policy reports the plugin as blocked, the calling plugin should
 * behave as if it does not exist for that player/world.</p>
 *
 * <p>Registered by HollowCrownHub when enabled. Querying plugins should fail-open (allow) when the
 * registration is missing, to remain usable on servers without HollowCrownHub.</p>
 */
public interface HubPluginPolicyApi {

    enum Scope {
        HUB,
        LIMBO,
        MATCH
    }

    enum Mode {
        WHITELIST,
        BLACKLIST
    }

    /**
     * @return true if the named plugin is allowed to run mechanics for the given scope.
     *         Hub and Foundation are always allowed.
     */
    boolean isPluginAllowedInScope(String pluginName, Scope scope);

    /** Convenience: checks {@link Plugin#getName()}. */
    default boolean isPluginAllowed(Plugin plugin, Scope scope) {
        if (plugin == null) return true;
        return isPluginAllowedInScope(plugin.getName(), scope);
    }

    /**
     * Resolve the effective policy scope for a player at the moment of the call.
     * Prefer this when a mechanic is keyed off a player.
     */
    Scope resolveScope(Player player);

    /**
     * Resolve the effective policy scope for a world (e.g. background tasks not bound to a player).
     * Returns MATCH for marked match worlds; HUB otherwise.
     */
    Scope resolveScope(World world);

    /**
     * Convenience: caller asks "am I allowed to act for this player?".
     */
    default boolean isPluginAllowedFor(Plugin plugin, Player player) {
        if (plugin == null || player == null) return true;
        return isPluginAllowedInScope(plugin.getName(), resolveScope(player));
    }

    /** @return the read-only mode for the requested scope. */
    Mode mode(Scope scope);

    /** @return lowercased plugin names currently on the list for the requested scope. */
    Set<String> listedPlugins(Scope scope);

    /** @return plugin names that are always allowed regardless of mode/list (Hub + Foundation). */
    Set<String> alwaysAllowedPluginNames();
}
