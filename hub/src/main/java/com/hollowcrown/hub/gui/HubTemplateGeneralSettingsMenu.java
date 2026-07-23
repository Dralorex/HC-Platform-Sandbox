package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import com.hollowcrown.hub.policy.HubTemplatePolicyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-template general settings: color mode, sneak hide, TAB visibility, allies enable/max, team size,
 * minimum fill %, TP to editor. 4 rows.
 */
public final class HubTemplateGeneralSettingsMenu extends HubMenuView {

    private static final int SIZE = 36;

    // Row 1 (top): tab headers (visual: this page is the General tab)
    private static final int SLOT_TAB_GENERAL = 3;
    private static final int SLOT_TAB_BLACKLIST = 5;

    // Row 2 + 3: controls
    private static final int SLOT_COLOR_MODE = 10;
    private static final int SLOT_SHIFT_HIDE = 11;
    private static final int SLOT_TAB_VISIBLE = 12;
    private static final int SLOT_ALLIES_TOGGLE = 13;
    private static final int SLOT_ALLY_MAX = 14;
    private static final int SLOT_FRIENDLY_FIRE = 15;
    private static final int SLOT_TEAM_FRIENDLY_FIRE = 23;
    private static final int SLOT_TP_EDITOR = 16;
    private static final int SLOT_TEAM_SIZE = 19;
    private static final int SLOT_MIN_PCT = 20;
    private static final int SLOT_MAX_PLAYERS = 21;

    // Row 4: nav
    private static final int SLOT_INFO = 28;
    private static final int SLOT_BACK = 31;
    private static final int SLOT_CLOSE = 34;

    private final HollowCrownHubPlugin plugin;
    private final String templateKey;
    private final Inventory inv;

    public String templateKey() {
        return templateKey;
    }

    public HubTemplateGeneralSettingsMenu(HollowCrownHubPlugin plugin, String templateKey) {
        this.plugin = plugin;
        this.templateKey = templateKey;
        this.inv = Bukkit.createInventory(this, SIZE,
                Component.text("HCH · " + templateKey + " · Settings", NamedTextColor.AQUA));
        build();
    }

