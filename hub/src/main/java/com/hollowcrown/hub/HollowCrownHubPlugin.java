package com.hollowcrown.hub;

import com.hollowcrown.api.integration.HubAdminGui;
import com.hollowcrown.api.integration.HubPlayerMenus;
import com.hollowcrown.api.integration.HubPluginPolicyApi;
import com.hollowcrown.api.integration.HubTemplatePublishLifecycle;
import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.api.integration.IronLegacyLobbyBridge;
import com.hollowcrown.api.integration.IronLegacyMatchInstanceRuntime;
import com.hollowcrown.api.integration.IronLegacyWorldsCatalog;
import com.hollowcrown.api.integration.PreMatchLobbyService;
import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.queue.MatchQueueService;
import com.hollowcrown.api.platform.GamePlatform;
import com.hollowcrown.api.platform.HubSpawn;
import com.hollowcrown.api.storage.StorageProvider;
import com.hollowcrown.api.template.TemplateRegistry;
import com.hollowcrown.hub.command.HubCommandExecutor;
import com.hollowcrown.hub.command.HubPlayerCommand;
import com.hollowcrown.hub.command.TeamAllyCommandExecutor;
import com.hollowcrown.hub.command.TeamRivalCommandExecutor;
import com.hollowcrown.hub.platform.DefaultGamePlatform;
import com.hollowcrown.hub.platform.HubHubSpawns;
import com.hollowcrown.hub.storage.HubSqlMigrations;
import com.hollowcrown.hub.storage.HubSqlRuntime;
import com.hollowcrown.hub.storage.HubYamlStorageProvider;
import com.hollowcrown.hub.lobby.HubLobbyInstanceRegistry;
import com.hollowcrown.hub.lobby.HubLobbyService;
import com.hollowcrown.hub.gui.DefaultHubAdminGui;
import com.hollowcrown.hub.gui.HubMenuListener;
import com.hollowcrown.hub.gui.LobbyAdminMenuListener;
import com.hollowcrown.hub.gui.LobbyJoinMenuListener;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import com.hollowcrown.hub.integration.DefaultHubPlayerMenus;
import com.hollowcrown.hub.integration.HubIronLegacyMatchInstanceRuntime;
import com.hollowcrown.hub.lobby.HubLobbyArenaState;
import com.hollowcrown.hub.lobby.HubLobbyLayout;
import com.hollowcrown.hub.lobby.HubPreMatchLobbyArenaListener;
import com.hollowcrown.hub.lobby.HubPreMatchLobbyService;
import com.hollowcrown.hub.party.HubPartyListener;
import com.hollowcrown.hub.party.HubPartyService;
import com.hollowcrown.hub.policy.HubPluginPolicyApiAdapter;
import com.hollowcrown.hub.policy.HubPluginPolicyEnforcer;
import com.hollowcrown.hub.policy.HubPluginPolicyService;
import com.hollowcrown.hub.queue.HubMatchQueueService;
import com.hollowcrown.hub.team.TeamListener;
import com.hollowcrown.hub.team.TeamManager;
import com.hollowcrown.hub.template.HubTemplateApiRegistry;
import com.hollowcrown.hub.template.HubManagedTemplateBootstrap;
import com.hollowcrown.hub.template.HubTemplateRegistry;
import com.hollowcrown.hub.world.HubWorldYaml;
import com.hollowcrown.hub.world.HubWorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Optional;

public final class HollowCrownHubPlugin extends JavaPlugin {

    private static HollowCrownHubPlugin instance;
    private DefaultGamePlatform gamePlatform;
    private HubSqlRuntime sqlRuntime;
    private StorageProvider storage;
    private HubTemplateRegistry templateRegistry;
    private HubTemplateApiRegistry templateApiRegistry;
    private HubLobbyInstanceRegistry lobbyInstanceRegistry;
    private HubLobbyService lobbyService;
    private HubPartyService partyService;
    private HubMatchQueueService matchQueueService;
    //private HubTeamService teamService; //Old teams system.
    //private TeamManager teamManager;
    private HubWorldYaml hubWorldYaml;
    private HubWorldManager hubWorldManager;
    private DefaultHubPlayerMenus playerMenus;
    private NamespacedKey lobbyAdminActionKey;
    private NamespacedKey lobbyAdminIdKey;
    private NamespacedKey lobbyJoinActionKey;
    private NamespacedKey lobbyJoinTemplateKey;
    private NamespacedKey lobbyJoinInstanceIdKey;
    private NamespacedKey lobbyJoinMechanicKey;
    private NamespacedKey partyInviteKey;
    private NamespacedKey lobbyWandTemplateKey;
    private NamespacedKey pvpZoneWandKey;
    private NamespacedKey lobbyCircleWandKey;
    private HubLobbyLayout hubLobbyLayout;
    private HubLobbyArenaState hubLobbyArenaState;
    private HubPreMatchLobbyService hubPreMatchLobbyService;
    private HubAdminGui hubAdminGui;
    private HubPluginPolicyService pluginPolicyService;
    private com.hollowcrown.hub.policy.HubTemplatePolicyService templatePolicyService;
    //private com.hollowcrown.hub.alliance.HubAllianceService allianceService;
    private com.hollowcrown.hub.nametag.HubNametagService nametagService;
    private com.hollowcrown.hub.template.HubTemplatePublishPending templatePublishPending;
    private com.hollowcrown.hub.util.HubChatInput chatInput;

