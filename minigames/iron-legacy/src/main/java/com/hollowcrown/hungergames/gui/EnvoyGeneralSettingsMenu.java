package com.hollowcrown.hungergames.gui;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.WorldEnvoyStorage;
import com.hollowcrown.hungergames.util.Yaml;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

/**
 * In-game knobs for scoped {@code envoys.yml} and {@code replenish.yml}.
 * Left-click: increase / enable · Right-click: decrease / disable · Shift: larger step (where applicable).
 */
public class EnvoyGeneralSettingsMenu extends MenuView {
    private final HungerGamesPlugin plugin = HungerGamesPlugin.get();
    private final String templateKey;
    private final Inventory inv;

    public EnvoyGeneralSettingsMenu(String templateKey) {
        this.templateKey = templateKey;
        this.inv = Bukkit.createInventory(this, 54, Component.text(
                "Envoy settings · " + templateKey, NamedTextColor.AQUA));
        build();
    }

    private FileConfiguration envY() {
        return plugin.envoys().yamlForTemplate(templateKey);
    }

    private FileConfiguration replY() {
        WorldEnvoyStorage.ensureBaseReplenish(plugin);
        return Yaml.loadOrCreate(plugin, WorldEnvoyStorage.BASE_REPLENISH);
    }

    private void saveRepl(FileConfiguration y) {
        Yaml.save(plugin, WorldEnvoyStorage.BASE_REPLENISH, y);
    }

    private void saveEnv(FileConfiguration y) {
        plugin.envoys().saveYamlTemplate(templateKey, y);
    }

