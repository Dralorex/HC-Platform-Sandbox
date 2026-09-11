package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Centralized rules for crafting, building, and digging.
 *
 * Features:
 * - Modes: whitelist (DEFAULT) or blacklist for build & dig
 * - Toggles: crafting/build/dig enabled
 * - Lists: explicit Material allow/block lists
 * - Categories: loaded from categories.yml; each category may include Bukkit tags (e.g. "tag:LOGS")
 *               and/or explicit material names. Only BLOCK materials are used.
 *
 * Public API kept compatible with your HGCommand:
 *  - summaryLines(), setCraftingEnabled(), setBuildEnabled(), setDigEnabled(), setBuildMode(),
 *    setDigMode(), addBuild(), delBuild(), addDig(), delDig(), addBuildAll(), delBuildAll(),
 *    addDigAll(), delDigAll(), getBuildList(), getDigList(), knownCategories(), resolveCategory()
 */
public class BuildRuleManager {

    private final HungerGamesPlugin plugin;

    private boolean craftingEnabled;

    private boolean dispenserInteractionEnabled;
    
    private boolean buildEnabled;
    private String  buildMode; // "whitelist" | "blacklist"
    private final Set<Material> buildList = new TreeSet<>(Comparator.comparing(Enum::name));

    private boolean digEnabled;
    private String  digMode; // "whitelist" | "blacklist"
    private final Set<Material> digList = new TreeSet<>(Comparator.comparing(Enum::name));

    /** categories.yml → lowercase name -> CategoryDef */
    private final Map<String, CategoryDef> categories = new TreeMap<>();