    public static HollowCrownHubPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.sqlRuntime = HubSqlRuntime.tryConnect(this);
        this.storage = sqlRuntime != null
                ? sqlRuntime.storageProvider()
                : new HubYamlStorageProvider(this);
        if (sqlRuntime == null && "MYSQL".equalsIgnoreCase(getConfig().getString("storage.type", "YAML").trim())) {
            getLogger().severe("*** storage.type=MYSQL but JDBC pool is NOT active — ALL HOT DATA USES YAML FILES ONLY. ***");
        }
        if (storage.readSchemaVersion("hub") < HubSqlMigrations.HUB_SQL_VERSION) {
            storage.writeSchemaVersion("hub", HubSqlMigrations.HUB_SQL_VERSION);
        }

        DataSource jdbc = sqlRuntime == null ? null : sqlRuntime.dataSource();

        this.gamePlatform = new DefaultGamePlatform(this);
        this.templateRegistry = new HubTemplateRegistry(this);
        this.templateApiRegistry = new HubTemplateApiRegistry(templateRegistry);
        this.lobbyInstanceRegistry = new HubLobbyInstanceRegistry(this, jdbc);
        this.lobbyService = new HubLobbyService(this, lobbyInstanceRegistry, templateRegistry);
        this.partyService = new HubPartyService(this, jdbc);
        this.matchQueueService = new HubMatchQueueService(this, partyService, templateRegistry);
        //this.teamService = new HubTeamService(this);
        //this.teamManager = new TeamManager();
        if (jdbc != null) {
            matchQueueService.attachJdbc(jdbc);
        }
        this.hubWorldYaml = new HubWorldYaml(this);
        this.hubWorldManager = new HubWorldManager(this, hubWorldYaml);
        this.playerMenus = new DefaultHubPlayerMenus(this);
        this.hubAdminGui = new DefaultHubAdminGui(this);
        this.lobbyAdminActionKey = new NamespacedKey(this, "lobby_admin_action");
        this.lobbyAdminIdKey = new NamespacedKey(this, "lobby_admin_id");
        this.lobbyJoinActionKey = new NamespacedKey(this, "lobby_join_action");
        this.lobbyJoinTemplateKey = new NamespacedKey(this, "lobby_join_template");
        this.lobbyJoinInstanceIdKey = new NamespacedKey(this, "lobby_join_instance");
        this.lobbyJoinMechanicKey = new NamespacedKey(this, "lobby_join_mechanic");
        this.partyInviteKey = new NamespacedKey(this, "party_invite_id");
        this.lobbyWandTemplateKey = new NamespacedKey(this, "lobby_wand_template");
        this.pvpZoneWandKey = new NamespacedKey(this, "pvp_zone_wand");
        this.lobbyCircleWandKey = new NamespacedKey(this, "lobby_circle_wand");
        this.hubLobbyLayout = new HubLobbyLayout(this);
        this.hubLobbyArenaState = new HubLobbyArenaState();
        this.hubPreMatchLobbyService = new HubPreMatchLobbyService(this, hubLobbyArenaState);
        this.pluginPolicyService = new HubPluginPolicyService(this);
        this.templatePolicyService = new com.hollowcrown.hub.policy.HubTemplatePolicyService(this);
        //this.allianceService = new com.hollowcrown.hub.alliance.HubAllianceService(this);
        this.nametagService = new com.hollowcrown.hub.nametag.HubNametagService(this);
        this.templatePublishPending = new com.hollowcrown.hub.template.HubTemplatePublishPending();
        this.chatInput = new com.hollowcrown.hub.util.HubChatInput(this);

