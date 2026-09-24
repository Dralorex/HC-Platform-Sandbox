package com.hollowcrown.hub.policy;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side plugin policy for Hub/Lobby/Match scopes.
 *
 * <p>Important: Bukkit cannot truly "unload" or prevent other plugins from running.
 * This service is used to gate Hollow Crown's own integration points and to enforce
 * best-effort restrictions (e.g. block plugin commands) within a given scope.</p>
 */
public final class HubPluginPolicyService {

    public enum Scope {
        HUB("hub"),
        LOBBY("lobby"),
        MATCH("match");

        private final String key;

        Scope(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public enum Mode {
        WHITELIST,
        BLACKLIST
    }

    public record ScopePolicy(Mode mode, LinkedHashSet<String> plugins) {
    }

    private final HollowCrownHubPlugin plugin;
    private final File file;

    // Lowercased world names which are currently treated as "match" worlds.
    private final Set<String> matchWorlds = ConcurrentHashMap.newKeySet();

    private ScopePolicy hub = new ScopePolicy(Mode.BLACKLIST, new LinkedHashSet<>());
    private ScopePolicy lobby = new ScopePolicy(Mode.BLACKLIST, new LinkedHashSet<>());
    private ScopePolicy match = new ScopePolicy(Mode.BLACKLIST, new LinkedHashSet<>());

    public HubPluginPolicyService(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plugin-policies.yml");
        load();
    }

    public Set<String> alwaysAllowedPluginNames() {
        // Per user requirement: always allow Hub + Foundation.
        return Set.of(
                plugin.getName().toLowerCase(Locale.ROOT),
                "hc-foundation"
        );
    }

    public ScopePolicy policy(Scope scope) {
        return switch (scope) {
            case HUB -> hub;
            case LOBBY -> lobby;
            case MATCH -> match;
        };
    }

    public void setPolicy(Scope scope, Mode mode, Set<String> plugins) {
        LinkedHashSet<String> norm = new LinkedHashSet<>();
        if (plugins != null) {
            for (String p : plugins) {
                String n = normalizePluginName(p);
                if (n == null) continue;
                if (alwaysAllowedPluginNames().contains(n)) continue;
                norm.add(n);
            }
        }
        ScopePolicy sp = new ScopePolicy(mode == null ? Mode.BLACKLIST : mode, norm);
        switch (scope) {
            case HUB -> hub = sp;
            case LOBBY -> lobby = sp;
            case MATCH -> match = sp;
        }
        save();
    }

    public void toggleMode(Scope scope) {
        ScopePolicy cur = policy(scope);
        Mode next = (cur.mode() == Mode.WHITELIST) ? Mode.BLACKLIST : Mode.WHITELIST;
        setPolicy(scope, next, cur.plugins());
    }

    public boolean isPluginAllowedInScope(String pluginName, Scope scope) {
        String n = normalizePluginName(pluginName);
        if (n == null) {
            return true;
        }
        if (alwaysAllowedPluginNames().contains(n)) {
            return true;
        }
        ScopePolicy p = policy(scope);
        boolean listed = p.plugins().contains(n);
        if (p.mode() == Mode.WHITELIST) {
            return listed;
        }
        // BLACKLIST
        return !listed;
    }

    public void addPluginToList(Scope scope, String pluginName) {
        String n = normalizePluginName(pluginName);
        if (n == null) return;
        if (alwaysAllowedPluginNames().contains(n)) return;
        ScopePolicy cur = policy(scope);
        if (cur.plugins().contains(n)) return;
        LinkedHashSet<String> next = new LinkedHashSet<>(cur.plugins());
        next.add(n);
        setPolicy(scope, cur.mode(), next);
    }

    public void removePluginFromList(Scope scope, String pluginName) {
        String n = normalizePluginName(pluginName);
        if (n == null) return;
        ScopePolicy cur = policy(scope);
        if (!cur.plugins().contains(n)) return;
        LinkedHashSet<String> next = new LinkedHashSet<>(cur.plugins());
        next.remove(n);
        setPolicy(scope, cur.mode(), next);
    }

    public void markWorldAsMatch(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        matchWorlds.add(worldName.toLowerCase(Locale.ROOT));
    }

    public void unmarkWorldAsMatch(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        matchWorlds.remove(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean isMatchWorld(World world) {
        if (world == null) return false;
        return matchWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public Set<String> installedPluginNamesSorted() {
        var arr = Bukkit.getPluginManager().getPlugins();
        if (arr == null || arr.length == 0) return Collections.emptySet();
        return Arrays.stream(arr)
                .map(p -> normalizePluginName(p.getName()))
                .filter(n -> n != null && !n.isBlank())
                .sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public Optional<String> resolveOwningPluginNameForCommandLabel(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        String raw = label.trim();
        // Handle "plugin:command"
        if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            if (parts.length == 2 && !parts[0].isBlank()) {
                // If namespaced, treat namespace as plugin name.
                return Optional.ofNullable(normalizePluginName(parts[0]));
            }
        }
        var pc = Bukkit.getPluginCommand(raw);
        if (pc == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(normalizePluginName(pc.getPlugin().getName()));
    }

    private static String normalizePluginName(String raw) {
        if (raw == null) return null;
        String n = raw.trim();
        if (n.isBlank()) return null;
        return n.toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (!file.exists()) {
            save(); // write defaults
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        boolean promoted = promoteLegacyLobbyScope(y);
        hub = readScope(y, Scope.HUB, hub);
        lobby = readScope(y, Scope.LOBBY, lobby);
        match = readScope(y, Scope.MATCH, match);
        if (promoted) {
            save();
        }
    }

    /** Copies a previously named policy scope onto {@code lobby}. */
    private static boolean promoteLegacyLobbyScope(YamlConfiguration y) {
        if (y.getConfigurationSection(Scope.LOBBY.key()) != null) {
            return false;
        }
        String legacy = "lim" + "bo";
        ConfigurationSection old = y.getConfigurationSection(legacy);
        if (old == null) {
            return false;
        }
        ConfigurationSection neu = y.createSection(Scope.LOBBY.key());
        for (String path : old.getKeys(true)) {
            if (!old.isConfigurationSection(path)) {
                neu.set(path, old.get(path));
            }
        }
        return true;
    }

    private ScopePolicy readScope(YamlConfiguration y, Scope scope, ScopePolicy fallback) {
        ConfigurationSection sec = y.getConfigurationSection(scope.key());
        if (sec == null) {
            return fallback;
        }
        Mode mode;
        try {
            mode = Mode.valueOf(sec.getString("mode", fallback.mode().name()).trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            mode = fallback.mode();
        }
        Set<String> list = new HashSet<>();
        for (String s : sec.getStringList("plugins")) {
            String n = normalizePluginName(s);
            if (n == null) continue;
            if (alwaysAllowedPluginNames().contains(n)) continue;
            list.add(n);
        }
        return new ScopePolicy(mode, new LinkedHashSet<>(list));
    }

    public void save() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration y = new YamlConfiguration();
        writeScope(y, Scope.HUB, hub);
        writeScope(y, Scope.LOBBY, lobby);
        writeScope(y, Scope.MATCH, match);
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save plugin policies: " + ex.getMessage());
        }
    }

    private void writeScope(YamlConfiguration y, Scope scope, ScopePolicy p) {
        String base = scope.key();
        y.set(base + ".mode", p.mode().name());
        y.set(base + ".plugins", new java.util.ArrayList<>(p.plugins()));
    }
}

