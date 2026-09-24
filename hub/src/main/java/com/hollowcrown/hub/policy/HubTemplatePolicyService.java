package com.hollowcrown.hub.policy;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Per-template overrides for plugin policy + match settings: color mode, sneak hide, TAB visibility,
 * allies on/off + max alliance size, per-template plugin allocation list.
 *
 * <p>When a player is in a match world, this service's per-template plugin policy takes precedence over
 * the global Match scope in {@link HubPluginPolicyService}; see {@link HubPluginPolicyEnforcer}.</p>
 */
public final class HubTemplatePolicyService {

    private static final String FILE = "template-policies.yml";

    public enum ColorMode {
        TEAM,
        LEGACY
    }

    /**
     * Snapshot of one template's configuration. Mutate via the service methods so saves and
     * downstream listeners get triggered consistently.
     */
    public record TemplateConfig(
            String templateKey,
            ColorMode colorMode,
            boolean shiftHidesNameTag,
            boolean tabVisible,
            boolean alliesEnabled,
            boolean friendlyFireEnabled,
            boolean teamFriendlyFireEnabled,
            int maxTeamsPerAlliance,
            HubPluginPolicyService.Mode pluginPolicyMode,
            LinkedHashSet<String> pluginPolicyList) {

        public TemplateConfig {
            templateKey = templateKey == null ? "" : templateKey.trim().toLowerCase(Locale.ROOT);
            colorMode = colorMode == null ? ColorMode.TEAM : colorMode;
            maxTeamsPerAlliance = Math.max(2, maxTeamsPerAlliance);
            pluginPolicyMode = pluginPolicyMode == null ? HubPluginPolicyService.Mode.BLACKLIST : pluginPolicyMode;
            pluginPolicyList = pluginPolicyList == null ? new LinkedHashSet<>() : new LinkedHashSet<>(pluginPolicyList);
        }

        public TemplateConfig withColorMode(ColorMode mode) {
            return new TemplateConfig(templateKey, mode, shiftHidesNameTag, tabVisible, alliesEnabled, friendlyFireEnabled, teamFriendlyFireEnabled,
                    maxTeamsPerAlliance, pluginPolicyMode, pluginPolicyList);
        }

        public TemplateConfig withShiftHide(boolean v) {
            return new TemplateConfig(templateKey, colorMode, v, tabVisible, alliesEnabled, friendlyFireEnabled, teamFriendlyFireEnabled,
                    maxTeamsPerAlliance, pluginPolicyMode, pluginPolicyList);
        }

        public TemplateConfig withTabVisible(boolean v) {
            return new TemplateConfig(templateKey, colorMode, shiftHidesNameTag, v, alliesEnabled, friendlyFireEnabled, teamFriendlyFireEnabled,
                    maxTeamsPerAlliance, pluginPolicyMode, pluginPolicyList);
        }

        public TemplateConfig withAlliesEnabled(boolean v) {
            return new TemplateConfig(templateKey, colorMode, shiftHidesNameTag, tabVisible, v, friendlyFireEnabled, teamFriendlyFireEnabled,
                    maxTeamsPerAlliance, pluginPolicyMode, pluginPolicyList);
        }

        public TemplateConfig withFriendlyFireEnabled(boolean v) {
            return new TemplateConfig(templateKey, colorMode, shiftHidesNameTag, tabVisible, alliesEnabled, v, teamFriendlyFireEnabled,
                    maxTeamsPerAlliance, pluginPolicyMode, pluginPolicyList);
        }

        public TemplateConfig withTeamFriendlyFireEnabled(boolean v) {
            return new TemplateConfig(templateKey, colorMode, shiftHidesNameTag, tabVisible, alliesEnabled, friendlyFireEnabled, v,
                    maxTeamsPerAlliance, pluginPolicyMode, pluginPolicyList);
        }

        public TemplateConfig withMaxTeamsPerAlliance(int n) {
            return new TemplateConfig(templateKey, colorMode, shiftHidesNameTag, tabVisible, alliesEnabled, friendlyFireEnabled, teamFriendlyFireEnabled,
                    n, pluginPolicyMode, pluginPolicyList);
        }

        public TemplateConfig withPolicyMode(HubPluginPolicyService.Mode mode) {
            return new TemplateConfig(templateKey, colorMode, shiftHidesNameTag, tabVisible, alliesEnabled, friendlyFireEnabled, teamFriendlyFireEnabled,
                    maxTeamsPerAlliance, mode, pluginPolicyList);
        }

        public TemplateConfig withPolicyList(Set<String> list) {
            return new TemplateConfig(templateKey, colorMode, shiftHidesNameTag, tabVisible, alliesEnabled, friendlyFireEnabled, teamFriendlyFireEnabled,
                    maxTeamsPerAlliance, pluginPolicyMode, new LinkedHashSet<>(list));
        }
    }