        Bukkit.getPluginManager().registerEvents(new LobbyAdminMenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new LobbyJoinMenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new HubMenuListener(), this);
        //getLogger().info("Sanity Check!");
        Bukkit.getPluginManager().registerEvents(new TeamListener(), this);
        Bukkit.getPluginManager().registerEvents(new HubPartyListener(this, partyService), this);
        Bukkit.getPluginManager().registerEvents(hubWorldManager, this);
        Bukkit.getPluginManager().registerEvents(
                new HubPluginPolicyEnforcer(this, pluginPolicyService, templatePolicyService), this);
        //Bukkit.getPluginManager().registerEvents(
        //        new com.hollowcrown.hub.alliance.HubAllianceListener(this, allianceService), this);
        Bukkit.getPluginManager().registerEvents(
                new com.hollowcrown.hub.nametag.HubNametagChatListener(nametagService), this);
        Bukkit.getPluginManager().registerEvents(
                new com.hollowcrown.hub.world.HubPvpZoneWandListener(this, hubWorldManager), this);
        Bukkit.getPluginManager().registerEvents(
                new com.hollowcrown.hub.policy.HubTemplatePolicyCloseListener(this), this);
        Bukkit.getPluginManager().registerEvents(
                new com.hollowcrown.hub.template.HubTemplatePublishChatListener(this), this);

        Bukkit.getServicesManager().register(
                GamePlatform.class,
                gamePlatform,
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                HubWorldApi.class,
                hubWorldManager,
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                TemplateRegistry.class,
                templateApiRegistry,
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                PartyService.class,
                partyService,
                this,
                ServicePriority.Normal
        );
        //Bukkit.getServicesManager().register(
        //        TeamService.class,
        //        teamService,
        //        this,
        //        ServicePriority.Normal
        //);
        Bukkit.getServicesManager().register(
                MatchQueueService.class,
                matchQueueService,
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                HubPlayerMenus.class,
                playerMenus,
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                HubAdminGui.class,
                hubAdminGui,
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                IronLegacyWorldsCatalog.class,
                hubLobbyLayout.worldsYaml(),
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                IronLegacyMatchInstanceRuntime.class,
                new HubIronLegacyMatchInstanceRuntime(this, gamePlatform, hubLobbyLayout.worldsYaml()),
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                PreMatchLobbyService.class,
                hubPreMatchLobbyService,
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                HubTemplatePublishLifecycle.class,
                (HubTemplatePublishLifecycle) templateKey -> {
                    templatePolicyService.registerTemplateIfAbsent(templateKey);
                    lobbyService.refreshLobbiesAfterPublish(templateKey);
                },
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                HubPluginPolicyApi.class,
                new HubPluginPolicyApiAdapter(this, pluginPolicyService),
                this,
                ServicePriority.Normal
        );
        Bukkit.getPluginManager().registerEvents(hubPreMatchLobbyService, this);

        Bukkit.getScheduler().runTask(this, () -> {
            RegisteredServiceProvider<IronLegacyLobbyBridge> bridgeReg =
                    Bukkit.getServicesManager().getRegistration(IronLegacyLobbyBridge.class);
            if (bridgeReg == null) {
                getLogger().warning("IronLegacyLobbyBridge not registered (HCIronLegacy disabled?). Lobby arena listeners inactive.");
            } else {
                IronLegacyLobbyBridge bridge = bridgeReg.getProvider();
                hubPreMatchLobbyService.attachBridge(bridge);
                HubPreMatchLobbyArenaListener arena = new HubPreMatchLobbyArenaListener(
                        this, hubLobbyArenaState, bridge, hubPreMatchLobbyService);
                arena.startScheduledTasks();
                Bukkit.getPluginManager().registerEvents(arena, this);
            }
            HubManagedTemplateBootstrap.syncFromDiskOnStartup(this);
        });

        for (var entry : templateRegistry.list()) {
            templatePolicyService.registerTemplateIfAbsent(entry.templateKey());
        }

        Bukkit.getScheduler().runTaskTimer(this, partyService::tickMaintenance, 20L, 20L);

