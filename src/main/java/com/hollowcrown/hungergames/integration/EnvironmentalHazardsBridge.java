package com.hollowcrown.hungergames.integration;

import com.hollowcrown.api.hazard.EnvironmentalHazardsLifecycle;
import com.hollowcrown.api.hazard.EnvironmentalHazardsTriggerCleanup;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Calls HC-Environmental-Hazards services when Iron Legacy tears down worlds or publishes templates. */
public final class EnvironmentalHazardsBridge {

    private EnvironmentalHazardsBridge() {
    }

    private static Optional<EnvironmentalHazardsTriggerCleanup> cleanup() {
        RegisteredServiceProvider<EnvironmentalHazardsTriggerCleanup> r =
                Bukkit.getServicesManager().getRegistration(EnvironmentalHazardsTriggerCleanup.class);
        return r == null ? Optional.empty() : Optional.of(r.getProvider());
    }

    private static Optional<EnvironmentalHazardsLifecycle> lifecycle() {
        RegisteredServiceProvider<EnvironmentalHazardsLifecycle> r =
                Bukkit.getServicesManager().getRegistration(EnvironmentalHazardsLifecycle.class);
        return r == null ? Optional.empty() : Optional.of(r.getProvider());
    }

    public static void onTemplateDeleted(String templateKeyLower) {
        if (templateKeyLower == null || templateKeyLower.isBlank()) {
            return;
        }
        cleanup().ifPresent(c -> c.removeByTemplateKey(templateKeyLower.toLowerCase(Locale.ROOT)));
    }

    public static void onMatchWorldDestroyed(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        cleanup().ifPresent(c -> {
            c.removeByWorldName(worldName);
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                c.removeByWorldUuid(w.getUID());
            }
        });
    }

    public static void onHubLobbyReleased(UUID lobbyWorldUuid) {
        if (lobbyWorldUuid == null) {
            return;
        }
        cleanup().ifPresent(c -> c.removeByWorldUuid(lobbyWorldUuid));
    }

    public static void onTemplatePublished(String templateKeyLower) {
        if (templateKeyLower == null || templateKeyLower.isBlank()) {
            return;
        }
        lifecycle().ifPresent(l -> l.onIronLegacyTemplatePublished(templateKeyLower.toLowerCase(Locale.ROOT)));
    }
}