    private void build() {
        inv.clear();
        HubTemplatePolicyService.TemplateConfig cfg = plugin.templatePolicies().configFor(templateKey);

        inv.setItem(SLOT_TAB_GENERAL, btn(Material.LIME_STAINED_GLASS_PANE,
                Component.text("General Settings", NamedTextColor.GREEN),
                List.of(Component.text("You are here", NamedTextColor.DARK_GREEN))));
        inv.setItem(SLOT_TAB_BLACKLIST, btn(Material.GRAY_STAINED_GLASS_PANE,
                Component.text("Plugin Allocation", NamedTextColor.GRAY),
                List.of(Component.text("Click to switch", NamedTextColor.DARK_GRAY))));

        boolean team = cfg.colorMode() == HubTemplatePolicyService.ColorMode.TEAM;
        inv.setItem(SLOT_COLOR_MODE, btn(team ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text("Color Mode: " + (team ? "TEAM" : "LEGACY"),
                        team ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                List.of(
                        Component.text("TEAM: per-viewer green/red/blue/orange", NamedTextColor.GRAY),
                        Component.text("        + full RGB picker.", NamedTextColor.GRAY),
                        Component.text("LEGACY: scoreboard-team coloring,", NamedTextColor.GRAY),
                        Component.text("        16 legacy colors only.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to toggle", NamedTextColor.YELLOW))));

        inv.setItem(SLOT_SHIFT_HIDE, btn(cfg.shiftHidesNameTag() ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text("Sneak hides name tag: " + (cfg.shiftHidesNameTag() ? "ON" : "OFF"),
                        cfg.shiftHidesNameTag() ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(
                        Component.text("When ON, sneaking players are hidden", NamedTextColor.GRAY),
                        Component.text("from all viewers except their own teammates.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to toggle", NamedTextColor.YELLOW))));

        inv.setItem(SLOT_TAB_VISIBLE, btn(cfg.tabVisible() ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text("TAB list visible: " + (cfg.tabVisible() ? "ON" : "OFF"),
                        cfg.tabVisible() ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(
                        Component.text("When OFF, the TAB key shows nothing", NamedTextColor.GRAY),
                        Component.text("during matches of this template.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to toggle", NamedTextColor.YELLOW))));

        inv.setItem(SLOT_ALLIES_TOGGLE, btn(cfg.alliesEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text("Allies (/ally): " + (cfg.alliesEnabled() ? "ENABLED" : "DISABLED"),
                        cfg.alliesEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(
                        Component.text("When ON, team leaders can form", NamedTextColor.GRAY),
                        Component.text("alliances with /ally <team>.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to toggle", NamedTextColor.YELLOW))));

        inv.setItem(SLOT_ALLY_MAX, btn(Material.SHIELD,
                Component.text("Max teams per alliance: " + cfg.maxTeamsPerAlliance(), NamedTextColor.AQUA),
                List.of(
                        Component.text("Left-click: +1", NamedTextColor.GRAY),
                        Component.text("Right-click: -1 (min 2)", NamedTextColor.GRAY))));

        inv.setItem(SLOT_FRIENDLY_FIRE, btn(cfg.friendlyFireEnabled() ? Material.ORANGE_DYE : Material.GRAY_DYE,
                Component.text("Ally Friendly Fire: " + (cfg.friendlyFireEnabled() ? "ON" : "OFF"),
                        cfg.friendlyFireEnabled() ? NamedTextColor.GOLD : NamedTextColor.RED),
                List.of(
                        Component.text("Applies to allied teams only.", NamedTextColor.GRAY),
                        Component.text("Default is ON.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to toggle", NamedTextColor.YELLOW))));

        inv.setItem(SLOT_TEAM_FRIENDLY_FIRE, btn(cfg.teamFriendlyFireEnabled() ? Material.ORANGE_DYE : Material.GRAY_DYE,
                Component.text("Team Friendly Fire: " + (cfg.teamFriendlyFireEnabled() ? "ON" : "OFF"),
                        cfg.teamFriendlyFireEnabled() ? NamedTextColor.GOLD : NamedTextColor.RED),
                List.of(
                        Component.text("Applies to same-team players.", NamedTextColor.GRAY),
                        Component.text("Default is ON.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to toggle", NamedTextColor.YELLOW))));

        inv.setItem(SLOT_TP_EDITOR, btn(Material.ENDER_PEARL,
                Component.text("Teleport to template editor", NamedTextColor.GOLD),
                List.of(
                        Component.text("Same as /hch template worlds edit " + templateKey,
                                NamedTextColor.GRAY))));

        // Team size + min % + max players (delegated to IL admin when present)
        var ilOpt = HubIronLegacySupport.ilAdmin();
        var rowOpt = ilOpt.flatMap(a -> a.listTemplates().stream()
                .filter(r -> r.key().equalsIgnoreCase(templateKey)).findFirst());
        int teamSize = rowOpt.map(r -> r.teamSizeMax()).orElse(-1);
        int maxPlayers = rowOpt.map(r -> r.maxPlayers()).orElse(-1);
        int minPct = plugin.limboLayout().worldsYaml().minimumPercentage(templateKey);

        inv.setItem(SLOT_TEAM_SIZE, btn(Material.IRON_SWORD,
                Component.text("Team size max: " + (teamSize > 0 ? teamSize : "—"), NamedTextColor.AQUA),
                List.of(
                        Component.text("Left-click: +1", NamedTextColor.GRAY),
                        Component.text("Right-click: -1 (min 1)", NamedTextColor.GRAY))));

        inv.setItem(SLOT_MIN_PCT, btn(Material.CLOCK,
                Component.text("Min fill %: " + (minPct > 0 ? minPct : "—"), NamedTextColor.AQUA),
                List.of(
                        Component.text("Left-click: +5", NamedTextColor.GRAY),
                        Component.text("Right-click: -5 (1-100)", NamedTextColor.GRAY))));

        inv.setItem(SLOT_MAX_PLAYERS, btn(Material.PLAYER_HEAD,
                Component.text("Max players: " + (maxPlayers > 0 ? maxPlayers : "—"), NamedTextColor.AQUA),
                List.of(
                        Component.text("Left-click: +1", NamedTextColor.GRAY),
                        Component.text("Right-click: -1 (min 2)", NamedTextColor.GRAY))));

        inv.setItem(SLOT_INFO, btn(Material.IRON_BARS,
                Component.text("Template: " + templateKey, NamedTextColor.YELLOW),
                List.of(Component.text("Changes save when you close this menu.", NamedTextColor.GRAY))));
        inv.setItem(SLOT_BACK, btn(Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Template landing", NamedTextColor.GRAY))));
        inv.setItem(SLOT_CLOSE, btn(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED), List.of()));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        boolean right = e.isRightClick();

        switch (slot) {
            case SLOT_TAB_GENERAL -> { /* already here */ }
            case SLOT_TAB_BLACKLIST ->
                    p.openInventory(new HubTemplateBlacklistMenu(plugin, templateKey, 0).getInventory());
            case SLOT_COLOR_MODE -> plugin.templatePolicies().toggleColorMode(templateKey);
            case SLOT_SHIFT_HIDE -> plugin.templatePolicies().toggleShiftHidesNameTag(templateKey);
            case SLOT_TAB_VISIBLE -> plugin.templatePolicies().toggleTabVisible(templateKey);
            case SLOT_ALLIES_TOGGLE -> plugin.templatePolicies().toggleAlliesEnabled(templateKey);
            case SLOT_ALLY_MAX -> {
                var cur = plugin.templatePolicies().configFor(templateKey);
                int next = right ? cur.maxTeamsPerAlliance() - 1 : cur.maxTeamsPerAlliance() + 1;
                plugin.templatePolicies().setMaxTeamsPerAlliance(templateKey, Math.max(2, next));
            }
            case SLOT_FRIENDLY_FIRE -> plugin.templatePolicies().toggleFriendlyFireEnabled(templateKey);
            case SLOT_TEAM_FRIENDLY_FIRE -> plugin.templatePolicies().toggleTeamFriendlyFireEnabled(templateKey);
            case SLOT_TP_EDITOR -> {
                p.closeInventory();
                HubIronLegacySupport.ilAdmin().ifPresentOrElse(
                        a -> a.teleportToEditor(p, templateKey),
                        () -> p.sendMessage(Component.text(
                                "Iron Legacy is not loaded — editor unavailable.", NamedTextColor.RED)));
                return;
            }
            case SLOT_TEAM_SIZE -> {
                HubIronLegacySupport.ilAdmin().ifPresent(a -> {
                    var row = a.listTemplates().stream()
                            .filter(r -> r.key().equalsIgnoreCase(templateKey)).findFirst().orElse(null);
                    if (row == null) return;
                    int next = right ? row.teamSizeMax() - 1 : row.teamSizeMax() + 1;
                    a.setTeamSizeMax(templateKey, Math.max(1, next));
                });
            }
            case SLOT_MIN_PCT -> {
                int cur = plugin.limboLayout().worldsYaml().minimumPercentage(templateKey);
                int next = right ? cur - 5 : cur + 5;
                next = Math.max(1, Math.min(100, next));
                plugin.limboLayout().worldsYaml().setMinimumPercentage(templateKey, next);
            }
            case SLOT_MAX_PLAYERS -> {
                HubIronLegacySupport.ilAdmin().ifPresent(a -> {
                    var row = a.listTemplates().stream()
                            .filter(r -> r.key().equalsIgnoreCase(templateKey)).findFirst().orElse(null);
                    if (row == null) return;
                    int next = right ? row.maxPlayers() - 1 : row.maxPlayers() + 1;
                    a.setMaxPlayers(templateKey, Math.max(2, next));
                });
            }
            case SLOT_BACK -> {
                p.openInventory(new HubTemplateLandingMenu(plugin, templateKey).getInventory());
                return;
            }
            case SLOT_CLOSE -> {
                p.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        // refresh in-place
        build();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    private static ItemStack btn(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                im.lore(new ArrayList<>(lore));
            }
            it.setItemMeta(im);
        }
        return it;
    }
}