    public BuildRuleManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        load();           // config.yml
        loadCategories(); // categories.yml
    }

    /* ----------------- Load / Save ----------------- */

    public void load() {
        // Defaults if not present
        if (!plugin.getConfig().isConfigurationSection("rules")) {
            plugin.getConfig().set("rules.crafting.enabled", true);

            plugin.getConfig().set("rules.build.enabled", true);
            plugin.getConfig().set("rules.build.mode", "whitelist");
            plugin.getConfig().set("rules.build.list", new ArrayList<>());

            plugin.getConfig().set("rules.dig.enabled", true);
            plugin.getConfig().set("rules.dig.mode", "whitelist");
            plugin.getConfig().set("rules.dig.list", new ArrayList<>());
            plugin.saveConfig();
        }

        var rules = plugin.getConfig().getConfigurationSection("rules");
        this.craftingEnabled = rules.getConfigurationSection("crafting").getBoolean("enabled", true);

        var b = rules.getConfigurationSection("build");
        this.buildEnabled = b.getBoolean("enabled", true);
        this.buildMode = normalizeMode(b.getString("mode", "whitelist"));
        buildList.clear();
        for (String s : b.getStringList("list")) {
            Material m = safeMat(s);
            if (m != null && m.isBlock()) buildList.add(m);
        }

        var d = rules.getConfigurationSection("dig");
        var dispenser = rules.getConfigurationSection("dispenserInteraction");
        this.dispenserInteractionEnabled =
        dispenser == null || dispenser.getBoolean("enabled", true);
        this.digEnabled = d.getBoolean("enabled", true);
        this.digMode = normalizeMode(d.getString("mode", "whitelist"));
        digList.clear();
        for (String s : d.getStringList("list")) {
            Material m = safeMat(s);
            if (m != null && m.isBlock()) digList.add(m);
        }
    }

    public void reloadAll() {
        plugin.reloadConfig();
        load();
        loadCategories();
    }

    private String normalizeMode(String raw) {
        if (raw == null) return "whitelist";
        raw = raw.trim().toLowerCase(Locale.ROOT);
        return raw.equals("blacklist") ? "blacklist" : "whitelist";
    }

    private void saveLists() {
        plugin.getConfig().set("rules.crafting.enabled", craftingEnabled);

        plugin.getConfig().set("rules.build.enabled", buildEnabled);
        plugin.getConfig().set("rules.build.mode", buildMode);
        plugin.getConfig().set("rules.build.list", buildList.stream().map(Enum::name).sorted().toList());
        plugin.getConfig().set("rules.dispenserInteraction.enabled",dispenserInteractionEnabled);    
        plugin.getConfig().set("rules.dig.enabled", digEnabled);
        plugin.getConfig().set("rules.dig.mode", digMode);
        plugin.getConfig().set("rules.dig.list", digList.stream().map(Enum::name).sorted().toList());

        plugin.saveConfig();
    }

    /* ----------------- Public API (used by commands + listener) ----------------- */

    public List<String> summaryLines() {
        List<String> out = new ArrayList<>();
        out.add("\u00A76Rules:");
        out.add("\u00A7e  Crafting: " + (craftingEnabled ? "\u00A7aENABLED" : "\u00A7cDISABLED"));
        out.add("\u00A7e  Build: " + (buildEnabled ? "\u00A7aENABLED" : "\u00A7cDISABLED")
                + "\u00A77 (" + buildMode + "), " + buildList.size() + " entries");
        out.add("\u00A7e  Dig:   " + (digEnabled ? "\u00A7aENABLED" : "\u00A7cDISABLED")
                + "\u00A77 (" + digMode + "), " + digList.size() + " entries");

        out.add("\u00A76Categories (" + categories.size() + "): "
                + "\u00A77" + String.join(", ", categories.keySet()));
        out.add("\u00A78Tip: use category:<name> or cat:<name> with /hg build|dig add/remove");
        return out;
    }
    /* Dispensers */
        public void setDispenserInteractionEnabled(boolean enabled) {
        this.dispenserInteractionEnabled = enabled;
        saveLists();
    }
    
    public boolean isDispenserInteractionEnabled() {
        return dispenserInteractionEnabled;
    }
    /* Crafting */
    public void setCraftingEnabled(boolean enabled) { this.craftingEnabled = enabled; saveLists(); }
    public boolean isCraftingEnabled() { return craftingEnabled; }

    /* Build */
    public void setBuildEnabled(boolean enabled) { this.buildEnabled = enabled; saveLists(); }
    public boolean isBuildEnabled() { return buildEnabled; }
    public boolean setBuildMode(String mode) {
        String m = normalizeMode(mode);
        boolean ok = m.equals(mode == null ? "whitelist" : mode.trim().toLowerCase(Locale.ROOT));
        this.buildMode = m; saveLists(); return ok;
    }
    public String  getBuildMode() { return buildMode; }
    public boolean addBuild(Material m) { boolean changed = buildList.add(m); if (changed) saveLists(); return changed; }
    public boolean delBuild(Material m) { boolean changed = buildList.remove(m); if (changed) saveLists(); return changed; }
    public Set<Material> getBuildList() { return Collections.unmodifiableSet(buildList); }
    public int addBuildAll(Collection<Material> mats) { int before = buildList.size(); mats.forEach(this::addBuild); return buildList.size()-before; }
    public int delBuildAll(Collection<Material> mats) { int before = buildList.size(); mats.forEach(this::delBuild); return before - buildList.size(); }

    /* Dig */
    public void setDigEnabled(boolean enabled) { this.digEnabled = enabled; saveLists(); }
    public boolean isDigEnabled() { return digEnabled; }
    public boolean setDigMode(String mode) {
        String m = normalizeMode(mode);
        boolean ok = m.equals(mode == null ? "whitelist" : mode.trim().toLowerCase(Locale.ROOT));
        this.digMode = m; saveLists(); return ok;
    }
    public String  getDigMode() { return digMode; }
    public boolean addDig(Material m) { boolean changed = digList.add(m); if (changed) saveLists(); return changed; }
    public boolean delDig(Material m) { boolean changed = digList.remove(m); if (changed) saveLists(); return changed; }
    public Set<Material> getDigList() { return Collections.unmodifiableSet(digList); }
    public int addDigAll(Collection<Material> mats) { int before = digList.size(); mats.forEach(this::addDig); return digList.size()-before; }
    public int delDigAll(Collection<Material> mats) { int before = digList.size(); mats.forEach(this::delDig); return before - digList.size(); }

    /* === NEW: Listener-facing helpers === */

    /** Can the player place this block material, according to current Build rules? */
    public boolean canPlace(Material m) {
        if (m == null || !m.isBlock()) return false; // only blocks are considered placeable
        if (!buildEnabled) return false;             // global building toggle OFF => block all
        return materialAllowedForMode(m, buildMode, buildList);
    }

    /** Same lists as global rules; {@code mode} is whitelist or blacklist (per-match overlay). */
    public boolean materialAllowedPlace(Material m, String mode) {
        if (m == null || !m.isBlock()) return false;
        return materialAllowedForMode(m, normalizeMode(mode), buildList);
    }

    /** Can the player break this block material, according to current Dig rules? */
    public boolean canBreak(Material m) {
        if (m == null || !m.isBlock()) return false; // only blocks are considered breakable
        if (!digEnabled) return false;               // global digging toggle OFF => block all
        return materialAllowedForMode(m, digMode, digList);
    }

    public boolean materialAllowedBreak(Material m, String mode) {
        if (m == null || !m.isBlock()) return false;
        return materialAllowedForMode(m, normalizeMode(mode), digList);
    }

    private static boolean materialAllowedForMode(Material m, String mode, Set<Material> list) {
        boolean listed = list.contains(m);
        if ("whitelist".equals(mode)) return listed;
        return !listed;
    }

    /* ----------------- Categories: names & resolution ----------------- */

    /** Lowercase category names loaded from categories.yml */
    public Set<String> knownCategories() { return Collections.unmodifiableSet(categories.keySet()); }

    /**
     * Resolve a category name into concrete BLOCK materials.
     * Supports entries:
     *  - "tag:<TAG_NAME>" → expands Bukkit Tag materials
     *  - "MATERIAL_NAME"  → explicit Material
     */
    public Set<Material> resolveCategory(String rawName) {
        if (rawName == null) return Collections.emptySet();
        CategoryDef def = categories.get(rawName.trim().toLowerCase(Locale.ROOT));
        if (def == null) return Collections.emptySet();

        Set<Material> out = new TreeSet<>(Comparator.comparing(Enum::name));
        out.addAll(def.materials); // explicit entries (already vetted for isBlock)

        for (String tagKey : def.tags) {
            try {
                // Reflect public static Tag<Material> fields on org.bukkit.Tag (e.g., LOGS, PLANKS, WOOL…)
                @SuppressWarnings("unchecked")
                Tag<Material> tag = (Tag<Material>) Tag.class.getField(tagKey).get(null);
                for (Material m : tag.getValues()) if (m.isBlock()) out.add(m);
            } catch (Throwable ignored) {
                // Unknown/unsupported tag for this server version → ignore safely.
            }
        }
        // blocks only
        out.removeIf(m -> !m.isBlock());
        return out;
    }

    /* ----------------- Internal: categories.yml loader ----------------- */

    private void loadCategories() {
        categories.clear();

        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists()) dataDir.mkdirs();

        File file = new File(dataDir, "categories.yml");
        if (!file.exists()) {
            // Copy default from resources if you shipped one; safe if missing.
            try { plugin.saveResource("categories.yml", false); }
            catch (IllegalArgumentException ignored) {}
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        if (!yml.isConfigurationSection("categories")) return;

        var sec = yml.getConfigurationSection("categories");
        for (String name : sec.getKeys(false)) {
            List<String> entries = sec.getStringList(name);
            CategoryDef def = new CategoryDef();

            for (String token : entries) {
                if (token == null) continue;
                String t = token.trim();
                if (t.isEmpty()) continue;

                // Tag entry: "tag:LOGS"
                if (t.regionMatches(true, 0, "tag:", 0, 4)) {
                    String tagKey = t.substring(4).trim().toUpperCase(Locale.ROOT);
                    if (!tagKey.isEmpty()) def.tags.add(tagKey);
                    continue;
                }

                // Explicit material
                Material m = safeMat(t);
                if (m != null && m.isBlock()) def.materials.add(m);
            }

            categories.put(name.toLowerCase(Locale.ROOT), def);
        }
    }

    /* ----------------- Helpers ----------------- */

    private Material safeMat(String s) {
        if (s == null) return null;
        try { return Material.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ignore) { return null; }
    }

    private static class CategoryDef {
        final Set<String> tags = new HashSet<>();
        final Set<Material> materials = new HashSet<>();
    }
}
