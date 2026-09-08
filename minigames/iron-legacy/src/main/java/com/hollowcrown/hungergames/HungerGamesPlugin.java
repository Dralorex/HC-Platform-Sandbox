package com.hollowcrown.hungergames;

import com.hollowcrown.hub.team.TeamManager;
import com.hollowcrown.hungergames.commands.HGCommand;
import com.hollowcrown.hungergames.gui.MenuListener;
import com.hollowcrown.hungergames.managers.SpawnManager;
import com.hollowcrown.hungergames.managers.GameManager;
import com.hollowcrown.hungergames.managers.EnvoyManager;
import com.hollowcrown.hungergames.managers.AddChestModeManager;
import com.hollowcrown.hungergames.managers.ReplenishManager;
import com.hollowcrown.hungergames.managers.ReplenishModeManager;
import com.hollowcrown.hungergames.managers.GiftDropManager;
import com.hollowcrown.hungergames.managers.BuildRuleManager;
import com.hollowcrown.hungergames.managers.LobbyManager;
import com.hollowcrown.hungergames.managers.LobbyShardManager;
import com.hollowcrown.hungergames.managers.TemplateWorldManager;
import com.hollowcrown.hungergames.managers.WorldSettingsManager;
import com.hollowcrown.hungergames.managers.IconManager;
import com.hollowcrown.hungergames.managers.ConfigFilesManager;
import com.hollowcrown.hungergames.managers.ServerRoleManager;
import com.hollowcrown.hungergames.managers.VelocityBridgeManager;
import com.hollowcrown.hungergames.managers.SharedStateManager;
import com.hollowcrown.hungergames.managers.AdminHistoryManager;
import com.hollowcrown.hungergames.managers.ResourcePackManager;
import com.hollowcrown.hungergames.managers.KillLeaderboardManager;
import com.hollowcrown.hungergames.managers.KillLeaderboardSidebarManager;
import com.hollowcrown.hungergames.listeners.PickupGuardListener;
import com.hollowcrown.hungergames.listeners.RulesListener;
import com.hollowcrown.hungergames.listeners.EnvoyListener;
import com.hollowcrown.hungergames.listeners.GiftDropListener;
import com.hollowcrown.hungergames.listeners.ReplenishChestProtectionListener;
import com.hollowcrown.api.integration.HubPlayerMenus;
import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.api.integration.IronLegacyHubBridge;
import com.hollowcrown.api.integration.IronLegacyLimboBridge;
import com.hollowcrown.api.integration.IronLegacyMatchInstanceRuntime;
import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.api.integration.IronLegacyWorldsCatalog;
import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.api.minigame.MinigameBootstrap;
import com.hollowcrown.api.minigame.MinigamePluginKey;
import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.queue.MatchQueueService;
import com.hollowcrown.api.chat.TeamChatProvider;
import com.hollowcrown.api.platform.GamePlatform;
import com.hollowcrown.api.template.TemplateRegistry;
import com.hollowcrown.api.world.GameWorldScopeResolver;
import com.hollowcrown.hungergames.integration.HubManagedLobbyRegistry;
import com.hollowcrown.hungergames.integration.IlGameWorldScopeResolver;
import com.hollowcrown.hungergames.integration.IronLegacyHubBridgeImpl;
import com.hollowcrown.hungergames.integration.IronLegacyLimboBridgeImpl;
import com.hollowcrown.hungergames.integration.IronLegacyTemplateAdminImpl;
import com.hollowcrown.hungergames.integration.IronLegacyMechanic;
import com.hollowcrown.hungergames.listeners.HubShardBoundaryListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class HungerGamesPlugin extends JavaPlugin {

    /** Single id for Hub templates, GamePlatform registration, and match provisioning. */
    public static final MinigamePluginKey HOLLOW_CROWN_KEY = MinigamePluginKey.hollowCrown("iron-legacy", "Iron Legacy");

    private static HungerGamesPlugin instance;
    /** Hollow Crown Hub {@link GamePlatform}; null if stack missing (plugin disabled). */
    private GamePlatform hollowGamePlatform;
    /** Hub {@link TemplateRegistry}; null if Hub did not register it. */
    private TemplateRegistry hollowTemplateRegistry;
    /** Hub {@link PartyService}; null if Hub did not register it. */
    private PartyService hollowPartyService;
    /** Hub {@link MatchQueueService}; null if Hub did not register it. */
    private MatchQueueService hollowMatchQueueService;
    private final HubManagedLobbyRegistry hubManagedLobbyRegistry = new HubManagedLobbyRegistry();
    private TeamManager teamManager;
    private SpawnManager spawnManager;
    private GameManager gameManager;
    private ChatInput chatInput;
    private EnvoyManager envoyManager;
    private AddChestModeManager addMode;
    private PickupGuardListener pickupGuard;
    private ReplenishManager replenishManager;
    private ReplenishModeManager replenishMode;
    private GiftDropManager giftDropManager;
    private LobbyManager lobbyManager;
    private LobbyShardManager lobbyShardManager;
    private TemplateWorldManager templateWorldManager;
    private WorldSettingsManager worldSettingsManager;
    //private TeamNameDatabaseManager teamNameDatabaseManager;
    private IconManager iconManager;
    private ConfigFilesManager configFilesManager;
    private ServerRoleManager serverRoleManager;
    private VelocityBridgeManager velocityBridgeManager;
    private SharedStateManager sharedStateManager;
    private AdminHistoryManager adminHistoryManager;
    private ResourcePackManager resourcePackManager;
    private IronLegacyLimboBridge ironLegacyLimboBridge;
    private KillLeaderboardManager killLeaderboardManager;
    private KillLeaderboardSidebarManager killLeaderboardSidebarManager;

    // NEW: centralized rules (crafting/build/dig) + categories.yml loader
    private BuildRuleManager buildRules;

    public static HungerGamesPlugin get() {
        return instance;
    }

    /** Handy static accessor used by commands/listeners. */
    public static BuildRuleManager rules() {
        return get().buildRules;
    }

    @Override
    public void onEnable() {
        if (!requireHollowCrownStack()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        instance = this;

        // Ensure config exists
        saveDefaultConfig();

        if (!registerWithHollowCrownHub()) {
            instance = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Managers
        this.chatInput    = new ChatInput(this);
        this.configFilesManager = new ConfigFilesManager(this);
        this.serverRoleManager = new ServerRoleManager(this);
        this.velocityBridgeManager = new VelocityBridgeManager(this);
        this.sharedStateManager = new SharedStateManager(this);
        this.adminHistoryManager = new AdminHistoryManager(this);
        this.resourcePackManager = new ResourcePackManager(this);
        this.killLeaderboardManager = new KillLeaderboardManager(this);
        this.killLeaderboardSidebarManager = new KillLeaderboardSidebarManager(this);
        //this.teamManager  = new TeamManager(this);
        this.spawnManager = new SpawnManager(this);
        this.gameManager  = new GameManager(this, spawnManager);
        this.envoyManager = new EnvoyManager(this);
        this.giftDropManager = new GiftDropManager(this);
        this.addMode = new AddChestModeManager(this, envoyManager);
        this.pickupGuard = new PickupGuardListener();
        this.replenishManager = new ReplenishManager(this);
        this.replenishMode = new ReplenishModeManager(this, replenishManager);
        this.lobbyManager = new LobbyManager(this);
        this.lobbyShardManager = new LobbyShardManager(this);
        this.templateWorldManager = new TemplateWorldManager(this);
        this.ironLegacyLimboBridge = new IronLegacyLimboBridgeImpl(this);
        this.worldSettingsManager = new WorldSettingsManager(this);
        //this.teamNameDatabaseManager = new TeamNameDatabaseManager(this);
        this.iconManager = new IconManager(this);
        try {
            var report = this.templateWorldManager.recoverStaleInstances();
            getLogger().info("Template instance recovery: scanned=" + report.scanned()
                    + ", cleaned=" + report.cleaned()
                    + ", skipped=" + report.skipped()
                    + ", failed=" + report.failed());
        } catch (Throwable t) {
            getLogger().warning("Template instance recovery failed: " + t.getMessage());
        }

        // Rules + enforcement listener
        this.buildRules = new BuildRuleManager(this);
        Bukkit.getPluginManager().registerEvents(new RulesListener(this, buildRules), this);

        // Command wiring
        HGCommand cmd = new HGCommand(gameManager, spawnManager, chatInput);
        if (getCommand("il") != null) {
            getCommand("il").setExecutor(cmd);
            getCommand("il").setTabCompleter(cmd);
        } else {
            getLogger().severe("Command 'il' not found. Make sure it's defined in plugin.yml!");
        }

        // Other listeners
        Bukkit.getPluginManager().registerEvents(gameManager, this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new EnvoyListener(envoyManager), this);
        Bukkit.getPluginManager().registerEvents(new GiftDropListener(giftDropManager, envoyManager), this);
        Bukkit.getPluginManager().registerEvents(addMode, this);
        Bukkit.getPluginManager().registerEvents(pickupGuard, this);
        Bukkit.getPluginManager().registerEvents(replenishMode, this);
        Bukkit.getPluginManager().registerEvents(new ReplenishChestProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new HubShardBoundaryListener(this), this);
        Bukkit.getPluginManager().registerEvents(resourcePackManager, this);
        Bukkit.getPluginManager().registerEvents(killLeaderboardSidebarManager, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        Bukkit.getPluginManager().registerEvents(lobbyShardManager, this);

        getLogger().info("HCIronLegacy enabled.");
        getLogger().info("Role=" + serverRoleManager.role() + ", velocityEnabled=" + serverRoleManager.velocityEnabled() + ", serverId=" + serverRoleManager.serverId());
        getLogger().info("SharedState backend=" + sharedStateManager.backend());

        Bukkit.getServicesManager().register(GameWorldScopeResolver.class, new IlGameWorldScopeResolver(this), this, ServicePriority.Normal);
        //Bukkit.getServicesManager().register(TeamChatProvider.class, new IronLegacyTeamChatProvider(this), this, ServicePriority.Normal);
        Bukkit.getServicesManager().register(IronLegacyHubBridge.class, new IronLegacyHubBridgeImpl(this), this, ServicePriority.Normal);
        Bukkit.getServicesManager().register(IronLegacyTemplateAdmin.class, new IronLegacyTemplateAdminImpl(this), this, ServicePriority.Normal);
        Bukkit.getServicesManager().register(IronLegacyLimboBridge.class, ironLegacyLimboBridge, this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        hubManagedLobbyRegistry.clear();
        unregisterFromHollowCrownHub();
        if (gameManager != null) gameManager.forceStop();
        getLogger().info("HCIronLegacy disabled.");
    }

    private boolean requireHollowCrownStack() {
        if (!isPluginReady("HC-Foundation")) {
            logMissingHollowCrownStack();
            return false;
        }
        if (!isPluginReady("HollowCrownHub")) {
            logMissingHollowCrownStack();
            return false;
        }
        return true;
    }

    private static boolean isPluginReady(String name) {
        var p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    private void logMissingHollowCrownStack() {
        getLogger().severe("This Hollow Crown HCIronLegacy plugin requires \"HollowCrownHub\" (Hollow Crown Hub)");
        getLogger().severe("and the HC-Foundation plugin (shared hollowcrown-api) to be installed and enabled.");
    }

    private boolean registerWithHollowCrownHub() {
        RegisteredServiceProvider<GamePlatform> reg =
                Bukkit.getServicesManager().getRegistration(GamePlatform.class);
        if (reg == null) {
            getLogger().severe("HollowCrownHub did not register GamePlatform. Is Hub enabled?");
            return false;
        }
        this.hollowGamePlatform = reg.getProvider();
        RegisteredServiceProvider<TemplateRegistry> tplReg =
                Bukkit.getServicesManager().getRegistration(TemplateRegistry.class);
        if (tplReg != null) {
            this.hollowTemplateRegistry = tplReg.getProvider();
            getLogger().info("Hub TemplateRegistry: " + hollowTemplateRegistry.list().size() + " template(s).");
        } else {
            this.hollowTemplateRegistry = null;
            getLogger().warning("Hub did not register TemplateRegistry; update HollowCrownHub.");
        }
        RegisteredServiceProvider<PartyService> partyReg =
                Bukkit.getServicesManager().getRegistration(PartyService.class);
        if (partyReg != null) {
            this.hollowPartyService = partyReg.getProvider();
            getLogger().info("Hub PartyService available (queue integration can use partyForMember).");
        } else {
            this.hollowPartyService = null;
            getLogger().warning("Hub did not register PartyService; update HollowCrownHub.");
        }
        RegisteredServiceProvider<MatchQueueService> mqReg =
                Bukkit.getServicesManager().getRegistration(MatchQueueService.class);
        if (mqReg != null) {
            this.hollowMatchQueueService = mqReg.getProvider();
            getLogger().info("Hub MatchQueueService available.");
        } else {
            this.hollowMatchQueueService = null;
            getLogger().warning("Hub did not register MatchQueueService; update HollowCrownHub.");
        }
        MinigameBootstrap.registerMechanic(this, hollowGamePlatform, HOLLOW_CROWN_KEY, new IronLegacyMechanic(this));
        return true;
    }

    private void unregisterFromHollowCrownHub() {
        if (hollowGamePlatform != null) {
            try {
                MinigameBootstrap.unregisterMechanic(this, hollowGamePlatform, HOLLOW_CROWN_KEY);
            } catch (Throwable ignored) {
            }
            hollowGamePlatform = null;
        }
        hollowTemplateRegistry = null;
        hollowPartyService = null;
        hollowMatchQueueService = null;
    }

    public TeamManager teams()   { return teamManager; }
    public SpawnManager spawns() { return spawnManager; }
    public GameManager game()    { return gameManager; }
    public ChatInput chatInput() { return chatInput; }
    public EnvoyManager envoys() { return envoyManager; }
    public AddChestModeManager addMode() { return addMode; }
    public PickupGuardListener pickupGuard() { return pickupGuard; }
    public ReplenishManager replenish() { return replenishManager; }
    public ReplenishModeManager replenishMode() { return replenishMode; }
    public GiftDropManager giftDrops() { return giftDropManager; }
    public LobbyManager lobby() { return lobbyManager; }

    /** Hollow Crown Hub hub-world protections + utility hotbar; empty when Hub is unavailable. */
    public Optional<HubWorldApi> hubWorldApi() {
        RegisteredServiceProvider<HubWorldApi> reg =
                Bukkit.getServicesManager().getRegistration(HubWorldApi.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }

    /** Hub join/party GUIs when HollowCrownHub is present. */
    public Optional<HubPlayerMenus> hubPlayerMenus() {
        RegisteredServiceProvider<HubPlayerMenus> reg =
                Bukkit.getServicesManager().getRegistration(HubPlayerMenus.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
    public LobbyShardManager lobbyShards() { return lobbyShardManager; }
    public TemplateWorldManager templates() { return templateWorldManager; }
    public WorldSettingsManager worldSettings() { return worldSettingsManager; }
    //public TeamNameDatabaseManager teamNames() { return teamNameDatabaseManager; }
    public IconManager icons() { return iconManager; }
    public ConfigFilesManager files() { return configFilesManager; }
    public ServerRoleManager serverRole() { return serverRoleManager; }
    public VelocityBridgeManager velocity() { return velocityBridgeManager; }
    public SharedStateManager sharedState() { return sharedStateManager; }
    public AdminHistoryManager history() { return adminHistoryManager; }
    public ResourcePackManager packs() { return resourcePackManager; }
    /**
     * Pre-match limbo queues and arena UX (registered by HollowCrownHub).
     */
    public PreMatchLimboService limbo() {
        RegisteredServiceProvider<PreMatchLimboService> reg =
                Bukkit.getServicesManager().getRegistration(PreMatchLimboService.class);
        if (reg == null) {
            throw new IllegalStateException("PreMatchLimboService not registered. Is HollowCrownHub enabled?");
        }
        return reg.getProvider();
    }
    public KillLeaderboardManager leaderboard() { return killLeaderboardManager; }
    public KillLeaderboardSidebarManager leaderboardSidebar() { return killLeaderboardSidebarManager; }

    /** Optional instance getter if you prefer non-static access. */
    public BuildRuleManager buildRules() { return buildRules; }

    /** Hub platform service when present. */
    public GamePlatform hollowGamePlatform() {
        return hollowGamePlatform;
    }

    /**
     * Hub-published templates ({@code /hch template …}); empty when Hub is too old or disabled.
     */
    public Optional<TemplateRegistry> hollowTemplateRegistry() {
        return Optional.ofNullable(hollowTemplateRegistry);
    }

    /** Hub party graph when present (queue, invites, GUI — all HollowCrownHub). */
    public Optional<PartyService> hollowPartyService() {
        return Optional.ofNullable(hollowPartyService);
    }

    public Optional<MatchQueueService> hollowMatchQueueService() {
        return Optional.ofNullable(hollowMatchQueueService);
    }

    /** Hub-owned reader/writer for HCIronLegacy worlds.yml metadata. */
    public Optional<IronLegacyWorldsCatalog> worldsCatalog() {
        RegisteredServiceProvider<IronLegacyWorldsCatalog> reg =
                Bukkit.getServicesManager().getRegistration(IronLegacyWorldsCatalog.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }

    public Optional<IronLegacyMatchInstanceRuntime> matchInstanceRuntime() {
        RegisteredServiceProvider<IronLegacyMatchInstanceRuntime> reg =
                Bukkit.getServicesManager().getRegistration(IronLegacyMatchInstanceRuntime.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }

    /** Hub-created lobby worlds this JVM knows about (via {@link IronLegacyMechanic}). */
    public HubManagedLobbyRegistry hubManagedLobbies() {
        return hubManagedLobbyRegistry;
    }
}
