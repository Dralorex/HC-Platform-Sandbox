package com.hollowcrown.hub.world;

import com.hollowcrown.api.integration.HubPlayerMenus;
import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.api.integration.IronLegacyHubBridge;
import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import io.papermc.paper.event.entity.EntityMoveEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Hub-world protections, locked utility hotbar, opt-in PvP via held sword, and pads.
 * Join/party GUIs are Hub-owned ({@link HubPlayerMenus}); Iron Legacy only registers {@link IronLegacyHubBridge} for hub PvP kill credit.
 */
public final class HubWorldManager implements HubWorldApi, Listener {

    private final HollowCrownHubPlugin plugin;
    private final HubWorldYaml hubWorldYaml;
    private final NamespacedKey lockedItemKey;
    private final NamespacedKey pvpPadItemKey;

    private final Set<UUID> pvpActive = new HashSet<>();
    private final Map<UUID, Integer> activateCountdown = new HashMap<>();
    private final Map<UUID, Integer> deactivateCountdown = new HashMap<>();
    private final Set<UUID> buildMode = new HashSet<>();
    private final Map<UUID, InventorySnapshot> buildModeSnapshots = new HashMap<>();
    private final Set<UUID> pendingDeactivateOnDeath = new HashSet<>();
    private final Map<UUID, Long> pvpInvulnerableUntil = new HashMap<>();
    private final Map<UUID, Location> pvpRespawnPads = new HashMap<>();
    private final Map<UUID, Long> lastPvpPlateActivationMs = new HashMap<>();
    private final Map<UUID, Long> lastPvpPadInactivePromptAt = new HashMap<>();
    /** Players who were inside the hub PvP zone when PvP was last active (for respawn routing). */
    private final Set<UUID> pvpZoneParticipants = new HashSet<>();
    /** Previous tick: was the player inside the PvP zone (for re-entry detection). */
    private final Map<UUID, Boolean> pvpZoneWasInside = new HashMap<>();
    private final Map<UUID, Long> suppressGameLobbyGuiOpenUntil = new HashMap<>();

    private Location pvpZonePos1;
    private Location pvpZonePos2;
    private Location pvpZoneSpawn;

    public HubWorldManager(HollowCrownHubPlugin plugin, HubWorldYaml hubWorldYaml) {
        this.plugin = plugin;
        this.hubWorldYaml = hubWorldYaml;
        this.lockedItemKey = new NamespacedKey(plugin, "lobby_locked_item");
        this.pvpPadItemKey = new NamespacedKey(plugin, "pvp_pad_item");
        loadPvpZone();
        startPvpStateTask();
        startInvulnerabilityParticleTask();
    }

    private FileConfiguration hw() {
        return hubWorldYaml.config();
    }

    // ── PvP zone persistence ──────────────────────────────────────────

    private void loadPvpZone() {
        FileConfiguration c = hw();
        pvpZonePos1 = readLoc(c, "pvpZone.pos1");
        pvpZonePos2 = readLoc(c, "pvpZone.pos2");
        pvpZoneSpawn = readLocFull(c, "pvpZoneSpawn");
    }

    private static Location readLoc(FileConfiguration c, String path) {
        if (!c.contains(path + ".x")) return null;
        String wn = c.getString(path + ".world", "");
        org.bukkit.World w = wn.isBlank() ? null : org.bukkit.Bukkit.getWorld(wn);
        if (w == null) return null;
        return new Location(w, c.getDouble(path + ".x"), c.getDouble(path + ".y"), c.getDouble(path + ".z"));
    }

    private static Location readLocFull(FileConfiguration c, String path) {
        Location loc = readLoc(c, path);
        if (loc == null) return null;
        loc.setYaw((float) c.getDouble(path + ".yaw", 0.0));
        loc.setPitch((float) c.getDouble(path + ".pitch", 0.0));
        return loc;
    }

    private static void writeLoc(FileConfiguration c, String path, Location loc) {
        c.set(path + ".world", loc.getWorld().getName());
        c.set(path + ".x", loc.getX());
        c.set(path + ".y", loc.getY());
        c.set(path + ".z", loc.getZ());
    }

    public void savePvpZone(Location pos1, Location pos2) {
        FileConfiguration c = hw();
        writeLoc(c, "pvpZone.pos1", pos1);
        writeLoc(c, "pvpZone.pos2", pos2);
        hubWorldYaml.save();
        pvpZonePos1 = pos1.clone();
        pvpZonePos2 = pos2.clone();
    }

    public boolean savePvpZoneSpawn(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        FileConfiguration c = hw();
        writeLoc(c, "pvpZoneSpawn", loc);
        c.set("pvpZoneSpawn.yaw", loc.getYaw());
        c.set("pvpZoneSpawn.pitch", loc.getPitch());
        hubWorldYaml.save();
        pvpZoneSpawn = loc.clone();
        return true;
    }

    @Override
    public boolean isInPvpZone(Player p) {
        return isLocationInPvpZone(p.getLocation());
    }

