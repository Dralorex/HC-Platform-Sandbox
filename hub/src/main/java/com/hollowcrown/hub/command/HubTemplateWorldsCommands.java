package com.hollowcrown.hub.command;

import com.hollowcrown.api.integration.IronLegacyHubImportResult;
import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.api.integration.IronLegacyTemplateRow;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.gui.HubIlIconPreviewMenu;
import com.hollowcrown.hub.gui.HubIlTemplateListMenu;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import com.hollowcrown.hub.template.HubMechanics;
import com.hollowcrown.hub.template.HubTemplatePublishGate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /hch template worlds …} — Iron Legacy {@code worlds.yml} admin (requires HCIronLegacy).
 *
 * <p>{@code args[0]} = {@code template}, {@code args[1]} = {@code worlds}.</p>
 */
public final class HubTemplateWorldsCommands {

    private HubTemplateWorldsCommands() {
    }

    /**
     * Resolves YAML key or world folder name to canonical worlds.yml key.
     * If unknown, tries to adopt an on-disk folder under {@code hch-worlds/templates/}
     * (or a bare world folder) into worlds.yml + Hub templates.yml, then resolves again.
     */
    private static Optional<String> resolveOrTell(
            HollowCrownHubPlugin plugin,
            CommandSender sender,
            IronLegacyTemplateAdmin admin,
            String raw
    ) {
        Optional<String> k = HubIlWorldInput.resolveCanonicalKey(admin, raw);
        if (k.isPresent()) {
            return k;
        }
        if (tryAdoptDiskFolder(plugin, admin, raw)) {
            k = HubIlWorldInput.resolveCanonicalKey(admin, raw);
            if (k.isPresent()) {
                sender.sendMessage(Component.text(
                        "Registered on-disk template: " + HubIlWorldInput.folderLabel(admin, k.get()),
                        NamedTextColor.YELLOW));
                return k;
            }
        }
        sender.sendMessage(Component.text(
                "Unknown Iron Legacy map \"" + raw + "\". Drop a world into hch-worlds/templates/<name> "
                        + "then run /hch template worlds discover — or use /hch template create <key>.",
                NamedTextColor.RED));
        return Optional.empty();
    }

