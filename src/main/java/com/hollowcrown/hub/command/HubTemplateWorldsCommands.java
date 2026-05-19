package com.hollowcrown.hub.command;

import com.hollowcrown.api.integration.IronLegacyHubImportResult;
import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.api.integration.IronLegacyTemplateRow;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.gui.HubIlIconPreviewMenu;
import com.hollowcrown.hub.gui.HubIlTemplateListMenu;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
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

    /** Resolves YAML key or world folder name to canonical worlds.yml key; prints on failure. */
    private static Optional<String> resolveOrTell(CommandSender sender, IronLegacyTemplateAdmin admin, String raw) {
        Optional<String> k = HubIlWorldInput.resolveCanonicalKey(admin, raw);
        if (k.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Unknown Iron Legacy map \"" + raw + "\". Use the world folder name or worlds.yml key "
                            + "(/hch template worlds list).",
                    NamedTextColor.RED));
        }
        return k;
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
                sender.sendMessage(Component.text("Reload finished.", NamedTextColor.GREEN));
                yield true;
            }
            case "list" -> {
                for (String line : summarize(admin)) {
                    sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
                }
                yield true;
            }
            case "gui" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    yield true;
                }
                p.openInventory(new HubIlTemplateListMenu(plugin, admin, 0).getInventory());
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
            case "tp", "teleport", "edit" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    yield true;
                }
                if (args.length < 4) {
                    sender.sendMessage(Component.text(
                            "Usage: /hch template worlds tp <worldFolder|yamlKey>", NamedTextColor.YELLOW));
                    yield true;
                }
                Optional<String> canon = resolveOrTell(sender, admin, args[3]);
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
                Optional<String> canonPub = resolveOrTell(sender, admin, args[3]);
                if (canonPub.isEmpty()) {
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
                Optional<String> canonDel = resolveOrTell(sender, admin, args[3]);
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
                Optional<String> canonRb = resolveOrTell(sender, admin, args[3]);
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
                Optional<String> canonSn = resolveOrTell(sender, admin, args[3]);
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
                Optional<String> canonTg = resolveOrTell(sender, admin, args[3]);
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
                " list · reload · gui · importhub [key] [--dry-run] [--sync]",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                " tp <world|key> · publish <world|key> · delete <world|key> · rollback <world|key> [snap]",
                NamedTextColor.GRAY));
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
