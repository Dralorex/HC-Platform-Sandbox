package com.hollowcrown.hub.template;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.integration.HubIronLegacySupport;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Registers dropped world folders under {@code hch-worlds/templates/} in IL {@code worlds.yml}
 * and Hub {@code templates.yml} (with {@link HubTemplateWorldBootstrap#IRON_LEGACY_MECHANIC_ID} when unset).
 */
public final class HubManagedTemplateBootstrap {

    private HubManagedTemplateBootstrap() {
    }

    public record SyncResult(List<String> newlyRegisteredIlKeys, List<String> syncedHubKeys) {
    }

    /**
     * Runs on server start (when enabled in config) and from {@code /hch template worlds discover}.
     *
     * @return empty result when Iron Legacy admin API is unavailable
     */
    public static SyncResult syncFromDisk(HollowCrownHubPlugin plugin) {
        return HubIronLegacySupport.ilAdmin()
                .map(admin -> syncFromDisk(plugin, admin))
                .orElseGet(() -> new SyncResult(List.of(), List.of()));
    }

    public static SyncResult syncFromDisk(HollowCrownHubPlugin plugin, IronLegacyTemplateAdmin admin) {
        List<String> ilNew = admin.syncManagedTemplatesFromDisk();
        List<String> hubSynced = new ArrayList<>();
        for (String key : admin.listManagedTemplateFolderKeys()) {
            try {
                admin.ensureWorldTemplateRow(key);
                plugin.templates().adoptManagedTemplateFolder(key);
                var hubRow = plugin.templates().get(key);
                if (hubRow.isPresent() && !hubRow.get().hasMechanicSelected()) {
                    plugin.templates().setMechanic(key, HubTemplateWorldBootstrap.IRON_LEGACY_MECHANIC_ID);
                }
                hubSynced.add(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Skipped managed template folder '" + key + "': " + ex.getMessage());
            }
        }
        return new SyncResult(ilNew, hubSynced);
    }

    public static void syncFromDiskOnStartup(HollowCrownHubPlugin plugin) {
        if (!plugin.getConfig().getBoolean("templates.autoDiscoverOnStartup", true)) {
            return;
        }
        SyncResult result = syncFromDisk(plugin);
        if (!result.newlyRegisteredIlKeys().isEmpty()) {
            plugin.getLogger().info("Startup template discover (IL worlds.yml): "
                    + String.join(", ", result.newlyRegisteredIlKeys()));
        }
        if (!result.syncedHubKeys().isEmpty()) {
            plugin.getLogger().info("Startup template discover (Hub templates.yml): "
                    + String.join(", ", result.syncedHubKeys()));
        }
    }
}
