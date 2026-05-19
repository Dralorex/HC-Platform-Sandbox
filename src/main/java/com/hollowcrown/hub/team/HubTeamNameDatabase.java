package com.hollowcrown.hub.team;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Hub-owned pool of display names for auto-created teams. */
public final class HubTeamNameDatabase {

    private static final String FILE = "team-name-database.yml";

    private final HollowCrownHubPlugin plugin;
    private FileConfiguration cfg;
    private final List<String> names = new ArrayList<>();

    public HubTeamNameDatabase(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cfg = HubYaml.loadOrCreate(plugin, FILE);
        names.clear();
        List<String> raw = cfg.getStringList("names");
        for (String s : raw) {
            String n = normalize(s);
            if (!n.isEmpty()) names.add(n);
        }
        dedupeSort();
        cfg.set("names", new ArrayList<>(names));
        HubYaml.save(plugin, FILE, cfg);
    }

    public void save() {
        cfg.set("names", new ArrayList<>(names));
        HubYaml.save(plugin, FILE, cfg);
    }

    private void dedupeSort() {
        List<String> out = new ArrayList<>();
        for (String n : names) {
            boolean dup = false;
            for (String e : out) {
                if (e.equalsIgnoreCase(n)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(n);
        }
        names.clear();
        names.addAll(out);
        names.sort(String.CASE_INSENSITIVE_ORDER);
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim();
    }

    public List<String> namesAlphabetical() {
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    public boolean tryAddName(String raw, HubTeamService teams) {
        String n = normalize(raw);
        if (n.isEmpty()) return false;
        for (String x : names) {
            if (x.equalsIgnoreCase(n)) return false;
        }
        if (teams.nameTaken(n)) return false;
        names.add(n);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        save();
        return true;
    }

    public String takeUnusedNameForNewTeam(HubTeamService teams) {
        for (String n : names) {
            if (!teams.nameTaken(n)) return n;
        }
        return null;
    }

    public String generateSequentialTeamName(HubTeamService teams) {
        int i = 1;
        while (true) {
            String candidate = "Team " + i;
            if (!teams.nameTaken(candidate)) return candidate;
            i++;
            if (i > 99999) return "Team " + System.currentTimeMillis();
        }
    }
}