        if (getCommand("hch") != null) {
            HubCommandExecutor exec = new HubCommandExecutor(this);
            getCommand("hch").setExecutor(exec);
            getCommand("hch").setTabCompleter(exec);
        } else {
            getLogger().severe("Command 'hch' missing from plugin.yml");
        }
        if (getCommand("hub") != null) {
            HubPlayerCommand hubCmd = new HubPlayerCommand(this);
            getCommand("hub").setExecutor(hubCmd);
            getCommand("hub").setTabCompleter(hubCmd);
        } else {
            getLogger().warning("Command 'hub' missing from plugin.yml (optional player teleport).");
        }
        /*if (getCommand("ally") != null) {
            var allyCmd = new com.hollowcrown.hub.command.HubAllyCommand(this, allianceService);
            getCommand("ally").setExecutor(allyCmd);
            getCommand("ally").setTabCompleter(allyCmd);
        } else {
            getLogger().warning("Command 'ally' missing from plugin.yml — alliance commands disabled.");
        }
        if (getCommand("enemy") != null) {
            var enemyCmd = new com.hollowcrown.hub.command.HubEnemyCommand(this, allianceService);
            getCommand("enemy").setExecutor(enemyCmd);
            getCommand("enemy").setTabCompleter(enemyCmd);
        } else {
            getLogger().warning("Command 'enemy' missing from plugin.yml — designated-enemy command disabled.");
        }*/

        //You don't need to tick alliance invites if you just give them a timestamp
        //Bukkit.getScheduler().runTaskTimer(this,
        //        () -> allianceService.tickPending(System.currentTimeMillis() / 50L),
        //        40L, 40L);

        //for (org.bukkit.World w : Bukkit.getWorlds()) {
        //    if (nametagService.isManagedWorld(w)) {
        //        nametagService.refreshWorld(w);
        //    }
        //}
        Bukkit.getScheduler().runTaskTimer(this, TeamManager::heartbeat, 100L, 100L);
        if (getCommand("ally") != null) {
            TeamAllyCommandExecutor exec = new TeamAllyCommandExecutor(this);
            getCommand("ally").setExecutor(exec);
            getCommand("ally").setTabCompleter(exec);
        } else {
            getLogger().severe("Command 'ally' missing from plugin.yml");
        }
        if (getCommand("rival") != null) {
            TeamRivalCommandExecutor exec = new TeamRivalCommandExecutor(this);
            getCommand("rival").setExecutor(exec);
            getCommand("rival").setTabCompleter(exec);
        } else {
            getLogger().severe("Command 'rival' missing from plugin.yml");
        }