    public boolean isLocationInPvpZone(Location loc) {
        if (pvpZonePos1 == null || pvpZonePos2 == null || loc == null || loc.getWorld() == null) return false;
        if (pvpZonePos1.getWorld() == null || pvpZonePos2.getWorld() == null) return false;
        if (!loc.getWorld().getName().equalsIgnoreCase(pvpZonePos1.getWorld().getName())) return false;
        double minX = Math.min(pvpZonePos1.getX(), pvpZonePos2.getX());
        double maxX = Math.max(pvpZonePos1.getX(), pvpZonePos2.getX()) + 1;
        double minY = Math.min(pvpZonePos1.getY(), pvpZonePos2.getY());
        double maxY = Math.max(pvpZonePos1.getY(), pvpZonePos2.getY()) + 1;
        double minZ = Math.min(pvpZonePos1.getZ(), pvpZonePos2.getZ());
        double maxZ = Math.max(pvpZonePos1.getZ(), pvpZonePos2.getZ()) + 1;
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    /**
     * Respawn while hub PvP is active: pad (if chosen) &gt; zone spawn (if they fought in-zone) &gt; hub spawn.
     * Outside the PvP zone, hub spawn is always used.
     */
    private Location resolvePvpRespawnLocation(UUID id) {
        if (!pvpActive.contains(id)) {
            return hubCenterLocation().orElse(null);
        }
        Location pad = pvpRespawnPads.get(id);
        if (pad != null) {
            return pad;
        }
        if (pvpZoneParticipants.contains(id) && pvpZoneSpawn != null) {
            return pvpZoneSpawn;
        }
        return hubCenterLocation().orElse(null);
    }

    @Override
    public NamespacedKey lockedLobbyUtilityItemKey() {
        return lockedItemKey;
    }

    private Optional<IronLegacyHubBridge> ironLegacy() {
        RegisteredServiceProvider<IronLegacyHubBridge> reg =
                Bukkit.getServicesManager().getRegistration(IronLegacyHubBridge.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }

    @Override
    public Optional<Location> hubCenterLocation() {
        return plugin.platform().getHubSpawn().flatMap(this::toLocation);
    }

    private Optional<Location> toLocation(HubSpawn spawn) {
        World w = Bukkit.getWorld(spawn.worldUuid());
        if (w == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(w, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()));
    }

    @Override
    public boolean isLocationInHubProtection(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Optional<LobbyCircleZone> circle = lobbyCircleZone();
        if (circle.isPresent()) {
            LobbyCircleZone z = circle.get();
            if (!Objects.equals(z.center().getWorld(), loc.getWorld())) {
                return false;
            }
            if (loc.getBlockY() < z.minY() || loc.getBlockY() > z.maxY()) {
                return false;
            }
            double dx = loc.getX() - (z.center().getBlockX() + 0.5);
            double dz = loc.getZ() - (z.center().getBlockZ() + 0.5);
            return (dx * dx + dz * dz) <= (z.radius() * z.radius());
        }
        Optional<Location> centerOpt = hubCenterLocation();
        if (centerOpt.isEmpty()) return false;
        Location center = centerOpt.get();
        if (!Objects.equals(center.getWorld(), loc.getWorld())) return false;
        double size = protectionRadiusBlocks();
        return Math.abs(loc.getX() - center.getX()) <= size && Math.abs(loc.getZ() - center.getZ()) <= size;
    }

    /** Saved cylindrical lobby protection, if configured via the circle wand. */
    public Optional<LobbyCircleZone> lobbyCircleZone() {
        var c = hw();
        if (!c.getBoolean("protection.circle.enabled", false)) {
            return Optional.empty();
        }
        String uuidStr = c.getString("protection.circle.worldUuid", "");
        if (uuidStr == null || uuidStr.isBlank() || !c.isSet("protection.circle.centerX")) {
            return Optional.empty();
        }
        try {
            World w = Bukkit.getWorld(UUID.fromString(uuidStr.trim()));
            if (w == null) {
                return Optional.empty();
            }
            double cx = c.getDouble("protection.circle.centerX");
            double cy = c.getDouble("protection.circle.centerY");
            double cz = c.getDouble("protection.circle.centerZ");
            double radius = Math.max(1.0, c.getDouble("protection.circle.radius", 64.0));
            int minY = c.getInt("protection.circle.minY", w.getMinHeight());
            int maxY = c.getInt("protection.circle.maxY", w.getMaxHeight() - 1);
            Location center = new Location(w, cx, cy, cz);
            return Optional.of(new LobbyCircleZone(center, radius, minY, maxY));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Persists a cylindrical lobby zone from center + edge (XZ = radius, Y delta = height).
     */
    public boolean saveLobbyCircleZone(Location center, Location edge) {
        if (center == null || edge == null || center.getWorld() == null || edge.getWorld() == null) {
            return false;
        }
        if (!center.getWorld().equals(edge.getWorld())) {
            return false;
        }
        double radius = HubLobbyCircleWandListener.horizontalRadius(center, edge);
        int minY = Math.min(center.getBlockY(), edge.getBlockY());
        int maxY = Math.max(center.getBlockY(), edge.getBlockY());
        if (minY == maxY) {
            minY = Math.max(center.getWorld().getMinHeight(), minY - 8);
            maxY = Math.min(center.getWorld().getMaxHeight() - 1, maxY + 24);
        }
        var c = hw();
        c.set("protection.circle.enabled", true);
        c.set("protection.circle.worldUuid", center.getWorld().getUID().toString());
        c.set("protection.circle.centerX", center.getBlockX() + 0.5);
        c.set("protection.circle.centerY", center.getBlockY());
        c.set("protection.circle.centerZ", center.getBlockZ() + 0.5);
        c.set("protection.circle.radius", radius);
        c.set("protection.circle.minY", minY);
        c.set("protection.circle.maxY", maxY);
        // Keep square radius in sync for APIs that still read radiusBlocks as a size hint.
        setProtectionRadiusBlocks(radius);
        hubWorldYaml.save();
        HubLog.info(plugin, "action=lobby_circle_save",
                "world=" + center.getWorld().getName(),
                "radius=" + radius,
                "minY=" + minY,
                "maxY=" + maxY);
        return true;
    }

    public record LobbyCircleZone(Location center, double radius, int minY, int maxY) {
    }

    @Override
    public boolean isInHubProtection(Player player) {
        return player != null && isLocationInHubProtection(player.getLocation());
    }

    @Override
    public boolean isHubBuildMode(Player player) {
        return player != null && buildMode.contains(player.getUniqueId());
    }

    @Override
    public void setHubBuildMode(Player player, boolean on) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        if (on) {
            buildMode.add(id);
            player.setGameMode(GameMode.CREATIVE);
            InventorySnapshot snapshot = buildModeSnapshots.get(id);
            if (snapshot != null) {
                applyInventorySnapshot(player, snapshot);
            }
            return;
        }
        buildModeSnapshots.put(id, captureInventorySnapshot(player));
        buildMode.remove(id);
        player.setGameMode(GameMode.SURVIVAL);
        applyHubUtilityLoadout(player, true, true);
    }

    @Override
    public boolean sendToHubSpawn(Player player) {
        return com.hollowcrown.hub.platform.HubHubRouting.sendToHubWithPartyRouting(plugin, player);
    }

    @Override
    public void applySurvivalHubLoadoutAfterTemplate(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        if (buildMode.contains(id)) {
            player.setGameMode(GameMode.CREATIVE);
            InventorySnapshot snapshot = buildModeSnapshots.get(id);
            if (snapshot != null) {
                applyInventorySnapshot(player, snapshot);
            }
            return;
        }
        player.setGameMode(GameMode.SURVIVAL);
        applyHubUtilityLoadout(player, true, true);
    }

    @Override
    public double protectionRadiusBlocks() {
        return Math.max(1.0, hw().getDouble("protection.radiusBlocks", 64.0));
    }

    @Override
    public void setProtectionRadiusBlocks(double radius) {
        hw().set("protection.radiusBlocks", Math.max(1.0, radius));
        hubWorldYaml.save();
    }

    @Override
    public boolean allowHostileMobSpawnsInHub() {
        return hw().getBoolean("mobSpawns.hostile", false);
    }

    @Override
    public void setAllowHostileMobSpawnsInHub(boolean value) {
        hw().set("mobSpawns.hostile", value);
        hubWorldYaml.save();
    }

    @Override
    public boolean allowPassiveMobSpawnsInHub() {
        return hw().getBoolean("mobSpawns.passive", false);
    }

    @Override
    public void setAllowPassiveMobSpawnsInHub(boolean value) {
        hw().set("mobSpawns.passive", value);
        hubWorldYaml.save();
    }

    @Override
    public boolean isHubPvpModeActive(Player player) {
        return player != null && pvpActive.contains(player.getUniqueId());
    }

    @Override
    public void suppressGameLobbyGuiBriefly(Player player, long durationMs) {
        if (player == null) return;
        suppressGameLobbyGuiOpenUntil.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(0L, durationMs));
    }

    @Override
    public boolean isGameLobbyGuiSuppressed(Player player) {
        if (player == null) return false;
        return System.currentTimeMillis() < suppressGameLobbyGuiOpenUntil.getOrDefault(player.getUniqueId(), 0L);
    }

    @Override
    public void applyHubUtilityLoadout(Player player, boolean wipeInventory, boolean selectCompassHotbar) {
        if (player == null) return;
        if (canModifyHub(player)) {
            return;
        }
        if (wipeInventory) {
            wipeInventory(player);
        }
        normalizeHubUtilitySlots(player);
        if (selectCompassHotbar && isCompassEnabled()) {
            player.getInventory().setHeldItemSlot(getCompassSlotIndex());
        }
    }

    @Override
    public void applyLimboUtilityLoadout(Player player, int hotbarSlotAfterApply) {
        if (player == null) {
            return;
        }
        if (canModifyHub(player)) {
            return;
        }
        var partySpec = readHubPartySpec();
        var teamSpec = readLimboTeamSpec();
        Map<Integer, ItemStack> expected = new HashMap<>();
        expected.put(0, lobbySword());
        if (isCompassEnabled()) {
            expected.put(getCompassSlotIndex(), lobbyCompass());
        }
        expected.put(teamSpec.slotIndex(), utilityItem(teamSpec));
        expected.put(partySpec.slotIndex(), utilityItem(partySpec));
        var leaveSpec = readLimboLeaveSpec();
        expected.put(leaveSpec.slotIndex(), utilityItem(leaveSpec));
        normalizeExpectedLockedHotbar(player, expected);
        int held = Math.max(0, Math.min(8, hotbarSlotAfterApply));
        player.getInventory().setHeldItemSlot(held);
    }

    @Override
    public boolean isLimboTeamUtilityItem(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        if (!isLockedHubItem(stack)) {
            return false;
        }
        var spec = readLimboTeamSpec();
        return player.getInventory().getHeldItemSlot() == spec.slotIndex()
                && stack.getType() == spec.material();
    }

    @Override
    public boolean isLimboLeaveUtilityItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        ItemStack ref = utilityItem(readLimboLeaveSpec());
        return stack.getType() == ref.getType() && isLockedHubItem(stack) && sameDisplayName(stack, ref);
    }

    @Override
    public void givePvpPadItem(Player player) {
        if (player != null) {
            player.getInventory().addItem(pvpPadItemStack());
        }
    }

    private boolean isCompassEnabled() {
        return hw().getBoolean("compassEnabled", true);
    }

    private int getCompassSlotIndex() {
        int oneBased = hw().getInt("compassSlot", 5);
        int clamped = Math.max(1, Math.min(9, oneBased));
        return clamped - 1;
    }

    private Material swordMaterial() {
        String raw = hw().getString("pvpSwordMaterial", "IRON_SWORD");
        try {
            Material m = Material.valueOf(raw.toUpperCase());
            return m.isItem() ? m : Material.IRON_SWORD;
        } catch (Exception ignored) {
            return Material.IRON_SWORD;
        }
    }

    private ItemStack lobbySword() {
        ItemStack sword = new ItemStack(swordMaterial());
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(lockedItemKey, PersistentDataType.BYTE, (byte) 1);
            sword.setItemMeta(meta);
        }
        return sword;
    }

