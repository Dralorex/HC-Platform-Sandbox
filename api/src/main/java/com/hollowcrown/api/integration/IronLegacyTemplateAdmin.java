package com.hollowcrown.api.integration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * HCIronLegacy template/map administration ({@code worlds.yml}, snapshots, icons).
 * Lobby zone/spawn editing is owned by HollowCrownHub; this API exposes a reload after Hub writes {@code worlds.yml}.
 *
 * <p>Registered by HCIronLegacy when present; HollowCrownHub resolves it from {@code ServicesManager}.</p>
 */
public interface IronLegacyTemplateAdmin {

    List<IronLegacyTemplateRow> listTemplates();

    boolean teleportToEditor(Player player, String templateKey);

    /** True when the player is standing in a registered template editor world (not a match instance). */
    boolean isInTemplateEditorWorld(Player player);

    /** Saves editor inventory and resume spot before leaving a template — does not teleport. */
    void prepareLeavingTemplateEditor(Player player);

    /** Restores hub build mode after returning from template edit (call once the player is at hub). */
    void finishHubReturnFromTemplateEditor(Player player);

    /**
     * Ensures a {@code worlds.yml} row exists for the canonical key (creates EDIT row from on-disk folder if missing).
     * Also resolves folders under {@code hch-worlds/templates/<name>}.
     */
    boolean ensureWorldTemplateRow(String templateKey);

    /**
     * Scans {@code hch-worlds/templates/} for world folders not yet in {@code worlds.yml} and registers them.
     *
     * @return newly registered template keys (leaf folder names, lowercase)
     */
    List<String> discoverManagedTemplateFolders();

    /**
     * All valid template folder leaf keys under {@code hch-worlds/templates/} (lowercase).
     */
    List<String> listManagedTemplateFolderKeys();

    /**
     * Ensures every on-disk managed template folder has a {@code worlds.yml} row.
     *
     * @return keys newly registered during this call
     */
    List<String> syncManagedTemplatesFromDisk();

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
     * Reload {@code worlds.yml} from disk (Hub writes lobby layout directly).
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
