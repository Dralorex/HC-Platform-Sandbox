package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.managers.WorldGameSettings;
import com.hollowcrown.hungergames.managers.WorldSettingsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

/** Per-template overrides stored in world-game-settings.yml. */
public class GameWorldSettingsMenu extends MenuView {
    private final String templateKey;
    private final Inventory inv;

    public GameWorldSettingsMenu(String templateKey) {
        this.templateKey = templateKey.toLowerCase(java.util.Locale.ROOT);
        this.inv = Bukkit.createInventory(this, 54, Component.text("Game: " + templateKey, NamedTextColor.GOLD));
        build();
    }

    private ItemStack button(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            if (lore != null) {
                im.lore(lore.stream().map(LegacyComponentSerializer.legacySection()::deserialize).collect(Collectors.toList()));
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private String onOff(boolean v) {
        return v ? "§aON" : "§cOFF";
    }

    private void build() {
        inv.clear();
        HungerGamesPlugin plugin = HungerGamesPlugin.get();
        WorldGameSettings ws = plugin.worldSettings().resolve(templateKey);

        inv.setItem(10, button(Material.COMPASS, "§aAuto-assign teams", List.of(
                "§7Fill partial teams then create new ones at match start.",
                "§7Current: " + onOff(ws.autoAssignTeams()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(11, button(Material.CLOCK, "§eMovement countdown (5..1)", List.of(
                "§7Pre-teleport §cDO NOT MOVE§7 countdown.",
                "§7Current: " + onOff(ws.movementCountdownEnabled()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(12, button(Material.FEATHER, "§dFree movement (no freeze)", List.of(
                "§7After teleport: skip leash + BEGIN countdown.",
                "§7Parkour / survival-style servers.",
                "§7Current: " + onOff(ws.freeMovementAfterTeleport()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(13, button(Material.BLAZE_ROD, "§dLaunch on move (freeze)", List.of(
                "§7Current: " + onOff(ws.launchOnMove()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(14, button(Material.IRON_SWORD, "§cDeath cannon sound", List.of(
                "§7Current: " + onOff(ws.playCannonOnDeath()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(15, button(Material.PAPER, "§fSuppress death messages", List.of(
                "§7Current: " + onOff(ws.suppressDeathMessages()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(16, button(Material.SHIELD, "§bKeep Adventure on start", List.of(
                "§7Current: " + onOff(ws.keepAdventureOnStart()),
                "",
                "§eClick: toggle"
        )));

        inv.setItem(19, button(Material.REPEATER, "§6Freeze countdown (BEGIN)", List.of(
                "§7Seconds before BEGIN after leash.",
                "§7Current: §f" + ws.freezeCountdownSeconds(),
                "",
                "§eLeft: +5  §eRight: -5"
        )));
        inv.setItem(20, button(Material.PLAYER_HEAD, "§eInclude OPs in game", List.of(
                "§7Counted OP join without intent flow.",
                "§7Current: " + onOff(ws.includeOpsInGame()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(21, button(Material.SUNFLOWER, "§eDay/night cycle", List.of(
                "§7doDaylightCycle gamerule on match world.",
                "§7Current: " + onOff(ws.doDaylightCycle()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(22, button(Material.WATER_BUCKET, "§3Weather cycle", List.of(
                "§7doWeatherCycle gamerule.",
                "§7Current: " + onOff(ws.doWeatherCycle()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(23, button(Material.CRAFTING_TABLE, "§6Crafting", List.of(
                "§7Counted players in this match only.",
                "§7Current: " + onOff(ws.craftingEnabled()),
                "",
                "§eClick: toggle"
        )));

        inv.setItem(28, button(Material.BRICKS, "§6Build enabled", List.of(
                "§7Uses global block lists + mode below.",
                "§7Current: " + onOff(ws.buildEnabled()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(29, button(Material.IRON_PICKAXE, "§6Dig enabled", List.of(
                "§7Current: " + onOff(ws.digEnabled()),
                "",
                "§eClick: toggle"
        )));
        inv.setItem(30, button(Material.COMPARATOR, "§bBuild list mode", List.of(
                "§7Current: §f" + ws.buildMode(),
                "",
                "§eClick: toggle whitelist/blacklist"
        )));
        inv.setItem(31, button(Material.REPEATER, "§bDig list mode", List.of(
                "§7Current: §f" + ws.digMode(),
                "",
                "§eClick: toggle whitelist/blacklist"
        )));

        inv.setItem(32, button(Material.BARRIER, "§9Border start size", List.of(
                "§7Current: §f" + String.format("%.0f", ws.borderStartSize()),
                "",
                "§eLeft: +50  §eRight: -50"
        )));
        inv.setItem(33, button(Material.BARRIER, "§9Border end size", List.of(
                "§7Current: §f" + String.format("%.0f", ws.borderEndSize()),
                "",
                "§eLeft: +25  §eRight: -25"
        )));
        inv.setItem(34, button(Material.BARRIER, "§9Shrink delay (sec)", List.of(
                "§7Current: §f" + ws.borderShrinkDelaySeconds(),
                "",
                "§eLeft: +30  §eRight: -30"
        )));
        inv.setItem(35, button(Material.BARRIER, "§9Shrink duration (sec)", List.of(
                "§7Current: §f" + ws.borderShrinkDurationSeconds(),
                "",
                "§eLeft: +60  §eRight: -60"
        )));
        inv.setItem(37, button(Material.PISTON, "§cFreeze penalty launch height", List.of(
                "§7Current: §f" + ws.freezePenaltyHeight(),
                "",
                "§eLeft: +10  §eRight: -10"
        )));

        inv.setItem(49, button(Material.BARRIER, "§cBack", List.of()));
    }

    private WorldSettingsManager wm() {
        return HungerGamesPlugin.get().worldSettings();
    }

    private void log(Player p, String action) {
        HungerGamesPlugin.get().history().log(p.getName(), "admin_gui_game_world", templateKey + ":" + action);
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.isOp()) return;
        ClickType click = e.getClick();
        int slot = e.getRawSlot();
        WorldGameSettings ws = HungerGamesPlugin.get().worldSettings().resolve(templateKey);

        switch (slot) {
            case 10 -> { wm().setBoolean(templateKey, "autoAssignTeams", !ws.autoAssignTeams()); log(p, "autoAssignTeams"); }
            case 11 -> { wm().setBoolean(templateKey, "movementCountdownEnabled", !ws.movementCountdownEnabled()); log(p, "movementCountdown"); }
            case 12 -> { wm().setBoolean(templateKey, "freeMovementAfterTeleport", !ws.freeMovementAfterTeleport()); log(p, "freeMovement"); }
            case 13 -> { wm().setBoolean(templateKey, "launchOnMove", !ws.launchOnMove()); log(p, "launchOnMove"); }
            case 14 -> { wm().setBoolean(templateKey, "playCannonOnDeath", !ws.playCannonOnDeath()); log(p, "cannon"); }
            case 15 -> { wm().setBoolean(templateKey, "suppressDeathMessages", !ws.suppressDeathMessages()); log(p, "suppressDeath"); }
            case 16 -> { wm().setBoolean(templateKey, "keepAdventureOnStart", !ws.keepAdventureOnStart()); log(p, "adventure"); }
            case 19 -> {
                int cur = ws.freezeCountdownSeconds();
                int next = click.isRightClick() ? Math.max(5, cur - 5) : Math.min(180, cur + 5);
                wm().setInt(templateKey, "freezeCountdownSeconds", next);
                log(p, "freezeSec=" + next);
            }
            case 20 -> { wm().setBoolean(templateKey, "includeOpsInGame", !ws.includeOpsInGame()); log(p, "includeOps"); }
            case 21 -> { wm().setBoolean(templateKey, "doDaylightCycle", !ws.doDaylightCycle()); log(p, "daylight"); }
            case 22 -> { wm().setBoolean(templateKey, "doWeatherCycle", !ws.doWeatherCycle()); log(p, "weather"); }
            case 23 -> { wm().setBoolean(templateKey, "craftingEnabled", !ws.craftingEnabled()); log(p, "craft"); }
            case 28 -> { wm().setBoolean(templateKey, "buildEnabled", !ws.buildEnabled()); log(p, "build"); }
            case 29 -> { wm().setBoolean(templateKey, "digEnabled", !ws.digEnabled()); log(p, "dig"); }
            case 30 -> {
                String next = "whitelist".equalsIgnoreCase(ws.buildMode()) ? "blacklist" : "whitelist";
                wm().setString(templateKey, "buildMode", next);
                log(p, "buildMode=" + next);
            }
            case 31 -> {
                String next = "whitelist".equalsIgnoreCase(ws.digMode()) ? "blacklist" : "whitelist";
                wm().setString(templateKey, "digMode", next);
                log(p, "digMode=" + next);
            }
            case 32 -> {
                double cur = ws.borderStartSize();
                double next = click.isRightClick() ? Math.max(16.0, cur - 50.0) : Math.min(6000.0, cur + 50.0);
                wm().setDouble(templateKey, "borderStartSize", next);
                log(p, "borderStart=" + next);
            }
            case 33 -> {
                double cur = ws.borderEndSize();
                double next = click.isRightClick() ? Math.max(16.0, cur - 25.0) : Math.min(3000.0, cur + 25.0);
                wm().setDouble(templateKey, "borderEndSize", next);
                log(p, "borderEnd=" + next);
            }
            case 34 -> {
                long cur = ws.borderShrinkDelaySeconds();
                long next = click.isRightClick() ? Math.max(0L, cur - 30L) : Math.min(7200L, cur + 30L);
                wm().setLong(templateKey, "borderShrinkDelaySeconds", next);
                log(p, "shrinkDelay=" + next);
            }
            case 35 -> {
                long cur = ws.borderShrinkDurationSeconds();
                long next = click.isRightClick() ? Math.max(1L, cur - 60L) : Math.min(7200L, cur + 60L);
                wm().setLong(templateKey, "borderShrinkDurationSeconds", next);
                log(p, "shrinkDur=" + next);
            }
            case 37 -> {
                int cur = ws.freezePenaltyHeight();
                int next = click.isRightClick() ? Math.max(10, cur - 10) : Math.min(500, cur + 10);
                wm().setInt(templateKey, "freezePenaltyHeight", next);
                log(p, "penaltyH=" + next);
            }
            case 49 -> {
                p.openInventory(new GameWorldsListMenu(0).getInventory());
                return;
            }
            default -> { return; }
        }
        build();
        p.updateInventory();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
