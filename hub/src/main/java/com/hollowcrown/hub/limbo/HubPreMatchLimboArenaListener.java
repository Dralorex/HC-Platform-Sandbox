package com.hollowcrown.hub.limbo;

import com.hollowcrown.api.integration.HubPlayerMenus;
import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.api.integration.IronLegacyLimboBridge;
import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.party.HubPartyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Limbo queue zones: locked utility hotbar + opt-in PvP via held sword (same rules as legacy IL limbo arena).
 */
public final class HubPreMatchLimboArenaListener implements Listener {

    private final HollowCrownHubPlugin hub;
    private final HubLimboArenaState arenaState;
    private final IronLegacyLimboBridge bridge;
    private final PreMatchLimboService limbo;
    private final NamespacedKey lobbyItemKey;

    public HubPreMatchLimboArenaListener(
            HollowCrownHubPlugin hub,
            HubLimboArenaState arenaState,
            IronLegacyLimboBridge bridge,
            PreMatchLimboService limbo
    ) {
        this.hub = hub;
        this.arenaState = arenaState;
        this.bridge = bridge;
        this.limbo = limbo;
        RegisteredServiceProvider<HubWorldApi> reg = Bukkit.getServicesManager().getRegistration(HubWorldApi.class);
        if (reg == null) {
            throw new IllegalStateException("HubWorldApi must be registered before limbo arena listener.");
        }
        this.lobbyItemKey = reg.getProvider().lockedLobbyUtilityItemKey();
    }

    public void startScheduledTasks() {
        startPvpStateTask();
        startInvulnerabilityParticleTask();
    }

    private Material swordMaterial() {
        String raw = bridge.limboPvpSwordMaterialName();
        try {
            Material m = Material.valueOf(raw.toUpperCase());
            return m.isItem() ? m : Material.IRON_SWORD;
        } catch (Exception ignored) {
            return Material.IRON_SWORD;
        }
    }

