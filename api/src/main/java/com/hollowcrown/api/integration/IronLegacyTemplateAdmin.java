package com.hollowcrown.api.integration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * HCIronLegacy template/map administration ({@code worlds.yml}, snapshots, icons).
 * Limbo zone/spawn editing is owned by HollowCrownHub; this API exposes a reload after Hub writes {@code worlds.yml}.
 *
 * <p>Registered by HCIronLegacy when present; HollowCrownHub resolves it from {@code ServicesManager}.</p>
 */
public interface IronLegacyTemplateAdmin {

    List<IronLegacyTemplateRow> listTemplates();

    boolean teleportToEditor(Player player, String templateKey);

    boolean publish(String templateKey);

    boolean delete(String templateKey);

    boolean rollback(String templateKey, String snapshotIdOrLatest);

    List<String> listSnapshotIds(String templateKey);

    /**
     * Reload IL configs, templates, icons, rules subset — same scope as legacy {@code /hg game reload}.
     *
     * @param actorNameForHistory admin name printed in IL audit trail
     */
    List<String> reloadAll(String actorNameForHistory);

    IronLegacyHubImportResult importFromHub(boolean dryRun, boolean syncMeta, String singleTemplateKeyOrNull);

    /**
     * Reload {@code worlds.yml} from disk (Hub writes limbo layout directly).
     * Safe to call from the main thread after saves.
     */
    void reloadWorldsYamlFromDisk();

    boolean setDisplayName(String templateKey, String displayName);

    boolean setEnabled(String templateKey, boolean enabled);

    boolean setMaxPlayers(String templateKey, int maxPlayers);

    boolean setTeamSizeMax(String templateKey, int teamSizeMax);

    boolean setTeamFriendlyFire(String templateKey, boolean teamFriendlyFire);

    boolean setAllyFriendlyFire(String templateKey, boolean allyFriendlyFire);

    boolean setIconKey(String templateKey, String iconKey);

    boolean setMinimumPercentage(String templateKey, int percentage);

    boolean setPublishGameRule(String templateKey, String ruleName, boolean value);

    ItemStack buildIconPreview(String iconKey);
}