        HubLog.info(this, "action=hub_enable", "result=ok", "storage=" + storage.backend(),
                "templates=" + templateRegistry.list().size(),
                "parties=" + partyService.allPartiesSnapshot().size());
        getLogger().info("HollowCrownHub enabled (API schema " + GamePlatform.apiSchema() + ").");
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (gamePlatform != null) {
            gamePlatform.clear();
        }
        if (sqlRuntime != null) {
            try {
                sqlRuntime.close();
            } catch (Throwable ignored) {
            }
            sqlRuntime = null;
        }
        instance = null;
        HubLog.info(this, "action=hub_disable", "result=ok");
        getLogger().info("HollowCrownHub disabled.");
    }

    public GamePlatform platform() {
        return gamePlatform;
    }

    public StorageProvider storage() {
        return storage;
    }

    public HubSqlRuntime sqlRuntime() {
        return sqlRuntime;
    }

    public HubTemplateRegistry templates() {
        return templateRegistry;
    }

    public HubLobbyService lobbyService() {
        return lobbyService;
    }

    public HubPartyService partyService() {
        return partyService;
    }

    public HubMatchQueueService matchQueueService() {
        return matchQueueService;
    }

    /** Hub spawn protection, locked hotbar, and hub PvP rules. */
    public HubWorldManager hubWorld() {
        return hubWorldManager;
    }

    /** Join-game + party GUIs for hub/lobby hotbar (also registered as {@link HubPlayerMenus}). */
    public HubPlayerMenus playerMenus() {
        return playerMenus;
    }

    /** Multi-system admin GUIs (Iron Legacy, templates.yml, queues, lobby). */
    public HubAdminGui adminGui() {
        return hubAdminGui;
    }

    public HubPluginPolicyService pluginPolicies() {
        return pluginPolicyService;
    }

    public com.hollowcrown.hub.policy.HubTemplatePolicyService templatePolicies() {
        return templatePolicyService;
    }

    public com.hollowcrown.hub.template.HubTemplatePublishPending templatePublishPending() {
        return templatePublishPending;
    }

    public com.hollowcrown.hub.util.HubChatInput chatInput() {
        return chatInput;
    }

    public com.hollowcrown.hub.nametag.HubNametagService nametags() {
        return nametagService;
    }

    public NamespacedKey lobbyAdminActionKey() {
        return lobbyAdminActionKey;
    }

    public NamespacedKey lobbyAdminIdKey() {
        return lobbyAdminIdKey;
    }

    public NamespacedKey lobbyJoinActionKey() {
        return lobbyJoinActionKey;
    }

    public NamespacedKey lobbyJoinTemplateKey() {
        return lobbyJoinTemplateKey;
    }

    public NamespacedKey lobbyJoinInstanceIdKey() {
        return lobbyJoinInstanceIdKey;
    }

    public NamespacedKey lobbyJoinMechanicKey() {
        return lobbyJoinMechanicKey;
    }

    public NamespacedKey partyInviteKey() {
        return partyInviteKey;
    }

    /** PDC on Hub lobby corner wand items (not the legacy IL namespace). */
    public NamespacedKey lobbyWandTemplateKey() {
        return lobbyWandTemplateKey;
    }

    /** PDC marker on PvP zone selection wand items. */
    public NamespacedKey pvpZoneWandKey() {
        return pvpZoneWandKey;
    }

    public NamespacedKey lobbyCircleWandKey() {
        return lobbyCircleWandKey;
    }

    /** Lobby zone/spawn editing + wand listener (writes HCIronLegacy {@code worlds.yml}). */
    public HubLobbyLayout lobbyLayout() {
        return hubLobbyLayout;
    }

    /**
     * Exit pre-match lobby before returning to hub spawn (same as lobby GUI Leave).
     */
    public void exitLobbyBeforeHubReturn(Player player) {
        if (player == null || hubPreMatchLobbyService == null) {
            return;
        }
        if (hubPreMatchLobbyService.isInLobby(player)) {
            hubPreMatchLobbyService.leaveLobby(player);
            hubWorldManager.suppressGameLobbyGuiBriefly(player, 1500L);
        }
    }

    private void prepareTemplateEditorLeave(Player player) {
        HubIronLegacySupport.ilAdmin().ifPresent(admin -> {
            if (admin.isInTemplateEditorWorld(player)) {
                admin.prepareLeavingTemplateEditor(player);
            }
        });
    }

    private void finishTemplateEditorReturn(Player player) {
        HubIronLegacySupport.ilAdmin().ifPresent(admin -> admin.finishHubReturnFromTemplateEditor(player));
    }

    /**
     * Evacuate/kick path: lobby exit + hub spawn (or Velocity hub when configured on Iron Legacy).
     */
    public boolean sendPlayerToHubSpawn(Player player) {
        return sendPlayerToHubSpawn(player, 1);
    }

    /**
     * Teleport to hub spawn. {@code hubNumber} 1 uses {@code /hch sethub}; higher numbers use
     * {@code hub.instances.<n>} in config.yml when configured.
     */
    public boolean sendPlayerToHubSpawn(Player player, int hubNumber) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        exitLobbyBeforeHubReturn(player);
        prepareTemplateEditorLeave(player);

        RegisteredServiceProvider<IronLegacyLobbyBridge> bridgeReg =
                Bukkit.getServicesManager().getRegistration(IronLegacyLobbyBridge.class);
        if (bridgeReg != null) {
            IronLegacyLobbyBridge bridge = bridgeReg.getProvider();
            if (bridge.velocityEnabled() && !bridge.hubTargetIsThisServer()) {
                bridge.returnToHubOrLobby(player, true, true);
                return true;
            }
        }

        Optional<HubSpawn> spawnOpt = HubHubSpawns.resolve(this, hubNumber);
        if (spawnOpt.isEmpty()) {
            if (hubNumber <= 1) {
                player.sendMessage(Component.text(
                        "Hub spawn is not set. Ask an admin to run /hch sethub.",
                        NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text(
                        "Hub #" + hubNumber + " is not configured. Ask an admin to add hub.instances."
                                + hubNumber + " in config.yml.",
                        NamedTextColor.YELLOW));
            }
            return false;
        }
        HubSpawn s = spawnOpt.get();
        World world = Bukkit.getWorld(s.worldUuid());
        if (world == null) {
            player.sendMessage(Component.text("Hub world is not loaded on this server.", NamedTextColor.RED));
            return false;
        }
        Location loc = safeHubStandLocation(new Location(world, s.x(), s.y(), s.z(), s.yaw(), s.pitch()));
        player.teleportAsync(loc).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                hubWorldManager.applySurvivalHubLoadoutAfterTemplate(player);
                finishTemplateEditorReturn(player);
            }
        });
        return true;
    }

    /**
     * Same as {@link #sendPlayerToHubSpawn(Player)} but uses a synchronous teleport on the main thread
     * so callers can unload/delete the player's current world immediately afterward (world teardown).
     */
    @SuppressWarnings("deprecation") // sync teleport required before synchronous world unload
    public boolean sendPlayerToHubSpawnSync(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        exitLobbyBeforeHubReturn(player);
        prepareTemplateEditorLeave(player);

        RegisteredServiceProvider<IronLegacyLobbyBridge> bridgeReg =
                Bukkit.getServicesManager().getRegistration(IronLegacyLobbyBridge.class);
        if (bridgeReg != null) {
            IronLegacyLobbyBridge bridge = bridgeReg.getProvider();
            if (bridge.velocityEnabled() && !bridge.hubTargetIsThisServer()) {
                bridge.returnToHubOrLobby(player, true, true);
                return true;
            }
        }

        Optional<HubSpawn> spawnOpt = platform().getHubSpawn();
        if (spawnOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    "Hub spawn is not set. Ask an admin to run /hch sethub.",
                    NamedTextColor.YELLOW));
            return false;
        }
        HubSpawn s = spawnOpt.get();
        World world = Bukkit.getWorld(s.worldUuid());
        if (world == null) {
            player.sendMessage(Component.text("Hub world is not loaded on this server.", NamedTextColor.RED));
            return false;
        }
        Location loc = safeHubStandLocation(new Location(world, s.x(), s.y(), s.z(), s.yaw(), s.pitch()));
        player.teleport(loc);
        hubWorldManager.applySurvivalHubLoadoutAfterTemplate(player);
        finishTemplateEditorReturn(player);
        return true;
    }

    /**
     * If the configured hub point is inside water/lava (or the block below is missing), nudge to a
     * standable spot so /hub and leave-lobby do not dump players underwater.
     */
    public static Location safeHubStandLocation(Location raw) {
        if (raw == null || raw.getWorld() == null) {
            return raw;
        }
        World world = raw.getWorld();
        int x = raw.getBlockX();
        int z = raw.getBlockZ();
        int startY = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, raw.getBlockY()));
        if (isStandable(world, x, startY, z)) {
            return new Location(world, x + 0.5, startY, z + 0.5, raw.getYaw(), raw.getPitch());
        }
        // Search upward then downward for air with solid below.
        for (int dy = 1; dy <= 48; dy++) {
            int up = startY + dy;
            if (up < world.getMaxHeight() - 1 && isStandable(world, x, up, z)) {
                return new Location(world, x + 0.5, up, z + 0.5, raw.getYaw(), raw.getPitch());
            }
            int down = startY - dy;
            if (down > world.getMinHeight() && isStandable(world, x, down, z)) {
                return new Location(world, x + 0.5, down, z + 0.5, raw.getYaw(), raw.getPitch());
            }
        }
        int surface = world.getHighestBlockYAt(x, z) + 1;
        surface = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, surface));
        return new Location(world, x + 0.5, surface, z + 0.5, raw.getYaw(), raw.getPitch());
    }

    private static boolean isStandable(World world, int x, int y, int z) {
        var feet = world.getBlockAt(x, y, z).getType();
        var head = world.getBlockAt(x, y + 1, z).getType();
        var below = world.getBlockAt(x, y - 1, z).getType();
        if (!feet.isAir() || !head.isAir()) {
            return false;
        }
        if (!below.isSolid()) {
            return false;
        }
        return below != org.bukkit.Material.MAGMA_BLOCK && below != org.bukkit.Material.CACTUS;
    }

    /** Teleport everyone in a world to hub spawn (lobby destroy, publish refresh, etc.). */
    public void evacuateWorldToHubSpawn(World world) {
        if (world == null) {
            return;
        }
        for (Player p : new ArrayList<>(world.getPlayers())) {
            sendPlayerToHubSpawn(p);
        }
    }

    /**
     * Evacuate with synchronous hub teleports so the world can be unloaded/deleted in the same tick.
     */
    public void evacuateWorldToHubSpawnSync(World world) {
        if (world == null) {
            return;
        }
        for (Player p : new ArrayList<>(world.getPlayers())) {
            sendPlayerToHubSpawnSync(p);
        }
    }
}
