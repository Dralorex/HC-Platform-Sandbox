package com.hollowcrown.hub.template;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import com.hollowcrown.hub.lobby.HubLobbyInstanceRecord;
import org.bukkit.Bukkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Registers dropped world folders under {@code hch-worlds/templates/} in IL {@code worlds.yml}
 * and Hub {@code templates.yml} (with {@link HubTemplateWorldBootstrap#IRON_LEGACY_MECHANIC_ID} when unset).
 * Also prunes registry rows / lobbies / queues when those folders are gone.
 */
public final class HubManagedTemplateBootstrap {

    private HubManagedTemplateBootstrap() {
    }

    public record SyncResult(
            List<String> newlyRegisteredIlKeys,
            List<String> syncedHubKeys,
            List<String> prunedKeys
    ) {
    }

    /**
     * Runs on server start (when enabled in config) and from {@code /hch template worlds discover}.
     *
     * @return empty result when Iron Legacy admin API is unavailable
     */
    public static SyncResult syncFromDisk(HollowCrownHubPlugin plugin) {
        return HubIronLegacySupport.ilAdmin()
                .map(admin -> syncFromDisk(plugin, admin))
                .orElseGet(() -> new SyncResult(List.of(), List.of(), List.of()));
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
        List<String> pruned = pruneMissingTemplates(plugin, admin);
        return new SyncResult(ilNew, hubSynced, pruned);
    }

    /**
     * Removes Hub / IL / lobby / queue / policy rows for template worlds whose folders no longer exist.
     */
    public static List<String> pruneMissingTemplates(HollowCrownHubPlugin plugin, IronLegacyTemplateAdmin admin) {
        Set<String> toPrune = new LinkedHashSet<>();

        List<String> ilPruned = List.of();
        try {
            ilPruned = admin.pruneMissingWorldFolders();
            toPrune.addAll(ilPruned);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "IL prune of missing template folders failed", t);
        }

        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        for (HubTemplateEntry entry : new ArrayList<>(plugin.templates().list())) {
            String key = entry.templateKey();
            if (templateSourceExists(container, entry)) {
                continue;
            }
            toPrune.add(key.toLowerCase(Locale.ROOT));
        }

        List<String> removed = new ArrayList<>();
        for (String key : toPrune) {
            try {
                purgeTemplateEverywhere(plugin, key);
                removed.add(key);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to purge missing template '" + key + "'", t);
            }
        }
        if (!removed.isEmpty()) {
            plugin.getLogger().info("Pruned missing template worlds: " + String.join(", ", removed));
        }
        return removed;
    }

    private static void purgeTemplateEverywhere(HollowCrownHubPlugin plugin, String key) {
        String nk = key.toLowerCase(Locale.ROOT);

        for (HubLobbyInstanceRecord r : new ArrayList<>(plugin.lobbyService().instances().all())) {
            if (r.templateKey() != null && r.templateKey().equalsIgnoreCase(nk)) {
                try {
                    plugin.lobbyService().destroyLobby(r.id(), false);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING,
                            "Could not destroy lobby " + r.id() + " for pruned template " + nk, t);
                    plugin.lobbyService().instances().remove(r.id());
                }
            }
        }

        try {
            plugin.matchQueueService().dropTemplate(nk);
        } catch (Throwable ignored) {
        }

        try {
            plugin.templatePolicies().removeTemplate(nk);
        } catch (Throwable ignored) {
        }

        if (plugin.templates().remove(nk)) {
            plugin.getLogger().info("Removed Hub templates.yml row for missing world: " + nk);
        }
    }

    private static boolean templateSourceExists(Path worldContainer, HubTemplateEntry entry) {
        List<String> candidates = new ArrayList<>();
        if (entry.sourceWorldName() != null && !entry.sourceWorldName().isBlank()) {
            candidates.add(entry.sourceWorldName().replace('\\', '/'));
        }
        candidates.add(HubTemplateWorldBootstrap.managedFolderPath(entry.templateKey()));
        for (String c : candidates) {
            if (c == null || c.isBlank()) {
                continue;
            }
            Path p = worldContainer.resolve(c).normalize();
            if (!p.startsWith(worldContainer)) {
                continue;
            }
            if (Files.isDirectory(p) && looksLikeMinecraftWorld(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeMinecraftWorld(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return false;
        }
        if (Files.isRegularFile(folder.resolve("level.dat"))
                || Files.isRegularFile(folder.resolve("level.dat_old"))) {
            return true;
        }
        return Files.isDirectory(folder.resolve("region"))
                || Files.isDirectory(folder.resolve("DIM-1"))
                || Files.isDirectory(folder.resolve("DIM1"));
    }

    public static void syncFromDiskOnStartup(HollowCrownHubPlugin plugin) {
        boolean discover = plugin.getConfig().getBoolean("templates.autoDiscoverOnStartup", true);
        boolean prune = plugin.getConfig().getBoolean("templates.pruneMissingOnStartup", true);
        if (!discover && !prune) {
            return;
        }
        SyncResult result;
        if (discover) {
            result = syncFromDisk(plugin);
        } else {
            result = HubIronLegacySupport.ilAdmin()
                    .map(admin -> new SyncResult(List.of(), List.of(), pruneMissingTemplates(plugin, admin)))
                    .orElseGet(() -> new SyncResult(List.of(), List.of(), List.of()));
        }
        if (!result.newlyRegisteredIlKeys().isEmpty()) {
            plugin.getLogger().info("Startup template discover (IL worlds.yml): "
                    + String.join(", ", result.newlyRegisteredIlKeys()));
        }
        if (!result.syncedHubKeys().isEmpty()) {
            plugin.getLogger().info("Startup template discover (Hub templates.yml): "
                    + String.join(", ", result.syncedHubKeys()));
        }
        if (!result.prunedKeys().isEmpty()) {
            plugin.getLogger().info("Startup template prune (missing folders): "
                    + String.join(", ", result.prunedKeys()));
        }
    }
}
