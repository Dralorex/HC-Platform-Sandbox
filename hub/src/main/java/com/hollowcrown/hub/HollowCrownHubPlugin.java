package com.hollowcrown.hub;

import com.hollowcrown.api.integration.HubAdminGui;
import com.hollowcrown.api.integration.HubPlayerMenus;
import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.api.integration.IronLegacyLimboBridge;
import com.hollowcrown.api.integration.IronLegacyMatchInstanceRuntime;
import com.hollowcrown.api.integration.IronLegacyWorldsCatalog;
import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.queue.MatchQueueService;
import com.hollowcrown.api.platform.GamePlatform;
import com.hollowcrown.api.storage.StorageProvider;
import com.hollowcrown.api.template.TemplateRegistry;
import com.hollowcrown.hub.command.HubCommandExecutor;
import com.hollowcrown.hub.command.HubPlayerCommand;
import com.hollowcrown.hub.command.TeamAllyCommandExecutor;
import com.hollowcrown.hub.command.TeamRivalCommandExecutor;
import com.hollowcrown.hub.platform.DefaultGamePlatform;
import com.hollowcrown.hub.storage.HubSqlMigrations;
import com.hollowcrown.hub.storage.HubSqlRuntime;
import com.hollowcrown.hub.storage.HubYamlStorageProvider;
import com.hollowcrown.hub.lobby.HubLobbyInstanceRegistry;
import com.hollowcrown.hub.lobby.HubLobbyService;
import com.hollowcrown.hub.gui.DefaultHubAdminGui;
import com.hollowcrown.hub.gui.HubMenuListener;
import com.hollowcrown.hub.gui.LobbyAdminMenuListener;
import com.hollowcrown.hub.gui.LobbyJoinMenuListener;
import com.hollowcrown.hub.integration.DefaultHubPlayerMenus;
import com.hollowcrown.hub.integration.HubIronLegacyMatchInstanceRuntime;
import com.hollowcrown.hub.limbo.HubLimboArenaState;
import com.hollowcrown.hub.limbo.HubLimboLayout;
import com.hollowcrown.hub.limbo.HubPreMatchLimboArenaListener;
import com.hollowcrown.hub.limbo.HubPreMatchLimboService;
import com.hollowcrown.hub.party.HubPartyListener;
import com.hollowcrown.hub.party.HubPartyService;
import com.hollowcrown.hub.queue.HubMatchQueueService;
import com.hollowcrown.hub.team.TeamListener;
import com.hollowcrown.hub.team.TeamManager;
import com.hollowcrown.hub.template.HubTemplateApiRegistry;
import com.hollowcrown.hub.template.HubTemplateRegistry;
import com.hollowcrown.hub.world.HubWorldYaml;
import com.hollowcrown.hub.world.HubWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;

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
    private NamespacedKey partyInviteKey;
    private NamespacedKey limboWandTemplateKey;
    private NamespacedKey pvpZoneWandKey;
    private HubLimboLayout hubLimboLayout;
    private HubLimboArenaState hubLimboArenaState;
    private HubPreMatchLimboService hubPreMatchLimboService;
    private HubAdminGui hubAdminGui;

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
        this.partyInviteKey = new NamespacedKey(this, "party_invite_id");
        this.limboWandTemplateKey = new NamespacedKey(this, "limbo_wand_template");
        this.pvpZoneWandKey = new NamespacedKey(this, "pvp_zone_wand");
        this.hubLimboLayout = new HubLimboLayout(this);
        this.hubLimboArenaState = new HubLimboArenaState();
        this.hubPreMatchLimboService = new HubPreMatchLimboService(this, hubLimboArenaState);

        Bukkit.getPluginManager().registerEvents(new LobbyAdminMenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new LobbyJoinMenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new HubMenuListener(), this);
        //getLogger().info("Sanity Check!");
        Bukkit.getPluginManager().registerEvents(new TeamListener(), this);
        Bukkit.getPluginManager().registerEvents(new HubPartyListener(this, partyService), this);
        Bukkit.getPluginManager().registerEvents(hubWorldManager, this);
        Bukkit.getPluginManager().registerEvents(
                new com.hollowcrown.hub.world.HubPvpZoneWandListener(this, hubWorldManager), this);

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
                hubLimboLayout.worldsYaml(),
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                IronLegacyMatchInstanceRuntime.class,
                new HubIronLegacyMatchInstanceRuntime(this, gamePlatform, hubLimboLayout.worldsYaml()),
                this,
                ServicePriority.Normal
        );
        Bukkit.getServicesManager().register(
                PreMatchLimboService.class,
                hubPreMatchLimboService,
                this,
                ServicePriority.Normal
        );
        Bukkit.getPluginManager().registerEvents(hubPreMatchLimboService, this);

        Bukkit.getScheduler().runTask(this, () -> {
            RegisteredServiceProvider<IronLegacyLimboBridge> bridgeReg =
                    Bukkit.getServicesManager().getRegistration(IronLegacyLimboBridge.class);
            if (bridgeReg == null) {
                getLogger().warning("IronLegacyLimboBridge not registered (HCIronLegacy disabled?). Limbo arena listeners inactive.");
                return;
            }
            IronLegacyLimboBridge bridge = bridgeReg.getProvider();
            hubPreMatchLimboService.attachBridge(bridge);
            HubPreMatchLimboArenaListener arena = new HubPreMatchLimboArenaListener(
                    this, hubLimboArenaState, bridge, hubPreMatchLimboService);
            arena.startScheduledTasks();
            Bukkit.getPluginManager().registerEvents(arena, this);
        });

        Bukkit.getScheduler().runTaskTimer(this, partyService::tickMaintenance, 20L, 20L);

        if (getCommand("hch") != null) {
            HubCommandExecutor exec = new HubCommandExecutor(this);
            getCommand("hch").setExecutor(exec);
            getCommand("hch").setTabCompleter(exec);
        } else {
            getLogger().severe("Command 'hch' missing from plugin.yml");
        }
        if (getCommand("hub") != null) {
            getCommand("hub").setExecutor(new HubPlayerCommand(this));
        } else {
            getLogger().warning("Command 'hub' missing from plugin.yml (optional player teleport).");
        }
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

    /** Join-game + party GUIs for hub/limbo hotbar (also registered as {@link HubPlayerMenus}). */
    public HubPlayerMenus playerMenus() {
        return playerMenus;
    }

    /** Multi-system admin GUIs (Iron Legacy, templates.yml, queues, lobby). */
    public HubAdminGui adminGui() {
        return hubAdminGui;
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

    public NamespacedKey partyInviteKey() {
        return partyInviteKey;
    }

    /** PDC on Hub limbo corner wand items (not the legacy IL namespace). */
    public NamespacedKey limboWandTemplateKey() {
        return limboWandTemplateKey;
    }

    /** PDC marker on PvP zone selection wand items. */
    public NamespacedKey pvpZoneWandKey() {
        return pvpZoneWandKey;
    }

    /** Limbo zone/spawn editing + wand listener (writes HCIronLegacy {@code worlds.yml}). */
    public HubLimboLayout limboLayout() {
        return hubLimboLayout;
    }
}