    private ItemStack lobbyCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Game Lobby", NamedTextColor.AQUA));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(lockedItemKey, PersistentDataType.BYTE, (byte) 1);
            compass.setItemMeta(meta);
        }
        return compass;
    }

    private ItemStack pvpPadItemStack() {
        ItemStack plate = new ItemStack(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        ItemMeta meta = plate.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("PVP Pad", NamedTextColor.GOLD));
            meta.lore(java.util.List.of(Component.text("Place to create a PvP respawn pad", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(pvpPadItemKey, PersistentDataType.BYTE, (byte) 1);
            plate.setItemMeta(meta);
        }
        return plate;
    }

    private boolean isPvpPadItem(ItemStack item) {
        if (item == null || item.getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(pvpPadItemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private record UtilityItemSpec(int slotIndex, Material material, String name) {}

    private UtilityItemSpec readHubPartySpec() {
        FileConfiguration c = hw();
        int slot = Math.max(1, Math.min(9, c.getInt("items.hub.party.slot", 9)));
        String materialRaw = c.getString("items.hub.party.material", "NETHER_STAR");
        String name = c.getString("items.hub.party.name", "Party");
        Material material;
        try {
            material = Material.valueOf(materialRaw.toUpperCase());
        } catch (Exception ignored) {
            material = Material.NETHER_STAR;
        }
        return new UtilityItemSpec(slot - 1, material, name);
    }

    private UtilityItemSpec readLimboTeamSpec() {
        FileConfiguration c = hw();
        int slot = Math.max(1, Math.min(9, c.getInt("items.limbo.team.slot", 3)));
        String materialRaw = c.getString("items.limbo.team.material", "NETHER_STAR");
        String name = c.getString("items.limbo.team.name", "Match Team");
        Material material;
        try {
            material = Material.valueOf(materialRaw.toUpperCase());
        } catch (Exception ignored) {
            material = Material.NETHER_STAR;
        }
        return new UtilityItemSpec(slot - 1, material, name);
    }

    private UtilityItemSpec readLimboLeaveSpec() {
        FileConfiguration c = hw();
        int slot = Math.max(1, Math.min(9, c.getInt("items.limbo.leave.slot", 9)));
        String materialRaw = c.getString("items.limbo.leave.material", "BARRIER");
        String name = c.getString("items.limbo.leave.name", "Leave Lobby");
        Material material;
        try {
            material = Material.valueOf(materialRaw.toUpperCase());
        } catch (Exception ignored) {
            material = Material.BARRIER;
        }
        return new UtilityItemSpec(slot - 1, material, name);
    }

    private ItemStack utilityItem(UtilityItemSpec spec) {
        ItemStack item = new ItemStack(spec.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(spec.name(), NamedTextColor.AQUA));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(lockedItemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void givePvpSword(Player p) {
        PlayerInventory inv = p.getInventory();
        boolean hasSword = false;
        for (ItemStack it : inv.getContents()) {
            if (isLockedHubItem(it) && it.getType() == swordMaterial()) {
                hasSword = true;
                break;
            }
        }
        if (!hasSword) {
            inv.setItem(0, lobbySword());
        }
    }

    private void giveLobbyCompass(Player p) {
        if (!isCompassEnabled()) return;
        PlayerInventory inv = p.getInventory();
        int slot = getCompassSlotIndex();
        ItemStack s = inv.getItem(slot);
        if (s == null || s.getType() == Material.AIR || !isLockedHubItem(s) || s.getType() != Material.COMPASS) {
            inv.setItem(slot, lobbyCompass());
        }
    }

    private void ensureHubPartyItem(Player p) {
        var spec = readHubPartySpec();
        PlayerInventory inv = p.getInventory();
        ItemStack current = inv.getItem(spec.slotIndex());
        if (current == null || current.getType() == Material.AIR || !isLockedHubItem(current)) {
            inv.setItem(spec.slotIndex(), utilityItem(spec));
        }
    }

    private void normalizeHubUtilitySlots(Player p) {
        var partySpec = readHubPartySpec();
        Map<Integer, ItemStack> expected = new HashMap<>();
        expected.put(0, lobbySword());
        if (isCompassEnabled()) {
            expected.put(getCompassSlotIndex(), lobbyCompass());
        }
        expected.put(partySpec.slotIndex(), utilityItem(partySpec));
        normalizeExpectedLockedHotbar(p, expected);
    }

    private void normalizeExpectedLockedHotbar(Player p, Map<Integer, ItemStack> expected) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack current = inv.getItem(i);
            if (i <= 8 && !expected.containsKey(i)) {
                if (current != null && current.getType() != Material.AIR) {
                    inv.setItem(i, new ItemStack(Material.AIR));
                }
                continue;
            }
            if (current == null || current.getType() == Material.AIR) {
                continue;
            }
            if (!isLockedHubItem(current)) {
                continue;
            }
            if (!expected.containsKey(i)) {
                inv.setItem(i, new ItemStack(Material.AIR));
                continue;
            }
            ItemStack wanted = expected.get(i);
            if (wanted != null && (current.getType() != wanted.getType() || !sameDisplayName(current, wanted))) {
                inv.setItem(i, wanted);
            }
        }

        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() != Material.AIR && isLockedHubItem(off)) {
            inv.setItemInOffHand(new ItemStack(Material.AIR));
        }

        for (Map.Entry<Integer, ItemStack> e : expected.entrySet()) {
            int slot = e.getKey();
            ItemStack wanted = e.getValue();
            ItemStack current = inv.getItem(slot);
            if (current == null || current.getType() == Material.AIR || !isLockedHubItem(current)
                    || current.getType() != wanted.getType() || !sameDisplayName(current, wanted)) {
                inv.setItem(slot, wanted);
            }
        }
    }

    private boolean sameDisplayName(ItemStack a, ItemStack b) {
        if (a == null || b == null || !a.hasItemMeta() || !b.hasItemMeta()) return false;
        ItemMeta am = a.getItemMeta();
        ItemMeta bm = b.getItemMeta();
        if (!am.hasDisplayName() && !bm.hasDisplayName()) return true;
        if (am.hasDisplayName() != bm.hasDisplayName()) return false;
        return Objects.equals(am.displayName(), bm.displayName());
    }

    private void wipeInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        // Paper rejects null arrays / null offhand — use empty slots.
        inv.setArmorContents(new ItemStack[4]);
        inv.setExtraContents(new ItemStack[inv.getExtraContents().length]);
        inv.setItemInOffHand(new ItemStack(Material.AIR));
    }

    private boolean isLockedHubItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte flag = meta.getPersistentDataContainer().get(lockedItemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private boolean isHoldingHubPvpSword(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        return main != null && isLockedHubItem(main) && main.getType() == swordMaterial();
    }

    private boolean canModifyHub(Player p) {
        return p.isOp() && (p.getGameMode() == GameMode.CREATIVE || isHubBuildMode(p));
    }

    private InventorySnapshot captureInventorySnapshot(Player p) {
        PlayerInventory inv = p.getInventory();
        return new InventorySnapshot(
                cloneItems(inv.getContents()),
                cloneItems(inv.getArmorContents()),
                cloneItems(inv.getExtraContents()),
                cloneItem(inv.getItemInOffHand()),
                inv.getHeldItemSlot()
        );
    }

    private void applyInventorySnapshot(Player p, InventorySnapshot snapshot) {
        PlayerInventory inv = p.getInventory();
        inv.setContents(cloneItems(snapshot.contents()));
        inv.setArmorContents(cloneItems(snapshot.armor()));
        inv.setExtraContents(cloneItems(snapshot.extra()));
        inv.setItemInOffHand(cloneItem(snapshot.offHand()));
        int held = Math.max(0, Math.min(8, snapshot.heldSlot()));
        inv.setHeldItemSlot(held);
    }

    private ItemStack[] cloneItems(ItemStack[] source) {
        if (source == null) return null;
        ItemStack[] out = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = cloneItem(source[i]);
        }
        return out;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private record InventorySnapshot(
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack[] extra,
            ItemStack offHand,
            int heldSlot
    ) {}

    private void activatePvp(Player p) {
        UUID id = p.getUniqueId();
        pvpActive.add(id);
        if (isInPvpZone(p)) {
            pvpZoneParticipants.add(id);
        }
        activateCountdown.remove(id);
        deactivateCountdown.remove(id);
        pendingDeactivateOnDeath.remove(id);
        sendPvpStatusLine(p, "PVP mode Activated", NamedTextColor.RED);
    }

    private void deactivatePvp(Player p, boolean message) {
        UUID id = p.getUniqueId();
        pvpActive.remove(id);
        activateCountdown.remove(id);
        deactivateCountdown.remove(id);
        pendingDeactivateOnDeath.remove(id);
        pvpZoneParticipants.remove(id);

        boolean hadPad = pvpRespawnPads.containsKey(id);
        pvpRespawnPads.remove(id);
        lastPvpPlateActivationMs.remove(id);

        if (message) sendPvpStatusLine(p, "PVP mode Deactivated", NamedTextColor.GREEN);

        boolean inZone = isInPvpZone(p);
        if (inZone || hadPad) {
            hubCenterLocation().ifPresent(lobby -> p.teleport(lobby));
        }
    }

    private void sendPvpStatusLine(Player p, String text, NamedTextColor color) {
        if (p == null) return;
        p.showTitle(net.kyori.adventure.title.Title.title(
                Component.empty(),
                Component.text(text, color),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(1), java.time.Duration.ZERO)
        ));
    }

    private void sendPvpPadActivatedSubtitle(Player p) {
        if (p == null) return;
        p.showTitle(net.kyori.adventure.title.Title.title(
                Component.empty(),
                Component.text("PVP Respawn Activated", NamedTextColor.YELLOW),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(1), java.time.Duration.ZERO)
        ));
    }

    private void sendPvpNotActivatedPrompt(Player p) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastPvpPadInactivePromptAt.getOrDefault(id, 0L);
        if (now - last < 300_000L) return;
        lastPvpPadInactivePromptAt.put(id, now);
        p.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("PVP Mode Not Activated", NamedTextColor.RED),
                Component.text("Hold the Sword to Activate PVP", NamedTextColor.YELLOW),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(2), java.time.Duration.ofMillis(150))
        ));
    }

    /** Inside the PvP zone while PvP is off: select sword and activate immediately. */
    private void activatePvpOnZoneReentry(Player p) {
        if (p == null || !p.isOnline() || canModifyHub(p) || !isInHubProtection(p)) {
            return;
        }
        if (!isInPvpZone(p)) {
            return;
        }
        UUID id = p.getUniqueId();
        if (pvpActive.contains(id)) {
            return;
        }
        p.getInventory().setHeldItemSlot(0);
        activateCountdown.remove(id);
        deactivateCountdown.remove(id);
        pendingDeactivateOnDeath.remove(id);
        activatePvp(p);
    }

    private void trackPvpZoneBoundary(Player p) {
        if (p == null || canModifyHub(p) || !isInHubProtection(p)) {
            return;
        }
        UUID id = p.getUniqueId();
        boolean inside = isInPvpZone(p);
        boolean wasInside = pvpZoneWasInside.getOrDefault(id, false);
        pvpZoneWasInside.put(id, inside);
        if (inside && !wasInside) {
            activatePvpOnZoneReentry(p);
        }
    }

    private void processHubPvpOptIn(Player p) {
        UUID id = p.getUniqueId();
        boolean holding = isHoldingHubPvpSword(p);
        boolean inZone = isInPvpZone(p);

        if (holding) {
            deactivateCountdown.remove(id);
            pendingDeactivateOnDeath.remove(id);
            if (!pvpActive.contains(id)) {
                if (inZone) {
                    activatePvp(p);
                } else {
                    int left = activateCountdown.getOrDefault(id, 10);
                    if (left <= 0) {
                        activatePvp(p);
                    } else {
                        sendPvpStatusLine(p, "PVP mode Activated in: " + left, NamedTextColor.YELLOW);
                        activateCountdown.put(id, left - 1);
                    }
                }
            }
            return;
        }

        activateCountdown.remove(id);
        if (!pvpActive.contains(id)) {
            return;
        }
        int left = deactivateCountdown.getOrDefault(id, 5);
        pendingDeactivateOnDeath.add(id);
        if (left <= 0) {
            deactivatePvp(p, true);
        } else {
            sendPvpStatusLine(p, "PVP mode Deactivated in: " + left, NamedTextColor.YELLOW);
            deactivateCountdown.put(id, left - 1);
        }
    }

    private void startPvpStateTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!isInHubProtection(p)) {
                        deactivatePvp(p, false);
                        continue;
                    }
                    boolean enforceUtilityLoadout = !canModifyHub(p);
                    if (enforceUtilityLoadout && p.getGameMode() != GameMode.ADVENTURE) {
                        p.setGameMode(GameMode.ADVENTURE);
                    }
                    if (enforceUtilityLoadout) {
                        givePvpSword(p);
                        giveLobbyCompass(p);
                        ensureHubPartyItem(p);
                        normalizeHubUtilitySlots(p);
                    }
                    hubCenterLocation().ifPresent(center -> {
                        World w = center.getWorld();
                        if (w != null) {
                            w.setTime(1000L);
                            w.setStorm(false);
                            w.setThundering(false);
                        }
                    });

                    if (!canModifyHub(p)) {
                        trackPvpZoneBoundary(p);
                        processHubPvpOptIn(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /** Clears hub PvP opt-in / respawn invulnerability (e.g. when leaving limbo for a match). */
    public void clearPvpOptInState(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        pvpActive.remove(id);
        activateCountdown.remove(id);
        deactivateCountdown.remove(id);
        pendingDeactivateOnDeath.remove(id);
        pvpZoneParticipants.remove(id);
        pvpZoneWasInside.remove(id);
        pvpInvulnerableUntil.remove(id);
    }

    private void startInvulnerabilityParticleTask() {
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!isInHubProtection(p) || !pvpActive.contains(p.getUniqueId())) {
                        continue;
                    }
                    Long until = pvpInvulnerableUntil.get(p.getUniqueId());
                    if (until == null || until <= now) continue;
                    Location base = p.getLocation().clone().add(0, 0.1, 0);
                    for (int i = 0; i < 14; i++) {
                        double a = (Math.PI * 2.0 * i) / 14.0;
                        double x = base.getX() + Math.cos(a) * 0.55;
                        double z = base.getZ() + Math.sin(a) * 0.55;
                        base.getWorld().spawnParticle(org.bukkit.Particle.DUST, x, base.getY() + 0.9, z, 1, 0, 0, 0, 0,
                                new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 235, 59), 1.1f));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 5L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Optional<Location> spawnOpt = hubCenterLocation();
        if (spawnOpt.isEmpty()) return;
        if (!hw().getBoolean("teleportNewPlayersToHub", true)) return;
        if (!p.hasPlayedBefore()) {
            Location loc = spawnOpt.get();
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline()) return;
                    p.teleportAsync(loc).thenAccept(ok -> {
                        if (Boolean.TRUE.equals(ok)) {
                            applyHubUtilityLoadout(p, true, true);
                        }
                    });
                }
            }.runTaskLater(plugin, 1L);
        } else if (isInHubProtection(p) && !canModifyHub(p)) {
            applyHubUtilityLoadout(p, false, false);
            ensureHubPartyItem(p);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        boolean inHub = isInHubProtection(p) || isLocationInHubProtection(e.getRespawnLocation());
        if (inHub || pendingDeactivateOnDeath.contains(id) || pvpActive.contains(id)) {
            Location resp = resolvePvpRespawnLocation(id);
            hubCenterLocation().ifPresent(loc -> e.setRespawnLocation(resp != null ? resp : loc));
            boolean grantInvuln = pvpActive.contains(id) || pendingDeactivateOnDeath.contains(id);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline()) return;
                    p.setNoDamageTicks(60);
                    if (grantInvuln) {
                        pvpInvulnerableUntil.put(id, System.currentTimeMillis() + 3000L);
                    }
                    if (pvpActive.contains(id)) {
                        p.getInventory().setHeldItemSlot(0);
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!isInHubProtection(victim)) return;

        boolean allow = false;
        if (e instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = getAttackingPlayer(byEntity.getDamager());
            if (attacker != null && isInHubProtection(attacker)) {
                long now = System.currentTimeMillis();
                if (pvpInvulnerableUntil.getOrDefault(victim.getUniqueId(), 0L) > now
                        || pvpInvulnerableUntil.getOrDefault(attacker.getUniqueId(), 0L) > now) {
                    allow = false;
                } else {
                    allow = pvpActive.contains(victim.getUniqueId()) && pvpActive.contains(attacker.getUniqueId());
                }
            }
        }

        if (!allow) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onLethalBypass(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!isInHubProtection(victim)) return;
        if (!(e instanceof EntityDamageByEntityEvent byEntity)) return;
        Player attacker = getAttackingPlayer(byEntity.getDamager());
        if (attacker == null || !isInHubProtection(attacker)) return;
        if (!pvpActive.contains(victim.getUniqueId()) || !pvpActive.contains(attacker.getUniqueId())) return;

        double finalDamage = e.getFinalDamage();
        if (finalDamage < victim.getHealth()) return;
        new BukkitRunnable() {
            @Override public void run() {
                if (victim.isOnline() && victim.getHealth() > 0) {
                    if (!isInHubProtection(victim)) return;
                    victim.setHealth(0.0);
                }
            }
        }.runTask(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!isInHubProtection(p)) return;
        Player killer = p.getKiller();
        if (killer != null && isInHubProtection(killer)) {
            ironLegacy().ifPresent(il -> il.recordHubProtectedKill(killer, p));
        }

        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();

        UUID id = p.getUniqueId();
        boolean shouldDeactivate = pendingDeactivateOnDeath.contains(id);
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                p.spigot().respawn();

                if (shouldDeactivate) {
                    // Died during deactivation countdown: hub spawn, stop counter, deactivate
                    hubCenterLocation().ifPresent(p::teleport);
                    deactivatePvp(p, false);
                    applyHubUtilityLoadout(p, true, false);
                    sendPvpStatusLine(p, "PVP mode Deactivated", NamedTextColor.GREEN);
                    return;
                }

                Location resp = resolvePvpRespawnLocation(id);
                if (resp != null) {
                    p.teleport(resp);
                    applyHubUtilityLoadout(p, false, false);
                    p.getInventory().setHeldItemSlot(0);
                } else {
                    hubCenterLocation().ifPresent(p::teleport);
                    applyHubUtilityLoadout(p, true, false);
                }
            }
        }.runTask(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!isInHubProtection(p)) return;
        if (!canModifyHub(p)) {
            e.setCancelled(true);
            return;
        }
        removePvpPad(e.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!isInHubProtection(p)) return;
        if (!canModifyHub(p)) {
            e.setCancelled(true);
            return;
        }
        if (isPvpPadItem(e.getItemInHand()) && p.isOp()) {
            registerPvpPad(e.getBlock().getLocation());
            p.sendMessage(Component.text("PVP pad created.", NamedTextColor.GREEN));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCraft(PrepareItemCraftEvent e) {
        for (org.bukkit.entity.HumanEntity viewer : e.getViewers()) {
            if (!(viewer instanceof Player p)) continue;
            if (isInHubProtection(p) && !canModifyHub(p)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamageByPlayer(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!isInHubProtection(p)) return;
        if (!canModifyHub(p) && !(e.getEntity() instanceof Player)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!isInHubProtection(p)) return;
        if (canModifyHub(p)) return;
        ItemStack dropped = e.getItemDrop().getItemStack();
        if (isLockedHubItem(dropped)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!isInHubProtection(p)) return;
        if (canModifyHub(p)) return;
        if (isLockedHubItem(e.getMainHandItem()) || isLockedHubItem(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemDamage(PlayerItemDamageEvent e) {
        Player p = e.getPlayer();
        if (!isInHubProtection(p)) return;
        if (canModifyHub(p)) return;
        if (isLockedHubItem(e.getItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isInHubProtection(p)) return;
        if (canModifyHub(p)) return;

        if (e.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack current = e.getCurrentItem();
            ItemStack offhand = p.getInventory().getItemInOffHand();
            if (isLockedHubItem(current) || isLockedHubItem(offhand)) {
                e.setCancelled(true);
                return;
            }
        }

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (isLockedHubItem(current) || isLockedHubItem(cursor)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isInHubProtection(p)) return;
        if (canModifyHub(p)) return;
        if (isLockedHubItem(e.getOldCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeldForZonePvp(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!isInHubProtection(p) || canModifyHub(p) || !isInPvpZone(p)) {
            return;
        }
        if (e.getNewSlot() != 0) {
            return;
        }
        activatePvpOnZoneReentry(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveIntoPvpZone(PlayerMoveEvent e) {
        if (!isInHubProtection(e.getPlayer()) || canModifyHub(e.getPlayer())) {
            return;
        }
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || isLocationInPvpZone(from) || !isLocationInPvpZone(to)) {
            return;
        }
        activatePvpOnZoneReentry(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent e) {
        Optional<Location> centerOpt = hubCenterLocation();
        if (centerOpt.isEmpty()) return;
        Player p = e.getPlayer();
        if (canModifyHub(p)) return;

        Location to = e.getTo();
        if (to == null) return;
        Location center = centerOpt.get();
        if (!Objects.equals(center.getWorld(), to.getWorld())) return;

        if (isLocationInHubProtection(to)) return;
        Optional<LobbyCircleZone> circle = lobbyCircleZone();
        Location safe = to.clone();
        if (circle.isPresent() && Objects.equals(circle.get().center().getWorld(), to.getWorld())) {
            LobbyCircleZone z = circle.get();
            double cx = z.center().getBlockX() + 0.5;
            double cz = z.center().getBlockZ() + 0.5;
            double dx = to.getX() - cx;
            double dz = to.getZ() - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > z.radius() && dist > 0.001) {
                double scale = z.radius() / dist;
                safe.setX(cx + dx * scale);
                safe.setZ(cz + dz * scale);
            }
            if (safe.getY() < z.minY()) {
                safe.setY(z.minY());
            } else if (safe.getY() > z.maxY()) {
                safe.setY(z.maxY());
            }
        } else {
            double size = protectionRadiusBlocks();
            double clampedX = Math.max(center.getX() - size, Math.min(center.getX() + size, to.getX()));
            double clampedZ = Math.max(center.getZ() - size, Math.min(center.getZ() + size, to.getZ()));
            safe.setX(clampedX);
            safe.setZ(clampedZ);
        }
        e.setTo(safe);
    }

    private String padKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName().toLowerCase() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private boolean isRegisteredPvpPad(Location loc) {
        return hw().getStringList("pvpPads").contains(padKey(loc));
    }

    private void handlePvpPadActivation(Player p, Location padBlockLoc) {
        UUID id = p.getUniqueId();
        if (!pvpActive.contains(id)) {
            sendPvpNotActivatedPrompt(p);
            return;
        }
        Location resp = padBlockLoc.clone();
        resp.setX(resp.getBlockX() + 0.5);
        resp.setY(resp.getBlockY());
        resp.setZ(resp.getBlockZ() + 0.5);
        resp.setYaw(p.getLocation().getYaw());
        resp.setPitch(p.getLocation().getPitch());
        String nextPad = padKey(resp);
        String currentPad = pvpRespawnPads.containsKey(id) ? padKey(pvpRespawnPads.get(id)) : "";
        pvpRespawnPads.put(id, resp);
        if (!nextPad.equals(currentPad)) {
            sendPvpPadActivatedSubtitle(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpPressurePlate(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        Player p = e.getPlayer();
        Block block = e.getClickedBlock();
        if (block == null || !Tag.PRESSURE_PLATES.isTagged(block.getType())) return;
        if (hubCenterLocation().isEmpty() || !isInHubProtection(p)) return;
        Location padLoc = block.getLocation();
        if (!isRegisteredPvpPad(padLoc)) return;

        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        if (now - lastPvpPlateActivationMs.getOrDefault(id, 0L) < 200L) return;
        lastPvpPlateActivationMs.put(id, now);

        handlePvpPadActivation(p, padLoc);
    }

    private void registerPvpPad(Location loc) {
        String key = padKey(loc);
        java.util.List<String> pads = new java.util.ArrayList<>(hw().getStringList("pvpPads"));
        if (!pads.contains(key)) {
            pads.add(key);
            hw().set("pvpPads", pads);
            hubWorldYaml.save();
        }
    }

    private void removePvpPad(Location loc) {
        String key = padKey(loc);
        java.util.List<String> pads = new java.util.ArrayList<>(hw().getStringList("pvpPads"));
        if (pads.remove(key)) {
            hw().set("pvpPads", pads);
            hubWorldYaml.save();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        Location loc = e.getLocation();
        if (!isLocationInHubProtection(loc)) return;

        Entity ent = e.getEntity();
        if (ent instanceof Player) return;

        boolean hostile = ent instanceof Monster;
        boolean allowed = hostile ? allowHostileMobSpawnsInHub() : allowPassiveMobSpawnsInHub();
        if (!allowed) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobWalkIntoHubLobby(EntityMoveEvent e) {
        if (!(e.getEntity() instanceof LivingEntity living)) return;
        if (living instanceof Player) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (isLocationInHubProtection(from) || !isLocationInHubProtection(to)) return;
        living.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHubCompassUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isInHubProtection(p)) return;
        if (canModifyHub(p)) return;
        ItemStack hand = e.getItem();
        if (hand == null || !isLockedHubItem(hand)) return;

        HubPlayerMenus menus = plugin.playerMenus();
        if (menus == null) {
            return;
        }

        switch (e.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                if (hand.getType() == Material.COMPASS) {
                    if (isGameLobbyGuiSuppressed(p)) {
                        e.setCancelled(true);
                        return;
                    }
                    menus.openJoinGameMenu(p);
                    e.setCancelled(true);
                    return;
                }
                String title = hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()
                        ? LegacyComponentSerializer.legacySection().serialize(hand.getItemMeta().displayName())
                        : "";
                String titleLower = title.toLowerCase(java.util.Locale.ROOT);
                if (titleLower.contains("party")) {
                    menus.openPartyMenu(p);
                    e.setCancelled(true);
                }
            }
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> {
                e.setCancelled(true);
            }
            default -> { }
        }
    }

    private Player getAttackingPlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
