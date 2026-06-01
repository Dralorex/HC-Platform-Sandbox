package com.hollowcrown.hungergames.integration;

import com.hollowcrown.api.integration.IronLegacyHubImportResult;
import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.api.integration.IronLegacyTemplateRow;
import com.hollowcrown.api.integration.IronLegacyWorldsCatalog;
import com.hollowcrown.api.integration.HubTemplatePublishLifecycle;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import com.hollowcrown.hungergames.managers.TemplateWorldManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record IronLegacyTemplateAdminImpl(HungerGamesPlugin plugin) implements IronLegacyTemplateAdmin {

    private IronLegacyWorldsCatalog requireWorldsCatalog() {
        return plugin.worldsCatalog().orElseThrow(
                () -> new IllegalStateException("IronLegacyWorldsCatalog is required (managed by HollowCrownHub)."));
    }

    @Override
    public List<IronLegacyTemplateRow> listTemplates() {
        List<IronLegacyTemplateRow> out = new ArrayList<>();
        var list = plugin.worldsCatalog()
                .map(IronLegacyWorldsCatalog::listTemplateInfos)
                .orElseGet(() -> plugin.templates().listTemplateInfos().stream().map(t ->
                        new IronLegacyWorldsCatalog.TemplateInfo(
                                t.key(), t.worldName(), t.displayName(), t.status(), t.enabled(),
                                t.maxPlayers(), t.teamSizeMax(), t.iconKey(), t.version())).toList());
        for (IronLegacyWorldsCatalog.TemplateInfo info : list) {
            out.add(new IronLegacyTemplateRow(
                    info.key(),
                    info.worldName(),
                    info.displayName(),
                    info.status(),
                    info.enabled(),
                    info.maxPlayers(),
                    info.teamSizeMax(),
                    info.iconKey(),
                    info.version()));
        }
        return out;
    }

    @Override
    public boolean teleportToEditor(Player player, String templateKey) {
        if (player == null || templateKey == null) {
            return false;
        }
        plugin.templates().captureHubBuildModeIfLeavingLobby(player);
        plugin.templates().saveEditorInventoryIfTemplate(player);
        boolean ok = plugin.templates().teleportEditor(player, templateKey);
        if (!ok) {
            return false;
        }
        plugin.templates().applyEditorWorldRules(player.getWorld());
        if (!plugin.templates().restoreEditorInventory(player, templateKey)) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(new ItemStack(org.bukkit.Material.AIR));
        }
        player.setGameMode(GameMode.CREATIVE);
        return true;
    }

    @Override
    public boolean publish(String templateKey) {
        boolean ok = plugin.templates().publishTemplate(templateKey);
        if (ok) {
            RegisteredServiceProvider<HubTemplatePublishLifecycle> reg =
                    Bukkit.getServicesManager().getRegistration(HubTemplatePublishLifecycle.class);
            if (reg != null) {
                reg.getProvider().onTemplatePublished(templateKey);
            }
        }
        return ok;
    }

    @Override
    public boolean delete(String templateKey) {
        return plugin.templates().deleteTemplate(templateKey);
    }

    @Override
    public boolean rollback(String templateKey, String snapshotIdOrLatest) {
        return plugin.templates().rollbackTemplate(templateKey, snapshotIdOrLatest);
    }

    @Override
    public List<String> listSnapshotIds(String templateKey) {
        return plugin.templates().listSnapshotIds(templateKey);
    }

    @Override
    public List<String> reloadAll(String actorNameForHistory) {
        List<String> lines = new ArrayList<>();
        plugin.reloadConfig();
        try {
            plugin.files().reloadAll();
        } catch (Throwable ignored) {
        }
        plugin.spawns().load();
        try {
            HungerGamesPlugin.rules().load();
        } catch (Throwable ignored) {
        }
        try {
            plugin.templates().reload();
        } catch (Throwable ignored) {
        }
        var recovery = plugin.templates().recoverStaleInstances();
        try {
            plugin.icons().reload();
        } catch (Throwable ignored) {
        }
        var iconReport = plugin.icons().validateIcons();
        lines.add("Game files/cache reloaded.");
        lines.add("Instances recovery: scanned=" + recovery.scanned()
                + ", cleaned=" + recovery.cleaned()
                + ", skipped=" + recovery.skipped()
                + ", failed=" + recovery.failed());
        lines.add("Icons: total=" + iconReport.total() + ", valid=" + iconReport.valid()
                + ", warnings=" + iconReport.warnings().size());
        int maxWarnings = Math.min(5, iconReport.warnings().size());
        for (int i = 0; i < maxWarnings; i++) {
            lines.add("- " + iconReport.warnings().get(i));
        }
        if (iconReport.warnings().size() > maxWarnings) {
            lines.add("... and " + (iconReport.warnings().size() - maxWarnings) + " more warning(s).");
        }
        String actor = actorNameForHistory == null || actorNameForHistory.isBlank() ? "hub-admin" : actorNameForHistory;
        plugin.history().log(actor, "reload_all", "via_ironlegacy_admin_api");
        return lines;
    }

    @Override
    public IronLegacyHubImportResult importFromHub(boolean dryRun, boolean syncMeta, String singleTemplateKeyOrNull) {
        TemplateWorldManager.HubYamlImportReport r =
                plugin.templates().importFromHubTemplateRegistry(dryRun, syncMeta, singleTemplateKeyOrNull);
        return new IronLegacyHubImportResult(
                dryRun ? r.newRowsWouldWrite() : r.newRowsWritten(),
                r.skippedAlreadyPresent(),
                r.skippedNoHubSource(),
                dryRun ? r.metadataSyncWouldWrite() : r.metadataSyncWritten(),
                dryRun,
                r.messages());
    }

    @Override
    public void reloadWorldsYamlFromDisk() {
        // worlds.yml management is Hub-owned.
    }

    @Override
    public boolean setDisplayName(String templateKey, String displayName) {
        return requireWorldsCatalog().setDisplayName(templateKey, displayName);
    }

    @Override
    public boolean setEnabled(String templateKey, boolean enabled) {
        return requireWorldsCatalog().setEnabled(templateKey, enabled);
    }

    @Override
    public boolean setMaxPlayers(String templateKey, int maxPlayers) {
        return requireWorldsCatalog().setMaxPlayers(templateKey, maxPlayers);
    }

    @Override
    public boolean setTeamSizeMax(String templateKey, int teamSizeMax) {
        return requireWorldsCatalog().setTeamSizeMax(templateKey, teamSizeMax);
    }

    @Override
    public boolean setIconKey(String templateKey, String iconKey) {
        return requireWorldsCatalog().setIconKey(templateKey, iconKey);
    }

    @Override
    public boolean setMinimumPercentage(String templateKey, int percentage) {
        return requireWorldsCatalog().setMinimumPercentage(templateKey, percentage);
    }

    @Override
    public boolean setPublishGameRule(String templateKey, String ruleName, boolean value) {
        return requireWorldsCatalog().setPublishGameRule(templateKey, ruleName, value);
    }

    @Override
    public ItemStack buildIconPreview(String iconKey) {
        String key = iconKey == null || iconKey.isBlank() ? "default" : iconKey.trim();
        List<String> lore = List.of(
                "§7Key: §f" + key,
                "§7Reload icons via §e/hch template worlds reload§7.");
        return plugin.icons().buildIcon(key, "§bIcon Preview", lore);
    }
}