    private boolean isLockedLobbyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte flag = meta.getPersistentDataContainer().get(lobbyItemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private boolean isHoldingLobbySword(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        return main != null && main.getType() == swordMaterial();
    }

    private boolean inLimboProtected(Player p) {
        return limbo.isInLimbo(p);
    }

    private void activatePvp(Player p) {
        UUID id = p.getUniqueId();
        arenaState.pvpActive().add(id);
        arenaState.activateCountdown().remove(id);
        arenaState.deactivateCountdown().remove(id);
        arenaState.pendingDeactivateOnDeath().remove(id);
        sendPvpStatusLine(p, "PVP mode Activated", NamedTextColor.RED);
    }

    private void deactivatePvp(Player p, boolean message) {
        UUID id = p.getUniqueId();
        arenaState.pvpActive().remove(id);
        arenaState.activateCountdown().remove(id);
        arenaState.deactivateCountdown().remove(id);
        arenaState.pendingDeactivateOnDeath().remove(id);
        if (message) sendPvpStatusLine(p, "PVP mode Deactivated", NamedTextColor.GREEN);
        Location limboSpawn = limbo.getSessionLimboSpawn(p);
        if (limboSpawn != null && limbo.isInLimbo(p)) {
            p.teleport(limboSpawn);
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

    private void startPvpStateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!limbo.isInLimbo(p)) {
                        deactivatePvp(p, false);
                        continue;
                    }
                    if (p.getGameMode() != GameMode.ADVENTURE) {
                        p.setGameMode(GameMode.ADVENTURE);
                    }
                    int held = Math.min(8, Math.max(0, p.getInventory().getHeldItemSlot()));
                    RegisteredServiceProvider<HubWorldApi> hubReg = Bukkit.getServicesManager().getRegistration(HubWorldApi.class);
                    if (hubReg != null) {
                        hubReg.getProvider().applyLimboUtilityLoadout(p, held);
                    }

                    for (String limboWorld : limbo.limboWorldNames()) {
                        World lw = Bukkit.getWorld(limboWorld);
                        if (lw != null) {
                            lw.setTime(1000L);
                            lw.setStorm(false);
                            lw.setThundering(false);
                        }
                    }

                    boolean holding = isHoldingLobbySword(p);
                    UUID id = p.getUniqueId();
                    Map<UUID, Integer> activateCountdown = arenaState.activateCountdown();
                    Map<UUID, Integer> deactivateCountdown = arenaState.deactivateCountdown();
                    Set<UUID> pvpActive = arenaState.pvpActive();
                    Set<UUID> pendingDeactivateOnDeath = arenaState.pendingDeactivateOnDeath();

                    if (holding) {
                        deactivateCountdown.remove(id);
                        pendingDeactivateOnDeath.remove(id);
                        if (pvpActive.contains(id)) continue;
                        int left = activateCountdown.getOrDefault(id, 10);
                        if (left <= 0) {
                            activatePvp(p);
                        } else {
                            sendPvpStatusLine(p, "PVP mode Activated in: " + left, NamedTextColor.YELLOW);
                            activateCountdown.put(id, left - 1);
                        }
                        continue;
                    }

                    activateCountdown.remove(id);
                    if (!pvpActive.contains(id)) continue;
                    int left = deactivateCountdown.getOrDefault(id, 5);
                    pendingDeactivateOnDeath.add(id);
                    if (left <= 0) {
                        deactivatePvp(p, true);
                    } else {
                        sendPvpStatusLine(p, "PVP mode Deactivated in: " + left, NamedTextColor.YELLOW);
                        deactivateCountdown.put(id, left - 1);
                    }
                }
            }
        }.runTaskTimer(hub, 20L, 20L);
    }

    private void startInvulnerabilityParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Map<UUID, Long> pvpInvulnerableUntil = arenaState.pvpInvulnerableUntil();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!limbo.isInLimbo(p) || !arenaState.pvpActive().contains(p.getUniqueId())) {
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
        }.runTaskTimer(hub, 1L, 5L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!bridge.isHubLikeServerRole()) return;
        Player p = e.getPlayer();
        if (limbo.isInLimbo(p)) {
            limbo.applyLimboLoadout(p);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Location limboSpawn = limbo.getSessionLimboSpawn(p);
        if (limboSpawn == null) return;
        UUID id = p.getUniqueId();

        e.setRespawnLocation(limboSpawn);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                p.setNoDamageTicks(60);
                if (arenaState.pvpActive().contains(id) || arenaState.pendingDeactivateOnDeath().contains(id)) {
                    arenaState.pvpInvulnerableUntil().put(id, System.currentTimeMillis() + 3000L);
                }
            }
        }.runTaskLater(hub, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!inLimboProtected(victim)) return;

        boolean allow = false;
        if (e instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = getAttackingPlayer(byEntity.getDamager());
            if (attacker != null && limbo.isInLimbo(attacker)) {
                long now = System.currentTimeMillis();
                Map<UUID, Long> inv = arenaState.pvpInvulnerableUntil();
                if (inv.getOrDefault(victim.getUniqueId(), 0L) > now
                        || inv.getOrDefault(attacker.getUniqueId(), 0L) > now) {
                    allow = false;
                } else {
                    allow = arenaState.pvpActive().contains(victim.getUniqueId()) && arenaState.pvpActive().contains(attacker.getUniqueId());
                }
            }
        }

        if (!allow) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onLethalBypass(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!inLimboProtected(victim)) return;
        if (!(e instanceof EntityDamageByEntityEvent byEntity)) return;
        Player attacker = getAttackingPlayer(byEntity.getDamager());
        if (attacker == null || !limbo.isInLimbo(attacker)) return;
        if (!arenaState.pvpActive().contains(victim.getUniqueId()) || !arenaState.pvpActive().contains(attacker.getUniqueId())) return;

        double finalDamage = e.getFinalDamage();
        if (finalDamage < victim.getHealth()) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (victim.isOnline() && victim.getHealth() > 0) {
                    if (!limbo.isInLimbo(victim)) return;
                    victim.setHealth(0.0);
                }
            }
        }.runTask(hub);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!inLimboProtected(p)) return;
        Player killer = p.getKiller();
        if (killer != null && limbo.isInLimbo(killer)) {
            bridge.recordKillCredit(killer.getUniqueId());
        }

        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();

        UUID id = p.getUniqueId();
        boolean shouldDeactivate = arenaState.pendingDeactivateOnDeath().contains(id);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                p.spigot().respawn();
                Location limboSpawn = limbo.getSessionLimboSpawn(p);
                if (limboSpawn != null) {
                    p.teleport(limboSpawn);
                }
                limbo.applyLimboLoadout(p);
                if (shouldDeactivate) {
                    deactivatePvp(p, false);
                    sendPvpStatusLine(p, "PVP mode Deactivated", NamedTextColor.GREEN);
                }
            }
        }.runTask(hub);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLimboUtilityUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!limbo.isInLimbo(p)) return;
        ItemStack hand = e.getItem();
        if (hand == null || !isLockedLobbyItem(hand)) return;

        switch (e.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                RegisteredServiceProvider<HubWorldApi> hubReg = Bukkit.getServicesManager().getRegistration(HubWorldApi.class);
                if (hubReg != null && hubReg.getProvider().isLimboLeaveUtilityItem(hand)) {
                    leaveLimboWithParty(p, hubReg.getProvider());
                    e.setCancelled(true);
                    return;
                }
                if (hand.getType() == Material.COMPASS) {
                    Optional.ofNullable(Bukkit.getServicesManager().getRegistration(HubPlayerMenus.class))
                            .map(RegisteredServiceProvider::getProvider)
                            .ifPresentOrElse(
                                    m -> m.openJoinGameMenu(p),
                                    () -> p.sendMessage(Component.text(
                                            "Join menu unavailable (HollowCrownHub).",
                                            NamedTextColor.RED)));
                    e.setCancelled(true);
                    return;
                }
                if (hand.getType() != swordMaterial()) {
                    Optional.ofNullable(Bukkit.getServicesManager().getRegistration(HubPlayerMenus.class))
                            .map(RegisteredServiceProvider::getProvider)
                            .ifPresentOrElse(
                                    m -> m.openPartyMenu(p),
                                    () -> p.sendMessage(Component.text(
                                            "Party menu unavailable (HollowCrownHub).",
                                            NamedTextColor.RED)));
                    e.setCancelled(true);
                }
            }
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> e.setCancelled(true);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMobSpawn(CreatureSpawnEvent e) {
        Location loc = e.getLocation();
        if (!limbo.isInLimboWorld(loc.getWorld())) return;

        Entity ent = e.getEntity();
        if (ent instanceof Player) return;

        boolean hostile = ent instanceof Monster;
        boolean allowed = hostile ? bridge.limboHostileMobSpawns() : bridge.limboPassiveMobSpawns();
        if (!allowed) {
            e.setCancelled(true);
        }
    }

    private Player getAttackingPlayer(Entity damager) {
        if (damager instanceof Player pl) return pl;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player pl) return pl;
        return null;
    }

    /**
     * Leave the limbo queue. When the actor is a party leader with online members in the same
     * limbo session, drag the whole party back to the hub together.
     */
    private void leaveLimboWithParty(Player actor, HubWorldApi hubWorldApi) {
        Set<UUID> sessionPlayers = limbo.playersInSameTemplateSession(actor);
        PartyView party = resolvePartyForLeader(actor);

        List<Player> movers = new ArrayList<>();
        movers.add(actor);
        if (party != null) {
            for (UUID memberId : party.membersJoinOrder()) {
                if (memberId.equals(actor.getUniqueId())) continue;
                if (!sessionPlayers.isEmpty() && !sessionPlayers.contains(memberId)) continue;
                Player member = Bukkit.getPlayer(memberId);
                if (member == null || !member.isOnline()) continue;
                if (!limbo.isInLimbo(member)) continue;
                movers.add(member);
            }
        }

        boolean dragged = movers.size() > 1;
        for (Player mover : movers) {
            limbo.leaveLimbo(mover);
            if (hubWorldApi != null) {
                hubWorldApi.suppressGameLobbyGuiBriefly(mover, 1500L);
            }
            bridge.sendToLobbyExact(mover, true, true);
            if (mover.equals(actor)) {
                mover.sendMessage(Component.text(
                        dragged ? "Left limbo queue. Your party came with you." : "Left limbo queue.",
                        NamedTextColor.YELLOW));
            } else {
                mover.sendMessage(Component.text()
                        .append(HubPartyText.coloredPlayerName(hub, actor))
                        .append(Component.text(" took the party back to the hub.", NamedTextColor.AQUA))
                        .build());
            }
        }
    }

    private PartyView resolvePartyForLeader(Player actor) {
        if (actor == null) return null;
        RegisteredServiceProvider<PartyService> reg =
                Bukkit.getServicesManager().getRegistration(PartyService.class);
        if (reg == null) return null;
        return reg.getProvider().partyForMember(actor.getUniqueId())
                .filter(v -> v.isLeader(actor.getUniqueId()) && v.memberCount() > 1)
                .orElse(null);
    }
}
