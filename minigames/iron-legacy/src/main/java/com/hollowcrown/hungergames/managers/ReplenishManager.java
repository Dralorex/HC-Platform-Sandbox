package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.WorldEnvoyStorage;
import com.hollowcrown.hungergames.util.Yaml;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ReplenishManager {
    public record BlockCoord(String worldName, int x, int y, int z) {}

    private final HungerGamesPlugin plugin;
    private final ConcurrentHashMap<String, List<BlockCoord>> coordCache = new ConcurrentHashMap<>();

    private BukkitRunnable scheduler;
    private int tierIndex;
    private int repeatCount;

    private volatile String matchTemplateKey;
    private volatile World matchWorld;

    public ReplenishManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void setMatchContext(String templateKey, World world) {
        this.matchTemplateKey = templateKey;
        this.matchWorld = world;
    }

    public void clearMatchContext() {
        this.matchTemplateKey = null;
        this.matchWorld = null;
    }

    /**
     * Match-driven schedule: first refill after {@code schedule.firstRefillDelaySeconds} (default 20),
     * then every {@code schedule.intervalMinutes} (default 30). Uses {@link #setMatchContext}.
     */
    public void startSchedule() {
        stopSchedule();
        if (matchTemplateKey == null || matchWorld == null) {
            plugin.getLogger().fine("Replenish schedule not started: missing match context.");
            return;
        }
        startScheduleInternal(matchTemplateKey, matchWorld);
    }

    /** OP testing: timer for the template matching the player's current world. */
    public void startScheduleFromAdmin(Player p) {
        stopSchedule();
        Optional<String> tk = plugin.templates().resolveTemplateKeyForWorld(p.getWorld().getName());
        if (tk.isEmpty()) {
            p.sendMessage(net.kyori.adventure.text.Component.text(
                    "Stand in a registered template or match world.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        startScheduleInternal(tk.get(), p.getWorld());
        p.sendMessage(net.kyori.adventure.text.Component.text(
                "Replenish schedule started for this map.", net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    private void startScheduleInternal(String templateKey, World world) {
        WorldEnvoyStorage.ensureReplenish(plugin, templateKey);
        FileConfiguration y = Yaml.loadOrCreate(plugin, WorldEnvoyStorage.replenishPath(templateKey));
        long intervalMin = Math.max(1, y.getLong("schedule.intervalMinutes", 30));
        int repeatsPerTier = Math.max(1, y.getInt("schedule.repeatsPerTier", 1));
        long firstDelayTicks = Math.max(0L, y.getLong("schedule.firstRefillDelaySeconds", 20)) * 20L;
        long periodTicks = intervalMin * 60L * 20L;

        tierIndex = 0;
        repeatCount = 0;
        List<String> tiers = List.of("T1", "T2", "T3", "T4");

        scheduler = new BukkitRunnable() {
            @Override public void run() {
                String tier = tiers.get(Math.min(tierIndex, tiers.size() - 1));
                replenishTier(tier, templateKey, world);
                repeatCount++;
                if (repeatCount >= repeatsPerTier) {
                    repeatCount = 0;
                    tierIndex = Math.min(tierIndex + 1, tiers.size() - 1);
                }
            }
        };
        scheduler.runTaskTimer(plugin, firstDelayTicks, periodTicks);
    }

    public void stopSchedule() {
        if (scheduler != null) {
            scheduler.cancel();
            scheduler = null;
        }
    }

    private List<BlockCoord> loadCoords(String templateKey) {
        WorldEnvoyStorage.ensureReplenish(plugin, templateKey);
        FileConfiguration y = Yaml.loadOrCreate(plugin, WorldEnvoyStorage.replenishPath(templateKey));
        List<BlockCoord> out = new ArrayList<>();
        List<Map<String, Object>> arr = (List<Map<String, Object>>) y.get("locations");
        if (arr == null) return out;
        for (Map<String, Object> m : arr) try {
            String worldName = Objects.toString(m.get("world"), "world");
            double xd = Double.parseDouble(Objects.toString(m.get("x")));
            double yd = Double.parseDouble(Objects.toString(m.get("y")));
            double zd = Double.parseDouble(Objects.toString(m.get("z")));
            out.add(new BlockCoord(worldName, (int) Math.floor(xd), (int) Math.floor(yd), (int) Math.floor(zd)));
        } catch (Exception ignored) {}
        return out;
    }

    private List<BlockCoord> coords(String templateKey) {
        return coordCache.computeIfAbsent(templateKey, this::loadCoords);
    }

    private void persistLocations(String templateKey, List<BlockCoord> list) {
        WorldEnvoyStorage.ensureReplenish(plugin, templateKey);
        FileConfiguration y = Yaml.loadOrCreate(plugin, WorldEnvoyStorage.replenishPath(templateKey));
        List<Map<String, Object>> arr = new ArrayList<>();
        for (BlockCoord c : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", c.worldName());
            m.put("x", c.x() + 0.5);
            m.put("y", (double) c.y());
            m.put("z", c.z() + 0.5);
            arr.add(m);
        }
        y.set("locations", arr);
        Yaml.save(plugin, WorldEnvoyStorage.replenishPath(templateKey), y);
    }

    public void invalidateTemplate(String templateKey) {
        coordCache.remove(templateKey);
    }

    public void add(Location l, String templateKey) {
        BlockCoord c = new BlockCoord(
                Objects.requireNonNull(l.getWorld()).getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ());
        List<BlockCoord> list = new ArrayList<>(coords(templateKey));
        list.removeIf(o -> o.worldName().equals(c.worldName()) && o.x() == c.x() && o.y() == c.y() && o.z() == c.z());
        list.add(c);
        persistLocations(templateKey, list);
        coordCache.put(templateKey, list);
    }

    public boolean removeNearest(Location ref, String templateKey) {
        List<BlockCoord> list = new ArrayList<>(coords(templateKey));
        BlockCoord best = null;
        double bd = Double.MAX_VALUE;
        World rw = ref.getWorld();
        if (rw == null) return false;
        for (BlockCoord c : list) {
            if (!c.worldName().equalsIgnoreCase(rw.getName())) continue;
            double dx = (c.x() + 0.5) - ref.getX();
            double dy = (c.y() + 0.5) - ref.getY();
            double dz = (c.z() + 0.5) - ref.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        if (best != null) {
            final BlockCoord rm = best;
            list.removeIf(o -> o.worldName().equals(rm.worldName()) && o.x() == rm.x() && o.y() == rm.y() && o.z() == rm.z());
            persistLocations(templateKey, list);
            coordCache.put(templateKey, list);
            return true;
        }
        return false;
    }

    /** Locations for GUI listing (resolved worlds). */
    public List<Location> list(String templateKey) {
        List<Location> out = new ArrayList<>();
        for (BlockCoord c : coords(templateKey)) {
            World w = Bukkit.getWorld(c.worldName());
            if (w == null) continue;
            out.add(new Location(w, c.x() + 0.5, c.y() + 0.5, c.z() + 0.5));
        }
        return out;
    }

    public World resolveFillWorld(String templateKey, World preferred) {
        if (matchTemplateKey != null && matchTemplateKey.equals(templateKey) && matchWorld != null) {
            return matchWorld;
        }
        if (preferred != null) {
            Optional<String> tk = plugin.templates().resolveTemplateKeyForWorld(preferred.getName());
            if (tk.isPresent() && tk.get().equalsIgnoreCase(templateKey)) return preferred;
        }
        for (BlockCoord c : coords(templateKey)) {
            World w = Bukkit.getWorld(c.worldName());
            if (w != null) return w;
        }
        return null;
    }

    /** Legacy command without world context — uses active match world if template matches, else template's saved world. */
    public void replenishTier(String tier) {
        String tk = matchTemplateKey;
        World w = matchWorld;
        if (tk == null || w == null) {
            plugin.getLogger().warning("replenishTier called without an active match; use per-template triggers from a template world.");
            return;
        }
        replenishTier(tier, tk, w);
    }

    public void replenishTier(String tier, String templateKey, World preferredWorld) {
        World w = resolveFillWorld(templateKey, preferredWorld);
        if (w == null) return;
        List<Location> targets = new ArrayList<>();
        for (BlockCoord c : coords(templateKey)) {
            targets.add(new Location(w, c.x(), c.y(), c.z()).add(0.5, 0.5, 0.5));
        }
        if (targets.isEmpty()) return;
        new BukkitRunnable() {
            int idx = 0;

            @Override public void run() {
                int perTick = 8;
                for (int i = 0; i < perTick && idx < targets.size(); i++, idx++) {
                    fillChest(targets.get(idx), tier, templateKey);
                }
                if (idx >= targets.size()) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void fillChest(Location l, String tier, String templateKey) {
        Block b = l.getBlock();
        if (!(b.getState() instanceof Chest chest)) return;
        WorldEnvoyStorage.ensureReplenish(plugin, templateKey);
        FileConfiguration y = Yaml.loadOrCreate(plugin, WorldEnvoyStorage.replenishPath(templateKey));
        String base = "tiers." + tier;
        int minItems = y.getInt(base + ".numItems.min", 2);
        int maxItems = y.getInt(base + ".numItems.max", 4);
        List<Map<?, ?>> items = y.getMapList(base + ".items");
        if (items.isEmpty()) return;
        int num = ThreadLocalRandom.current().nextInt(minItems, Math.max(minItems, maxItems) + 1);
        Collections.shuffle(items);
        Inventory inv = chest.getBlockInventory();
        inv.clear();

        List<ItemStack> toPlace = new ArrayList<>();
        for (int i = 0; i < num && i < items.size(); i++) {
            Map<?, ?> m = items.get(i);
            try {
                Material mat = Material.valueOf(Objects.toString(m.get("material"), "STONE"));
                int minS = Integer.parseInt(Objects.toString(m.get("minStack"), "1"));
                int maxS = Integer.parseInt(Objects.toString(m.get("maxStack"), "1"));
                int totalAmt = Math.max(1, ThreadLocalRandom.current().nextInt(minS, maxS + 1));

                int numStacks = ThreadLocalRandom.current().nextInt(1, Math.min(5, totalAmt / 2 + 1));
                numStacks = Math.max(1, numStacks);
                int remaining = totalAmt;
                for (int s = 0; s < numStacks && remaining > 0; s++) {
                    int stackSize = s == numStacks - 1 ? remaining : Math.max(1, ThreadLocalRandom.current().nextInt(1, remaining / (numStacks - s) + 1));
                    stackSize = Math.min(stackSize, remaining);
                    if (stackSize > 0 && stackSize <= mat.getMaxStackSize()) {
                        toPlace.add(new ItemStack(mat, stackSize));
                        remaining -= stackSize;
                    }
                }
            } catch (Exception ignored) {}
        }

        for (ItemStack it : toPlace) {
            int slot = inv.firstEmpty();
            if (slot == -1) break;
            inv.setItem(slot, it);
        }
    }

    /** IL replenish chests: unbreakable during edit (saved world) or active match instance for this template. */
    public boolean isProtectedReplenishChest(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Optional<String> tkOpt = plugin.templates().resolveTemplateKeyForWorld(loc.getWorld().getName());
        String tk = tkOpt.orElse(null);
        if (tk == null) return false;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        String worldName = loc.getWorld().getName();
        for (BlockCoord c : coords(tk)) {
            if (c.x() != bx || c.y() != by || c.z() != bz) continue;
            if (c.worldName().equals(worldName)) return true;
            GameManager gm = plugin.game();
            if (gm != null
                    && tk.equalsIgnoreCase(gm.getActiveMatchTemplateKey())
                    && gm.getCurrentWorld() != null
                    && gm.getCurrentWorld().equals(loc.getWorld())) {
                return true;
            }
        }
        return false;
    }
}