    public static TemplateConfig defaultsFor(String templateKey) {
        return new TemplateConfig(
                templateKey,
                ColorMode.TEAM,
                /* shiftHidesNameTag */ true,
                /* tabVisible       */ false,
                /* alliesEnabled    */ false,
                /* friendlyFireEnabled */ true,
                /* teamFriendlyFireEnabled */ true,
                /* maxTeamsPerAlliance */ 2,
                HubPluginPolicyService.Mode.BLACKLIST,
                new LinkedHashSet<>());
    }

    private final HollowCrownHubPlugin plugin;
    private final File file;
    private final Map<String, TemplateConfig> templates = new ConcurrentHashMap<>();
    /** Template keys edited in a policy GUI but not yet flushed to disk. */
    private final Set<String> deferredDirty = ConcurrentHashMap.newKeySet();

    public HubTemplatePolicyService(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE);
        load();
    }

    public TemplateConfig configFor(String templateKey) {
        String norm = normalize(templateKey);
        if (norm == null) {
            return defaultsFor("");
        }
        return templates.computeIfAbsent(norm, k -> {
            TemplateConfig def = defaultsFor(k);
            save();
            return def;
        });
    }

    /**
     * @return true if a config row was removed
     */
    public boolean removeTemplate(String templateKey) {
        String norm = normalize(templateKey);
        if (norm == null) {
            return false;
        }
        deferredDirty.remove(norm);
        if (templates.remove(norm) == null) {
            return false;
        }
        save();
        return true;
    }

    /**
     * @return true if a config row was added (template was new), false if it already existed.
     */
    public boolean registerTemplateIfAbsent(String templateKey) {
        String norm = normalize(templateKey);
        if (norm == null) return false;
        if (templates.containsKey(norm)) return false;
        templates.put(norm, defaultsFor(norm));
        save();
        return true;
    }

    public Map<String, TemplateConfig> snapshot() {
        return new TreeMap<>(templates);
    }

    public void setColorMode(String templateKey, ColorMode mode) {
        update(templateKey, c -> c.withColorMode(mode));
    }

    public void toggleColorMode(String templateKey) {
        TemplateConfig cur = configFor(templateKey);
        setColorMode(templateKey, cur.colorMode() == ColorMode.TEAM ? ColorMode.LEGACY : ColorMode.TEAM);
    }

    public void setShiftHidesNameTag(String templateKey, boolean value) {
        update(templateKey, c -> c.withShiftHide(value));
    }

    public void toggleShiftHidesNameTag(String templateKey) {
        update(templateKey, c -> c.withShiftHide(!c.shiftHidesNameTag()));
    }

    public void setTabVisible(String templateKey, boolean value) {
        update(templateKey, c -> c.withTabVisible(value));
    }

    public void toggleTabVisible(String templateKey) {
        update(templateKey, c -> c.withTabVisible(!c.tabVisible()));
    }

    public void setAlliesEnabled(String templateKey, boolean value) {
        update(templateKey, c -> c.withAlliesEnabled(value));
    }

    public void toggleAlliesEnabled(String templateKey) {
        update(templateKey, c -> c.withAlliesEnabled(!c.alliesEnabled()));
    }

    public void setFriendlyFireEnabled(String templateKey, boolean value) {
        update(templateKey, c -> c.withFriendlyFireEnabled(value));
    }

    public void toggleFriendlyFireEnabled(String templateKey) {
        update(templateKey, c -> c.withFriendlyFireEnabled(!c.friendlyFireEnabled()));
    }

    public void setTeamFriendlyFireEnabled(String templateKey, boolean value) {
        update(templateKey, c -> c.withTeamFriendlyFireEnabled(value));
    }

    public void toggleTeamFriendlyFireEnabled(String templateKey) {
        update(templateKey, c -> c.withTeamFriendlyFireEnabled(!c.teamFriendlyFireEnabled()));
    }

    public void setMaxTeamsPerAlliance(String templateKey, int n) {
        update(templateKey, c -> c.withMaxTeamsPerAlliance(Math.max(2, n)));
    }

    public void toggleMode(String templateKey) {
        update(templateKey, c -> c.withPolicyMode(
                c.pluginPolicyMode() == HubPluginPolicyService.Mode.WHITELIST
                        ? HubPluginPolicyService.Mode.BLACKLIST
                        : HubPluginPolicyService.Mode.WHITELIST));
    }

    public void addPluginToList(String templateKey, String pluginName) {
        String norm = normalize(pluginName);
        if (norm == null) return;
        update(templateKey, c -> {
            if (c.pluginPolicyList().contains(norm)) return c;
            LinkedHashSet<String> next = new LinkedHashSet<>(c.pluginPolicyList());
            next.add(norm);
            return c.withPolicyList(next);
        });
    }

    public void removePluginFromList(String templateKey, String pluginName) {
        String norm = normalize(pluginName);
        if (norm == null) return;
        update(templateKey, c -> {
            if (!c.pluginPolicyList().contains(norm)) return c;
            LinkedHashSet<String> next = new LinkedHashSet<>(c.pluginPolicyList());
            next.remove(norm);
            return c.withPolicyList(next);
        });
    }

    /** Marks a template as dirty; persisted when the operator closes the policy GUI. */
    public void markDeferredDirty(String templateKey) {
        String norm = normalize(templateKey);
        if (norm != null) {
            deferredDirty.add(norm);
        }
    }

    /**
     * @return true if anything was written
     */
    public boolean flushDeferred(String templateKey) {
        String norm = normalize(templateKey);
        if (norm == null || !deferredDirty.remove(norm)) {
            return false;
        }
        save();
        return true;
    }

    private void update(String templateKey, java.util.function.UnaryOperator<TemplateConfig> mut) {
        String norm = normalize(templateKey);
        if (norm == null) return;
        TemplateConfig cur = configFor(norm);
        TemplateConfig next = mut.apply(cur);
        if (next == cur) return;
        templates.put(norm, next);
        deferredDirty.add(norm);
    }

    private void updateAndSave(String templateKey, java.util.function.UnaryOperator<TemplateConfig> mut) {
        String norm = normalize(templateKey);
        if (norm == null) return;
        TemplateConfig cur = configFor(norm);
        TemplateConfig next = mut.apply(cur);
        if (next == cur) return;
        templates.put(norm, next);
        save();
    }

    private static String normalize(String raw) {
        if (raw == null) return null;
        String n = raw.trim().toLowerCase(Locale.ROOT);
        return n.isEmpty() ? null : n;
    }

    private void load() {
        if (!file.exists()) {
            save();
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("templates");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection row = sec.getConfigurationSection(key);
            if (row == null) continue;
            try {
                ColorMode cm = ColorMode.valueOf(row.getString("colorMode", "TEAM").toUpperCase(Locale.ROOT));
                boolean shiftHide = row.getBoolean("shiftHidesNameTag", true);
                boolean tabVisible = row.getBoolean("tabVisible", false);
                boolean alliesEnabled = row.getBoolean("allies.enabled", false);
                boolean friendlyFireEnabled = row.getBoolean("allies.friendlyFireEnabled", true);
                boolean teamFriendlyFireEnabled = row.getBoolean("allies.teamFriendlyFireEnabled", true);
                int maxAlly = Math.max(2, row.getInt("allies.maxTeamsPerAlliance", 2));
                ConfigurationSection policy = row.getConfigurationSection("pluginPolicy");
                HubPluginPolicyService.Mode mode = HubPluginPolicyService.Mode.BLACKLIST;
                LinkedHashSet<String> list = new LinkedHashSet<>();
                if (policy != null) {
                    try {
                        mode = HubPluginPolicyService.Mode.valueOf(
                                policy.getString("mode", "BLACKLIST").toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                    }
                    for (String s : policy.getStringList("plugins")) {
                        String n = normalize(s);
                        if (n != null) list.add(n);
                    }
                }
                templates.put(key.toLowerCase(Locale.ROOT),
                        new TemplateConfig(key, cm, shiftHide, tabVisible, alliesEnabled, friendlyFireEnabled, teamFriendlyFireEnabled,
                                maxAlly, mode, list));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Skipping malformed template-policy row '" + key + "'", ex);
            }
        }
    }

    private void save() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, TemplateConfig> e : new TreeMap<>(templates).entrySet()) {
            String base = "templates." + e.getKey();
            TemplateConfig c = e.getValue();
            y.set(base + ".colorMode", c.colorMode().name());
            y.set(base + ".shiftHidesNameTag", c.shiftHidesNameTag());
            y.set(base + ".tabVisible", c.tabVisible());
            y.set(base + ".allies.enabled", c.alliesEnabled());
            y.set(base + ".allies.friendlyFireEnabled", c.friendlyFireEnabled());
            y.set(base + ".allies.teamFriendlyFireEnabled", c.teamFriendlyFireEnabled());
            y.set(base + ".allies.maxTeamsPerAlliance", c.maxTeamsPerAlliance());
            y.set(base + ".pluginPolicy.mode", c.pluginPolicyMode().name());
            y.set(base + ".pluginPolicy.plugins", List.copyOf(c.pluginPolicyList()));
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save template-policies.yml", ex);
        }
    }
}
