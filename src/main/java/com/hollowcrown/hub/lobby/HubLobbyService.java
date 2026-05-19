package com.hollowcrown.hub.lobby;

import com.hollowcrown.api.ids.LobbyInstanceId;
import com.hollowcrown.api.ids.MechanicId;
import com.hollowcrown.api.ids.TemplateKey;
import com.hollowcrown.api.integration.IronLegacyTemplateRow;
import com.hollowcrown.api.mechanic.MechanicContext;
import com.hollowcrown.api.mechanic.MinigameMechanic;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.api.queue.MatchEnqueueResult;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.command.MatchEnqueueMessages;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import com.hollowcrown.hub.template.HubTemplateEntry;
import com.hollowcrown.hub.template.HubTemplateRegistry;
import com.hollowcrown.hub.util.WorldFolderCopier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Creates and tears down lobby instance worlds ({@code <template>-L#} under the server world container).
 */
public final class HubLobbyService {

    private static final Pattern FOLDER_SAFE = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");
    /** When Iron Legacy rows are unavailable, assume this lobby capacity for fill checks. */
    private static final int DEFAULT_LOBBY_MAX_PLAYERS = 64;

    private final HollowCrownHubPlugin plugin;
    private final HubLobbyInstanceRegistry instances;
    private final HubTemplateRegistry templates;
    private final AtomicLong prioritySequence;

    public HubLobbyService(HollowCrownHubPlugin plugin, HubLobbyInstanceRegistry instances, HubTemplateRegistry templates) {
        this.plugin = plugin;
        this.instances = instances;
        this.templates = templates;
        long maxPrio = instances.all().stream().mapToLong(HubLobbyInstanceRecord::prioritySequence).max().orElse(0L);
        this.prioritySequence = new AtomicLong(Math.max(maxPrio, System.currentTimeMillis()));
    }

    public HubLobbyInstanceRegistry instances() {
        return instances;
    }

    public List<HubLobbyInstanceRecord> listVisibleTo(boolean op) {
        List<HubLobbyInstanceRecord> out = new ArrayList<>(instances.all());
        out.removeIf(r -> r.state() == LobbyInstanceState.BROKEN && !op);
        out.sort(Comparator.comparingLong(HubLobbyInstanceRecord::prioritySequence));
        return out;
    }

    public String createLobby(String templateKey) throws IOException {
        Optional<HubTemplateEntry> t = templates.get(templateKey);
        if (t.isEmpty()) {
            throw new IOException("Unknown template. Register with /hch template set … then /hch template setsource …");
        }
        HubTemplateEntry entry = t.get();
        String sourceFolder = resolveEffectiveSourceFolder(entry);
        if (sourceFolder == null || sourceFolder.isBlank()) {
            throw new IOException("Template has no source world folder. Use: /hch template setsource " + entry.templateKey() + " <folderName>");
        }
        validateFolderName(sourceFolder);
        String tk = entry.templateKey();
        int lobbyIndex = nextLobbyIndex(tk);
        String worldName = tk + "-L" + lobbyIndex;
        validateFolderName(worldName);

        Path worldContainer = Bukkit.getWorldContainer().toPath();
        Path source = worldContainer.resolve(sourceFolder).normalize();
        Path target = worldContainer.resolve(worldName).normalize();
        if (!source.startsWith(worldContainer) || !target.startsWith(worldContainer)) {
            throw new IOException("Invalid path.");
        }
        if (!Files.isDirectory(source)) {
            throw new IOException("Source folder does not exist: " + source);
        }
        if (Files.exists(target)) {
            throw new IOException("Target folder already exists: " + worldName);
        }
        if (Bukkit.getWorld(worldName) != null) {
            throw new IOException("World already loaded: " + worldName);
        }

        WorldFolderCopier.copyDirectory(source, target);

        World world = Bukkit.createWorld(new WorldCreator(worldName));
        if (world == null) {
            try {
                WorldFolderCopier.deleteDirectory(target);
            } catch (IOException ignored) {
            }
            throw new IOException("Bukkit failed to create world: " + worldName);
        }

        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long prio = prioritySequence.incrementAndGet();
        HubLobbyInstanceRecord rec = new HubLobbyInstanceRecord(
                id, tk, worldName, world.getUID(), lobbyIndex, now, prio, LobbyInstanceState.OPEN);
        instances.put(rec);

        notifyCreated(entry, rec, world);

        HubLog.info(plugin, "action=lobby_create", "id=" + id, "templateKey=" + tk, "worldName=" + worldName);
        return id;
    }

    /**
     * Prefer Hub template source mapping, but auto-fallback to Iron Legacy worlds.yml worldName when missing.
     * This keeps legacy IL templates usable without requiring manual /hch template setsource every time.
     */
    private String resolveEffectiveSourceFolder(HubTemplateEntry entry) {
        String src = entry.sourceWorldName();
        if (src != null && !src.isBlank()) {
            return src.trim();
        }
        var il = HubIronLegacySupport.ilAdmin();
        if (il.isEmpty()) {
            return src;
        }
        Optional<IronLegacyTemplateRow> row = il.get().listTemplates().stream()
                .filter(r -> r.key().equalsIgnoreCase(entry.templateKey()))
                .findFirst();
        if (row.isEmpty()) {
            return src;
        }
        String inferred = row.get().worldName();
        if (inferred == null || inferred.isBlank()) {
            return src;
        }
        try {
            templates.setSourceWorld(entry.templateKey(), inferred.trim());
            HubLog.info(plugin, "action=lobby_source_autofill", "templateKey=" + entry.templateKey(), "sourceWorld=" + inferred.trim());
        } catch (Throwable ignored) {
            // Non-fatal: still return inferred source for this create attempt.
        }
        return inferred.trim();
    }

    /**
     * @return human-readable result for chat
     */
    public String destroyLobby(String id, boolean alertOpsOnDiskFailure) {
        Optional<HubLobbyInstanceRecord> opt = instances.byId(id);
        if (opt.isEmpty()) {
            return "No lobby instance with that id.";
        }
        HubLobbyInstanceRecord rec = opt.get();
        World world = rec.loadedWorld();

        if (world != null) {
            evacuate(world);
        }

        notifyReleased(rec);

        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }

        Path worldContainer = Bukkit.getWorldContainer().toPath();
        Path target = worldContainer.resolve(rec.worldName()).normalize();
        boolean deleted = WorldFolderCopier.deleteDirectoryBestEffort(target, 5, 200L);

        if (deleted) {
            instances.remove(rec.id());
            HubLog.info(plugin, "action=lobby_destroy", "id=" + rec.id(), "result=ok");
            return "Lobby destroyed and folder removed.";
        }
        instances.updateState(rec.id(), LobbyInstanceState.BROKEN);
        String msg = "World unloaded but folder delete failed after retries. Manual delete: " + rec.worldName();
        HubLog.warn(plugin, "action=lobby_destroy", "id=" + rec.id(), "result=broken", "path=" + target);
        if (alertOpsOnDiskFailure) {
            Component line = Component.text("[HollowCrownHub] " + msg, NamedTextColor.RED);
            Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage(line));
        }
        return msg + " Marked BROKEN. OP: /hch lobby forget " + rec.id();
    }

    public boolean forgetBroken(String id) {
        Optional<HubLobbyInstanceRecord> opt = instances.byId(id);
        if (opt.isEmpty()) {
            return false;
        }
        HubLobbyInstanceRecord rec = opt.get();
        if (rec.state() != LobbyInstanceState.BROKEN) {
            return false;
        }
        instances.remove(rec.id());
        HubLog.info(plugin, "action=lobby_forget", "id=" + id);
        return true;
    }

    public void teleportAdmin(Player player, String id) {
        instances.byId(id).flatMap(r -> Optional.ofNullable(r.loadedWorld())).ifPresentOrElse(w -> {
            player.teleport(w.getSpawnLocation());
            player.sendMessage(net.kyori.adventure.text.Component.text("Teleported to " + w.getName(), net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }, () -> player.sendMessage(net.kyori.adventure.text.Component.text("Instance not loaded or unknown id.", net.kyori.adventure.text.format.NamedTextColor.RED)));
    }

    /**
     * OPEN lobby instances for a template, oldest first ({@link HubLobbyInstanceRecord#prioritySequence} ascending).
     */
    public List<HubLobbyInstanceRecord> listOpenForTemplate(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            return List.of();
        }
        List<HubLobbyInstanceRecord> out = new ArrayList<>();
        for (HubLobbyInstanceRecord r : instances.all()) {
            if (r.state() != LobbyInstanceState.OPEN) {
                continue;
            }
            if (!r.templateKey().equalsIgnoreCase(templateKey.trim())) {
                continue;
            }
            out.add(r);
        }
        out.sort(Comparator.comparingLong(HubLobbyInstanceRecord::prioritySequence));
        return out;
    }

    /**
     * Player join GUI: teleport into an OPEN, loaded instance.
     *
     * @return true if teleported
     */
    public boolean teleportPlayerJoin(Player player, String instanceId) {
        Optional<HubLobbyInstanceRecord> opt = instances.byId(instanceId);
        if (opt.isEmpty()) {
            player.sendMessage(Component.text("That lobby no longer exists.", NamedTextColor.RED));
            return false;
        }
        HubLobbyInstanceRecord r = opt.get();
        if (r.state() != LobbyInstanceState.OPEN) {
            player.sendMessage(Component.text("That lobby is not available.", NamedTextColor.RED));
            return false;
        }
        World w = r.loadedWorld();
        if (w == null) {
            player.sendMessage(Component.text("That lobby world is not loaded.", NamedTextColor.RED));
            return false;
        }
        player.teleport(w.getSpawnLocation());
        player.sendMessage(Component.text("Joined " + w.getName(), NamedTextColor.GREEN));
        HubLog.info(plugin, "action=lobby_player_join", "player=" + player.getName(), "id=" + instanceId, "world=" + r.worldName());
        return true;
    }

    /**
     * Join an OPEN lobby with room for this player (or their whole party when the actor is party leader),
     * preferring the oldest instance. Creates a new lobby when none exist, all are full, or none fit the party.
     *
     * @return true if everyone who should move was teleported
     */
    public boolean joinOldestOpenForTemplate(Player player, String templateKey) {
        Optional<HubTemplateEntry> entryOpt = templates.get(templateKey);
        if (entryOpt.isEmpty()) {
            player.sendMessage(Component.text("Unknown game template.", NamedTextColor.RED));
            return false;
        }
        HubTemplateEntry entry = entryOpt.get();
        LobbyCaps caps = resolveLobbyCaps(entry);

        PreparedLobbyJoin prep = tryPreparePartyMove(player);
        if (prep == null) {
            return false;
        }
        if (prep.unitSize() > caps.teamSizeMax) {
            player.sendMessage(Component.text(
                    "Your party is too large for this game (max " + caps.teamSizeMax + " per lobby).",
                    NamedTextColor.RED));
            return false;
        }

        for (HubLobbyInstanceRecord r : listOpenForTemplate(templateKey)) {
            World w = r.loadedWorld();
            if (w == null) {
                continue;
            }
            int remaining = caps.maxPlayers - w.getPlayerCount();
            if (remaining >= prep.unitSize()) {
                return teleportUnitToLobby(r.id(), prep.partyOpt(), player);
            }
        }

        try {
            String newId = createLobby(templateKey);
            HubLog.info(plugin, "action=lobby_auto_created_for_join", "templateKey=" + templateKey, "id=" + newId,
                    "unitSize=" + prep.unitSize());
            return teleportUnitToLobby(newId, prep.partyOpt(), player);
        } catch (IOException ex) {
            player.sendMessage(Component.text("Could not open a lobby: " + ex.getMessage(), NamedTextColor.RED));
            HubLog.warn(plugin, "action=lobby_auto_create_failed", "templateKey=" + templateKey, "err=" + ex.getMessage());
            return false;
        }
    }

    /**
     * Instance-picker GUI: move solo player or whole party into a specific OPEN lobby if capacity allows.
     */
    public boolean teleportUnitIntoOpenLobby(Player player, String instanceId) {
        Optional<HubLobbyInstanceRecord> opt = instances.byId(instanceId);
        if (opt.isEmpty()) {
            player.sendMessage(Component.text("That lobby no longer exists.", NamedTextColor.RED));
            return false;
        }
        HubLobbyInstanceRecord r = opt.get();
        if (r.state() != LobbyInstanceState.OPEN) {
            player.sendMessage(Component.text("That lobby is not available.", NamedTextColor.RED));
            return false;
        }
        Optional<HubTemplateEntry> entryOpt = templates.get(r.templateKey());
        if (entryOpt.isEmpty()) {
            player.sendMessage(Component.text("Unknown template for that lobby.", NamedTextColor.RED));
            return false;
        }
        LobbyCaps caps = resolveLobbyCaps(entryOpt.get());

        PreparedLobbyJoin prep = tryPreparePartyMove(player);
        if (prep == null) {
            return false;
        }
        if (prep.unitSize() > caps.teamSizeMax) {
            player.sendMessage(Component.text(
                    "Your party is too large for this game (max " + caps.teamSizeMax + " per lobby).",
                    NamedTextColor.RED));
            return false;
        }

        World w = r.loadedWorld();
        if (w == null) {
            player.sendMessage(Component.text("That lobby world is not loaded.", NamedTextColor.RED));
            return false;
        }
        int remaining = caps.maxPlayers - w.getPlayerCount();
        if (remaining < prep.unitSize()) {
            player.sendMessage(Component.text(
                    "That lobby cannot fit your party. Pick another or left-click to open a fresh lobby.",
                    NamedTextColor.RED));
            return false;
        }

        return teleportUnitToLobby(instanceId, prep.partyOpt(), player);
    }

    /** @return null if the player cannot move a unit (message already sent). */
    private PreparedLobbyJoin tryPreparePartyMove(Player player) {
        Optional<PartyView> partyOpt = plugin.partyService().partyForMember(player.getUniqueId());
        if (partyOpt.isEmpty()) {
            return new PreparedLobbyJoin(Optional.empty(), 1);
        }
        PartyView pv = partyOpt.get();
        if (!pv.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    MatchEnqueueMessages.playerDenyMessage(MatchEnqueueResult.Status.NOT_PARTY_LEADER),
                    NamedTextColor.RED));
            return null;
        }
        for (UUID mid : pv.membersJoinOrder()) {
            if (Bukkit.getPlayer(mid) == null) {
                player.sendMessage(Component.text(
                        MatchEnqueueMessages.playerDenyMessage(MatchEnqueueResult.Status.OFFLINE_PARTY_MEMBER),
                        NamedTextColor.RED));
                return null;
            }
        }
        return new PreparedLobbyJoin(partyOpt, pv.memberCount());
    }

    private record PreparedLobbyJoin(Optional<PartyView> partyOpt, int unitSize) {
    }

    private LobbyCaps resolveLobbyCaps(HubTemplateEntry entry) {
        Optional<IronLegacyTemplateRow> ilRow = HubIronLegacySupport.ilAdmin().flatMap(admin ->
                admin.listTemplates().stream()
                        .filter(r -> r.key().equalsIgnoreCase(entry.templateKey()))
                        .findFirst());
        int maxPlayers = ilRow.map(IronLegacyTemplateRow::maxPlayers).filter(m -> m > 0).orElse(DEFAULT_LOBBY_MAX_PLAYERS);
        int teamSizeMax = ilRow.map(IronLegacyTemplateRow::teamSizeMax)
                .filter(t -> t > 0)
                .orElse(Math.max(1, entry.queueMaxUnitSize()));
        return new LobbyCaps(maxPlayers, teamSizeMax);
    }

    private boolean teleportUnitToLobby(String instanceId, Optional<PartyView> partyOpt, Player actor) {
        List<Player> movers = new ArrayList<>();
        if (partyOpt.isPresent()) {
            for (UUID id : partyOpt.get().membersJoinOrder()) {
                Player pl = Bukkit.getPlayer(id);
                if (pl != null) {
                    movers.add(pl);
                }
            }
            if (movers.size() != partyOpt.get().memberCount()) {
                actor.sendMessage(Component.text(
                        MatchEnqueueMessages.playerDenyMessage(MatchEnqueueResult.Status.OFFLINE_PARTY_MEMBER),
                        NamedTextColor.RED));
                return false;
            }
        } else {
            movers.add(actor);
        }
        boolean ok = true;
        for (Player pl : movers) {
            if (!teleportPlayerJoin(pl, instanceId)) {
                ok = false;
            }
        }
        return ok;
    }

    private record LobbyCaps(int maxPlayers, int teamSizeMax) {
    }

    private void evacuate(World world) {
        Location fallback = plugin.platform().getHubSpawn()
                .map(this::toLocation)
                .orElseGet(() -> Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation());
        if (fallback == null) {
            return;
        }
        for (Player p : new ArrayList<>(world.getPlayers())) {
            p.teleport(fallback);
        }
    }

    private Location toLocation(HubSpawn s) {
        World w = Bukkit.getWorld(s.worldUuid());
        if (w == null) {
            return null;
        }
        return new Location(w, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
    }

    private void notifyCreated(HubTemplateEntry tmpl, HubLobbyInstanceRecord rec, World world) {
        MinigameMechanic m = plugin.platform().getMechanic(MechanicId.of(tmpl.mechanicId()));
        if (m == null) {
            return;
        }
        try {
            m.onLobbyInstanceCreated(new MechanicContext(
                    TemplateKey.of(tmpl.templateKey()),
                    new LobbyInstanceId(world.getUID(), rec.lobbyIndex()),
                    world.getUID()
            ));
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Mechanic onLobbyInstanceCreated failed", t);
        }
    }

    private void notifyReleased(HubLobbyInstanceRecord rec) {
        Optional<HubTemplateEntry> t = templates.get(rec.templateKey());
        if (t.isEmpty()) {
            return;
        }
        MinigameMechanic m = plugin.platform().getMechanic(MechanicId.of(t.get().mechanicId()));
        if (m == null) {
            return;
        }
        try {
            m.onLobbyInstanceReleased(new MechanicContext(
                    TemplateKey.of(rec.templateKey()),
                    new LobbyInstanceId(rec.worldUuid(), rec.lobbyIndex()),
                    rec.worldUuid()
            ));
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Mechanic onLobbyInstanceReleased failed", ex);
        }
    }

    private int nextLobbyIndex(String templateKey) {
        Set<Integer> used = new HashSet<>();
        for (HubLobbyInstanceRecord r : instances.all()) {
            if (!r.templateKey().equalsIgnoreCase(templateKey)) {
                continue;
            }
            if (r.state() == LobbyInstanceState.OPEN || r.state() == LobbyInstanceState.BROKEN) {
                used.add(r.lobbyIndex());
            }
        }
        int i = 1;
        while (used.contains(i)) {
            i++;
        }
        return i;
    }

    private static void validateFolderName(String name) throws IOException {
        if (name == null || !FOLDER_SAFE.matcher(name).matches()) {
            throw new IOException("Invalid folder/world name: " + name);
        }
    }
}