    /** Registers worlds.yml + Hub row when the folder exists on disk but is not listed yet. */
    private static boolean tryAdoptDiskFolder(
            HollowCrownHubPlugin plugin,
            IronLegacyTemplateAdmin admin,
            String raw
    ) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        if (!admin.ensureWorldTemplateRow(raw)) {
            // Full scan may pick it up if the user used a partial/odd name
            admin.discoverManagedTemplateFolders();
            if (!admin.ensureWorldTemplateRow(raw)) {
                return false;
            }
        }
        Optional<String> key = HubIlWorldInput.resolveCanonicalKey(admin, raw);
        String leaf = key.orElseGet(() -> {
            String n = raw.trim().replace('\\', '/');
            int slash = n.lastIndexOf('/');
            return slash >= 0 ? n.substring(slash + 1) : n;
        });
        try {
            plugin.templates().adoptManagedTemplateFolder(leaf);
        } catch (IllegalArgumentException ignored) {
            // Key syntax invalid for Hub — worlds.yml row alone is still enough for edit.
        }
        return HubIlWorldInput.resolveCanonicalKey(admin, raw).isPresent()
                || HubIlWorldInput.resolveCanonicalKey(admin, leaf).isPresent();
    }

    public static boolean handle(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.admin")) {
            sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
            return true;
        }
        var il = HubIronLegacySupport.ilAdmin();
        if (il.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Iron Legacy admin API unavailable (load HCIronLegacy on this server).",
                    NamedTextColor.RED));
            return true;
        }
        IronLegacyTemplateAdmin admin = il.get();
        if (args.length < 3) {
            help(sender);
            return true;
        }
        String sub = args[2].toLowerCase(Locale.ROOT);
        String actor = sender.getName();
        return switch (sub) {
            case "help", "?" -> {
                help(sender);
                yield true;
            }
            case "reload" -> {
                for (String line : admin.reloadAll(actor)) {
                    sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
                }
                List<String> found = discoverAndAdopt(plugin, admin);
                if (!found.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "Discovered " + found.size() + " managed template folder(s): " + found,
                            NamedTextColor.GREEN));
                }
                sender.sendMessage(Component.text("Reload finished.", NamedTextColor.GREEN));
                yield true;
            }
            case "list" -> {
                discoverAndAdopt(plugin, admin);
                for (String line : summarize(admin)) {
                    sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
                }
                yield true;
            }
            case "discover" -> {
                List<String> found = discoverAndAdopt(plugin, admin);
                if (found.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "No new folders under hch-worlds/templates/ (need level.dat or region/). "
                                    + "Already registered maps are unchanged.",
                            NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text(
                            "Registered " + found.size() + " template(s) from disk: " + String.join(", ", found),
                            NamedTextColor.GREEN));
                    sender.sendMessage(Component.text(
                            "Edit with /hch template worlds edit <key> — set mechanic before publish.",
                            NamedTextColor.GRAY));
                }
                yield true;
            }
            case "gui" -> {
                sender.sendMessage(Component.text(
                        "Use /hch worldsgui instead of /hch template worlds gui.",
                        NamedTextColor.YELLOW));
                yield true;
            }
            case "mechanic" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text(
                            "Usage: /hch template worlds mechanic <world|key> <mechanicName>",
                            NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(
                            "Use mechanic id \"vanilla\" for non-minigame worlds.", NamedTextColor.GRAY));
                    yield true;
                }
                Optional<String> canonMech = resolveOrTell(plugin, sender, admin, args[3]);
                if (canonMech.isEmpty()) {
                    yield true;
                }
                String mechanicId = args[4].trim().toLowerCase(Locale.ROOT);
                Optional<com.hollowcrown.hub.template.HubTemplateEntry> hubRow =
                        HubTemplatePublishGate.resolveHubRow(plugin, admin, canonMech.get());
                if (hubRow.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "No Hub template linked to that world. Run /hch template setsource <hubKey> "
                                    + HubIlWorldInput.folderLabel(admin, canonMech.get()) + " first.",
                            NamedTextColor.RED));
                    yield true;
                }
                try {
                    plugin.templates().setMechanic(hubRow.get().templateKey(), mechanicId);
                    sender.sendMessage(Component.text(
                            "Mechanic for " + hubRow.get().templateKey() + " set to " + mechanicId + ".",
                            NamedTextColor.GREEN));
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
                }
                yield true;
            }
            case "importhub" -> {
                boolean dryRun = false;
                boolean syncMeta = false;
                String oneKey = null;
                for (int i = 3; i < args.length; i++) {
                    String a = args[i];
                    if ("--dry-run".equalsIgnoreCase(a)) {
                        dryRun = true;
                    } else if ("--sync".equalsIgnoreCase(a)) {
                        syncMeta = true;
                    } else if (a.startsWith("--")) {
                        sender.sendMessage(Component.text("Unknown flag: " + a, NamedTextColor.RED));
                        yield true;
                    } else if (oneKey != null) {
                        sender.sendMessage(Component.text("Only one template key allowed.", NamedTextColor.RED));
                        yield true;
                    } else {
                        oneKey = a;
                    }
                }
                IronLegacyHubImportResult r = admin.importFromHub(dryRun, syncMeta, oneKey);
                sender.sendMessage(Component.text(
                        "Hub import" + (dryRun ? " (dry-run)" : "") + ": new=" + r.newRowsWritten()
                                + " skipped=" + r.skippedAlreadyPresent()
                                + " noSource=" + r.skippedNoHubSource()
                                + " metaSync=" + r.metadataSyncWritten(),
                        NamedTextColor.GOLD));
                for (String line : r.messages()) {
                    sender.sendMessage(Component.text(line, NamedTextColor.DARK_GRAY));
                }
                if (!dryRun && (r.newRowsWritten() > 0 || r.metadataSyncWritten() > 0)) {
                    sender.sendMessage(Component.text("Tip: run /hch template worlds reload", NamedTextColor.YELLOW));
                }
                yield true;
            }
            case "edit" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    yield true;
                }
                if (args.length < 4) {
                    sender.sendMessage(Component.text(
                            "Usage: /hch template worlds edit <worldFolder|yamlKey>", NamedTextColor.YELLOW));
                    yield true;
                }
                Optional<String> canon = resolveOrTell(plugin, sender, admin, args[3]);
                if (canon.isEmpty()) {
                    yield true;
                }
                if (admin.teleportToEditor(p, canon.get())) {
                    p.sendMessage(Component.text(
                            "Template editor: " + HubIlWorldInput.folderLabel(admin, canon.get()), NamedTextColor.GREEN));
                } else {
                    p.sendMessage(Component.text("Could not open template (unknown key or world missing?).", NamedTextColor.RED));
                }
                yield true;
            }
            case "publish" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text(
                            "Usage: /hch template worlds publish <worldFolder|yamlKey>", NamedTextColor.YELLOW));
                    yield true;
                }
                Optional<String> canonPub = resolveOrTell(plugin, sender, admin, args[3]);
                if (canonPub.isEmpty()) {
                    yield true;
                }
                admin.ensureWorldTemplateRow(canonPub.get());
                if (!HubTemplatePublishGate.canPublish(plugin, admin, canonPub.get())) {
                    HubTemplatePublishGate.sendMechanicRequired(sender);
                    if (sender instanceof org.bukkit.entity.Player pl) {
                        plugin.templatePublishPending().register(pl.getUniqueId(), canonPub.get());
                    }
                    yield true;
                }
                if (admin.publish(canonPub.get())) {
                    sender.sendMessage(Component.text(
                            "Published: " + HubIlWorldInput.folderLabel(admin, canonPub.get()), NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Publish failed.", NamedTextColor.RED));
                    HubIlAdminHints.explainPublishFailure(sender, admin, canonPub.get());
                }
                yield true;
            }
            case "delete" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text(
                            "Usage: /hch template worlds delete <worldFolder|yamlKey>", NamedTextColor.YELLOW));
                    yield true;
                }
                Optional<String> canonDel = resolveOrTell(plugin, sender, admin, args[3]);
                if (canonDel.isEmpty()) {
                    yield true;
                }
                if (admin.delete(canonDel.get())) {
                    sender.sendMessage(Component.text(
                            "Deleted: " + HubIlWorldInput.folderLabel(admin, canonDel.get()), NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Delete failed (players inside or missing?).", NamedTextColor.RED));
                }
                yield true;
            }
            case "rollback" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text(
                            "Usage: /hch template worlds rollback <worldFolder|yamlKey> [snapshot|latest]",
                            NamedTextColor.YELLOW));
                    yield true;
                }
                Optional<String> canonRb = resolveOrTell(plugin, sender, admin, args[3]);
                if (canonRb.isEmpty()) {
                    yield true;
                }
                String snap = args.length >= 5 ? args[4] : "latest";
                if (admin.rollback(canonRb.get(), snap)) {
                    sender.sendMessage(Component.text(
                            "Rollback OK: " + HubIlWorldInput.folderLabel(admin, canonRb.get()) + " @ " + snap,
                            NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Rollback failed.", NamedTextColor.RED));
                }
                yield true;
            }
            case "snapshots" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text(
                            "Usage: /hch template worlds snapshots <worldFolder|yamlKey>", NamedTextColor.YELLOW));
                    yield true;
                }
                Optional<String> canonSn = resolveOrTell(plugin, sender, admin, args[3]);
                if (canonSn.isEmpty()) {
                    yield true;
                }
                List<String> ids = admin.listSnapshotIds(canonSn.get());
                sender.sendMessage(Component.text(
                        "Snapshots (" + ids.size() + ") for " + HubIlWorldInput.folderLabel(admin, canonSn.get()) + ":",
                        NamedTextColor.GOLD));
                int cap = Math.min(40, ids.size());
                for (int i = 0; i < cap; i++) {
                    sender.sendMessage(Component.text(" - " + ids.get(i), NamedTextColor.GRAY));
                }
                if (ids.size() > cap) {
                    sender.sendMessage(Component.text("... (" + (ids.size() - cap) + " more)", NamedTextColor.DARK_GRAY));
                }
                yield true;
            }
            case "toggle" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text(
                            "Usage: /hch template worlds toggle <worldFolder|yamlKey>", NamedTextColor.YELLOW));
                    yield true;
                }
                Optional<String> canonTg = resolveOrTell(plugin, sender, admin, args[3]);
                if (canonTg.isEmpty()) {
                    yield true;
                }
                String canon = canonTg.get();
                boolean cur = admin.listTemplates().stream()
                        .filter(r -> r.key().equalsIgnoreCase(canon))
                        .findFirst()
                        .map(IronLegacyTemplateRow::enabled)
                        .orElse(true);
                boolean next = !cur;
                if (admin.setEnabled(canon, next)) {
                    sender.sendMessage(Component.text(
                            "enabled=" + next + " for " + HubIlWorldInput.folderLabel(admin, canon),
                            NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("toggle failed.", NamedTextColor.RED));
                }
                yield true;
            }
            case "iconpreview" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    yield true;
                }
                String ik = args.length >= 4 ? args[3] : "default";
                p.openInventory(new HubIlIconPreviewMenu(admin.buildIconPreview(ik)).getInventory());
                yield true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown worlds subcommand. /hch template worlds help", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private static List<String> discoverAndAdopt(HollowCrownHubPlugin plugin, IronLegacyTemplateAdmin admin) {
        List<String> found = new ArrayList<>(admin.discoverManagedTemplateFolders());
        for (String key : found) {
            try {
                plugin.templates().adoptManagedTemplateFolder(key);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return found;
    }

    private static List<String> summarize(IronLegacyTemplateAdmin admin) {
        List<String> lines = new ArrayList<>();
        var list = admin.listTemplates();
        if (list.isEmpty()) {
            lines.add("No templates.");
            return lines;
        }
        lines.add("Templates (" + list.size() + ") — CLI accepts world folder or yaml key:");
        for (var r : list) {
            lines.add("- " + r.worldName() + " · key=" + r.key() + " | " + r.status() + " | v" + r.version()
                    + " | en=" + r.enabled());
        }
        return lines;
    }

    private static void help(CommandSender sender) {
        sender.sendMessage(Component.text("Iron Legacy worlds (/hch template worlds)", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                " list · discover · reload · importhub [key] [--dry-run] [--sync]",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                " discover — scan hch-worlds/templates/ and register dropped world folders",
                NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(
                " edit <world|key> · publish <world|key> · mechanic <world|key> <id>",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                " delete <world|key> · rollback <world|key> [snap]",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                " GUI: /hch worldsgui  (replaces template worlds gui)",
                NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(
                " snapshots <world|key> · toggle <world|key> · iconpreview [iconKey]",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Limbo: /hch template limbo wand <world|key> · limbo spawn",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "World fields: /hch template set <world|key> display|enabled|…",
                NamedTextColor.DARK_GRAY));
    }
}