    private ItemStack btn(Material mat, String title, List<String> loreLines) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(LegacyComponentSerializer.legacySection().deserialize(title));
            im.lore(loreLines.stream()
                    .map(LegacyComponentSerializer.legacySection()::deserialize)
                    .collect(Collectors.toList()));
            it.setItemMeta(im);
        }
        return it;
    }

    private void build() {
        inv.clear();
        FileConfiguration e = envY();
        FileConfiguration r = replY();

        long spawnIv = e.getLong("timing.spawnIntervalSeconds", 180);
        int wMin = e.getInt("wave.minPerWave", 3);
        int wMax = e.getInt("wave.maxPerWave", 10);
        double fall = e.getDouble("timing.fallSpeedBlocksPerTick", 0.08);
        boolean chute = e.getBoolean("visual.parachuteEnabled", true);
        double spawnH = e.getDouble("timing.spawnHeightAboveTarget", 50);
        long batch = e.getLong("timing.spawnBatchSize", 10);
        boolean forager = e.getBoolean("wave.randomForager.enabled", false);
        boolean tiersEn = e.getBoolean("tiers.enabled", false);

        long replMin = r.getLong("schedule.intervalMinutes", 30);
        long replFirst = r.getLong("schedule.firstRefillDelaySeconds", 20);
        int replRep = r.getInt("schedule.repeatsPerTier", 1);

        inv.setItem(10, btn(Material.CLOCK, "§eSpawn interval",
                List.of("§7timing.spawnIntervalSeconds", "§f" + spawnIv + " §7sec",
                        "§8L/R ±15 · Shift ±60")));
        inv.setItem(11, btn(Material.HOPPER, "§eMin / wave",
                List.of("§7wave.minPerWave", "§f" + wMin, "§8L/R ±1")));
        inv.setItem(12, btn(Material.COMPARATOR, "§eMax / wave",
                List.of("§7wave.maxPerWave", "§f" + wMax, "§8L/R ±1")));
        inv.setItem(13, btn(Material.FEATHER, "§eFall speed",
                List.of("§7timing.fallSpeedBlocksPerTick", String.format("§f%.2f", fall),
                        "§8L/R ±0.02")));
        inv.setItem(14, btn(Material.WHITE_BANNER, "§eParachute",
                List.of("§7visual.parachuteEnabled", chute ? "§aON" : "§cOFF", "§8Click toggle")));
        inv.setItem(15, btn(Material.LADDER, "§eSpawn height",
                List.of("§7timing.spawnHeightAboveTarget", "§f" + spawnH + " §7blocks",
                        "§8L/R ±5 · Shift ±25")));
        inv.setItem(16, btn(Material.PISTON, "§eSpawn batch",
                List.of("§7timing.spawnBatchSize", "§f" + batch, "§8L/R ±1 · Shift ±5")));

        inv.setItem(19, btn(Material.CHEST, "§aReplenish interval",
                List.of("§7schedule.intervalMinutes", "§f" + replMin + " §7min",
                        "§8L/R ±1 · Shift ±5")));
        inv.setItem(20, btn(Material.HONEYCOMB, "§aFirst refill delay",
                List.of("§7schedule.firstRefillDelaySeconds", "§f" + replFirst + " §7sec",
                        "§8L/R ±5")));
        inv.setItem(21, btn(Material.KNOWLEDGE_BOOK, "§aRepeats / tier",
                List.of("§7schedule.repeatsPerTier", "§f" + replRep, "§8L/R ±1")));

        inv.setItem(23, btn(Material.ENDER_EYE, "§dRandom forager",
                List.of("§7wave.randomForager.enabled", forager ? "§aON" : "§cOFF", "§8Click toggle")));
        inv.setItem(24, btn(Material.NETHER_STAR, "§dTiered packages",
                List.of("§7tiers.enabled", tiersEn ? "§aON" : "§cOFF", "§8Click toggle")));

        inv.setItem(4, btn(Material.PAPER, "§bMap: §f" + templateKey,
                List.of("§7Files:", "§8world-envoys/" + templateKey + "/envoys.yml",
                        "§8plugins/HCIronLegacy/replenish.yml (loot + schedule)",
                        "§8world-envoys/" + templateKey + "/replenish-locations.yml")));

        inv.setItem(49, btn(Material.BARRIER, "§cBack", List.of("§7Envoys admin")));
    }

    private long clamp(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 49) {
            p.openInventory(new EnvoyAdminMenu(templateKey).getInventory());
            return;
        }
        ClickType ct = e.getClick();
        boolean inc = ct.isRightClick();
        boolean shift = ct.isShiftClick();

        FileConfiguration ey = envY();
        FileConfiguration ry = replY();

        switch (slot) {
            case 10 -> {
                long cur = ey.getLong("timing.spawnIntervalSeconds", 180);
                long step = shift ? 60 : 15;
                ey.set("timing.spawnIntervalSeconds", clamp(cur + (inc ? -step : step), 30, 3600));
                saveEnv(ey);
            }
            case 11 -> {
                int cur = ey.getInt("wave.minPerWave", 3);
                ey.set("wave.minPerWave", (int) clamp(cur + (inc ? -1 : 1), 1, 64));
                saveEnv(ey);
            }
            case 12 -> {
                int cur = ey.getInt("wave.maxPerWave", 10);
                ey.set("wave.maxPerWave", (int) clamp(cur + (inc ? -1 : 1), 1, 128));
                saveEnv(ey);
            }
            case 13 -> {
                double cur = ey.getDouble("timing.fallSpeedBlocksPerTick", 0.08);
                double step = 0.02;
                ey.set("timing.fallSpeedBlocksPerTick", clamp(cur + (inc ? -step : step), 0.02, 0.5));
                saveEnv(ey);
            }
            case 14 -> {
                ey.set("visual.parachuteEnabled", !ey.getBoolean("visual.parachuteEnabled", true));
                saveEnv(ey);
            }
            case 15 -> {
                double cur = ey.getDouble("timing.spawnHeightAboveTarget", 50);
                double step = shift ? 25 : 5;
                ey.set("timing.spawnHeightAboveTarget", clamp(cur + (inc ? -step : step), 5, 320));
                saveEnv(ey);
            }
            case 16 -> {
                long cur = ey.getLong("timing.spawnBatchSize", 10);
                long step = shift ? 5 : 1;
                ey.set("timing.spawnBatchSize", clamp(cur + (inc ? -step : step), 1, 128));
                saveEnv(ey);
            }
            case 19 -> {
                long cur = ry.getLong("schedule.intervalMinutes", 30);
                long step = shift ? 5 : 1;
                ry.set("schedule.intervalMinutes", clamp(cur + (inc ? -step : step), 1, 240));
                saveRepl(ry);
            }
            case 20 -> {
                long cur = ry.getLong("schedule.firstRefillDelaySeconds", 20);
                ry.set("schedule.firstRefillDelaySeconds", clamp(cur + (inc ? -5 : 5), 0, 600));
                saveRepl(ry);
            }
            case 21 -> {
                int cur = ry.getInt("schedule.repeatsPerTier", 1);
                ry.set("schedule.repeatsPerTier", (int) clamp(cur + (inc ? -1 : 1), 1, 20));
                saveRepl(ry);
            }
            case 23 -> {
                ey.set("wave.randomForager.enabled", !ey.getBoolean("wave.randomForager.enabled", false));
                saveEnv(ey);
            }
            case 24 -> {
                ey.set("tiers.enabled", !ey.getBoolean("tiers.enabled", false));
                saveEnv(ey);
            }
            default -> {
                return;
            }
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        p.openInventory(new EnvoyGeneralSettingsMenu(templateKey).getInventory());
    }

    @Override public Inventory getInventory() {
        return inv;
    }
}
