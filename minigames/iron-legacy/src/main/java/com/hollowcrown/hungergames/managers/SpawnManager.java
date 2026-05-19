package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SpawnManager {
    private final HungerGamesPlugin plugin;
    private final List<Location> pads = new ArrayList<>();

    public SpawnManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        pads.clear();
        var list = plugin.getConfig().getStringList("spawns.pads");
        for (String s : list) {
            Location l = deserializeLocation(s);
            if (l != null) pads.add(l);
        }
    }

    public void save() {
        List<String> out = pads.stream().map(this::serializeLocation).collect(Collectors.toList());
        plugin.getConfig().set("spawns.pads", out);
        plugin.saveConfig();
    }

    public List<Location> getSpawnPads() { return pads; }

    public void add(Location loc) {
        pads.add(loc.clone());
        save();
    }

    public void clear() {
        pads.clear();
        save();
    }

    public int count() { return pads.size(); }

    /** Remove the pad closest to a point (useful for tweaking circles) */
    public Location removeNearest(Location from) {
        if (pads.isEmpty()) return null;
        int best = -1; double bestD = Double.MAX_VALUE;
        for (int i = 0; i < pads.size(); i++) {
            double d = pads.get(i).distanceSquared(from);
            if (d < bestD) { bestD = d; best = i; }
        }
        if (best >= 0) {
            Location removed = pads.remove(best);
            save();
            return removed;
        }
        return null;
    }

    /** Generate a ring of pads around either the world spawn or the player's location */
    public List<Location> addRing(Location center, int count, double radius, boolean useWorldSpawn) {
        Location base = useWorldSpawn ? center.getWorld().getSpawnLocation() : center.clone();
        List<Location> added = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI) * i / count;
            double x = base.getX() + radius * Math.cos(angle);
            double z = base.getZ() + radius * Math.sin(angle);
            int y = center.getWorld().getHighestBlockYAt((int)Math.round(x), (int)Math.round(z)) + 1;
            Location spot = new Location(center.getWorld(), Math.round(x) + 0.5, y, Math.round(z) + 0.5, 0f, 0f);
            pads.add(spot);
            added.add(spot);
        }
        save();
        return added;
    }

    private String serializeLocation(Location l) {
        return l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw() + "," + l.getPitch();
    }

    private Location deserializeLocation(String s) {
        try {
            String[] p = s.split(",");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to parse location: " + s);
            return null;
        }
    }
}
