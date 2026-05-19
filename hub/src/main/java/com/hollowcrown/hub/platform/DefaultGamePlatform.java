package com.hollowcrown.hub.platform;

import com.hollowcrown.api.ids.MechanicId;
import com.hollowcrown.api.mechanic.MinigameMechanic;
import com.hollowcrown.api.platform.GamePlatform;
import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.api.platform.MatchInstanceReservation;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.template.HubTemplateEntry;
import com.hollowcrown.hub.util.WorldFolderCopier;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultGamePlatform implements GamePlatform {

    private static final String CFG_HUB = "hub.spawn";

    private final HollowCrownHubPlugin plugin;
    private final Logger log;
    private final Map<String, MinigameMechanic> mechanics = new ConcurrentHashMap<>();
    private volatile Optional<HubSpawn> hubSpawnCache = Optional.empty();

    public DefaultGamePlatform(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        reloadHubSpawnFromConfig();
    }

    private void reloadHubSpawnFromConfig() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        String uuidStr = c.getString(CFG_HUB + ".world-uuid", "");
        if (uuidStr == null || uuidStr.isBlank()) {
            hubSpawnCache = Optional.empty();
            return;
        }
        try {
            UUID w = UUID.fromString(uuidStr.trim());
            if (!c.isSet(CFG_HUB + ".x")) {
                hubSpawnCache = Optional.empty();
                return;
            }
            double x = c.getDouble(CFG_HUB + ".x");
            double y = c.getDouble(CFG_HUB + ".y");
            double z = c.getDouble(CFG_HUB + ".z");
            float yaw = (float) c.getDouble(CFG_HUB + ".yaw");
            float pitch = (float) c.getDouble(CFG_HUB + ".pitch");
            hubSpawnCache = Optional.of(new HubSpawn(w, x, y, z, yaw, pitch));
        } catch (IllegalArgumentException ex) {
            log.warning("Invalid hub.spawn.world-uuid in config: " + uuidStr);
            hubSpawnCache = Optional.empty();
        }
    }

    @Override
    public void registerMechanic(MechanicId id, MinigameMechanic mechanic) {
        MinigameMechanic prev = mechanics.put(id.value(), mechanic);
        if (prev != null) {
            log.warning("Mechanic replaced: id=" + id.value());
        } else {
            log.info("Mechanic registered: id=" + id.value());
        }
    }

    @Override
    public void unregisterMechanic(MechanicId id) {
        if (mechanics.remove(id.value()) != null) {
            log.info("Mechanic unregistered: id=" + id.value());
        }
    }

    @Override
    public MinigameMechanic getMechanic(MechanicId id) {
        return mechanics.get(id.value());
    }

    @Override
    public Optional<HubSpawn> getHubSpawn() {
        return hubSpawnCache;
    }

    @Override
    public void setHubSpawn(HubSpawn spawn) {
        FileConfiguration c = plugin.getConfig();
        c.set(CFG_HUB + ".world-uuid", spawn.worldUuid().toString());
        c.set(CFG_HUB + ".x", spawn.x());
        c.set(CFG_HUB + ".y", spawn.y());
        c.set(CFG_HUB + ".z", spawn.z());
        c.set(CFG_HUB + ".yaw", spawn.yaw());
        c.set(CFG_HUB + ".pitch", spawn.pitch());
        plugin.saveConfig();
        hubSpawnCache = Optional.of(spawn);
        HubLog.info(plugin, "action=hub_spawn_set", "worldUuid=" + spawn.worldUuid());
    }

    @Override
    public void clearHubSpawn() {
        FileConfiguration c = plugin.getConfig();
        c.set(CFG_HUB, null);
        plugin.saveConfig();
        hubSpawnCache = Optional.empty();
        HubLog.info(plugin, "action=hub_spawn_clear", "result=ok");
    }

    @Override
    public Set<String> registeredMechanicIdStrings() {
        return Collections.unmodifiableSet(mechanics.keySet());
    }

    @Override
    public Optional<MatchInstanceReservation> createMatchWorld(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            return Optional.empty();
        }
        Optional<HubTemplateEntry> opt = plugin.templates().get(templateKey);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        HubTemplateEntry entry = opt.get();
        if (!"iron-legacy".equalsIgnoreCase(entry.mechanicId())) {
            return Optional.empty();
        }
        String srcFolder = entry.sourceWorldName();
        if (srcFolder == null || srcFolder.isBlank()) {
            return Optional.empty();
        }
        String tk = entry.templateKey();
        Path worldContainer = Bukkit.getWorldContainer().toPath();
        Path source = worldContainer.resolve(srcFolder).normalize();
        if (!source.startsWith(worldContainer) || !Files.isDirectory(source)) {
            log.warning("createMatchWorld: missing source folder for template=" + tk + " path=" + source);
            return Optional.empty();
        }
        String worldName = allocateMatchWorldFolderName(worldContainer, tk);
        Path target = worldContainer.resolve(worldName).normalize();
        if (!target.startsWith(worldContainer) || Files.exists(target) || Bukkit.getWorld(worldName) != null) {
            log.warning("createMatchWorld: target exists or invalid: " + worldName);
            return Optional.empty();
        }
        try {
            WorldFolderCopier.copyDirectory(source, target);
        } catch (IOException ex) {
            log.log(Level.WARNING, "createMatchWorld: copy failed template=" + tk, ex);
            return Optional.empty();
        }
        World world = Bukkit.createWorld(new WorldCreator(worldName));
        if (world == null) {
            try {
                WorldFolderCopier.deleteDirectory(target);
            } catch (IOException ignored) {
            }
            log.warning("createMatchWorld: Bukkit.createWorld failed for " + worldName);
            return Optional.empty();
        }
        HubLog.info(plugin, "action=match_world_created", "templateKey=" + tk, "worldName=" + worldName);
        return Optional.of(new MatchInstanceReservation(tk, worldName, world.getUID()));
    }

    private static String allocateMatchWorldFolderName(Path worldContainer, String templateKey) {
        String base = templateKey;
        for (int i = 0; i < 5000; i++) {
            long n = (System.nanoTime() ^ (i * 0x9E3779B97L)) & 0x7FFF_FFFFL;
            String wn = base + "-M" + n;
            Path t = worldContainer.resolve(wn).normalize();
            if (!t.startsWith(worldContainer)) {
                continue;
            }
            if (!Files.exists(t) && Bukkit.getWorld(wn) == null) {
                return wn;
            }
        }
        return base + "-M" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public void clear() {
        mechanics.clear();
    }
}
