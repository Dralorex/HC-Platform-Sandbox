# Hollow Crown Platform — Feature List

Copy-paste reference for **HC-Foundation**, **HollowCrownHub**, and **HCIronLegacy**.

**Maintainers:** update this file in the same change set whenever you add, remove, or change user-visible behavior (commands, GUIs, permissions, config keys, integration contracts). Also update `docs/PLATFORM_COMMANDS.md` if commands changed, and `CHANGELOG.md` under `[Unreleased]`.

**Detail (GUIs, integration flows):** `docs/PLATFORM_REFERENCE.md`

**Bundle baseline:** HC-Plugins a0.1.22

**Load order:** HC-Foundation (STARTUP) → HollowCrownHub → HCIronLegacy

================================================================================
HC-FOUNDATION
================================================================================

Purpose
  Thin bootstrap plugin. Shades hollowcrown-api onto the server classpath.
  No gameplay, commands, GUIs, or Bukkit service registrations.

Provides (classpath types for Hub + minigames)
  GamePlatform, HubSpawn, MatchInstanceReservation
  MinigameMechanic, MinigameBootstrap, MechanicContext
  PartyService, PartyView
  MatchQueueService, MatchEnqueueResult
  TemplateRegistry, TemplateEntry
  HubWorldApi, HubPlayerMenus, HubAdminGui
  PreMatchLimboService, IronLegacyLimboBridge, IronLegacyTemplateAdmin
  IronLegacyWorldsCatalog, IronLegacyMatchInstanceRuntime
  HubTemplatePublishLifecycle, HubPluginPolicyApi
  GameWorldScopeResolver, EnvironmentalHazardsLifecycle (SPI)
  (Teams: Hub TeamManager + FakeTeamAPI — not API TeamService)

Depends on
  Nothing. Hub and minigames depend on Foundation.

================================================================================
HOLLOWCROWNHUB — Hub world & player experience
================================================================================

Hub spawn
  Status: Implemented
  /hch sethub saves world UUID + coords. /hub teleports to hub #1.

Numbered hub instances
  Status: Implemented
  /hch sethub 2, /hub join 2. Extra spawns in config.yml hub.instances.<n>.

Hub capacity routing
  Status: Implemented
  hub.instanceCapacity (default 20). Party hub return overflows to next numbered hub.

Protection radius
  Status: Implemented
  /hch hubworld size <blocks>. Hub rules apply inside radius (square fallback).

Circle lobby zone
  Status: Implemented
  /hch hubworld lobby wand — left-click center, right-click edge (radius + height),
  Shift+scroll cycles Center/Edge tip and locks that hotbar slot while holding the wand
  (Left Alt is not sent by vanilla), particle border + action-bar only while holding wand,
  shift-right saves cylinder to hub-world.yml (overrides square check).

Build mode
  Status: Implemented
  Creative + inventory snapshot. /hch hubworld buildmode.

Hub hotbar
  Status: Implemented
  Locked slots from hub-world.yml: compass (join), party star, etc.

New player teleport
  Status: Implemented
  Optional teleportNewPlayersToHub in hub-world.yml.

Hub mob spawn toggles
  Status: Implemented
  /hch hubworld hostilemobs / passivemobs.

PvP zone
  Status: Implemented
  Wand-defined zone. Enter with sword → instant PvP. Re-enter zone re-activates PvP.
  Swap off sword → deactivate countdown. PvP pads for toggle outside zone.

Template editor return
  Status: Implemented (Unreleased)
  /hub and evacuate paths save/restore editor inventory, resume spot, and build mode.

================================================================================
HOLLOWCROWNHUB — Templates & lobbies
================================================================================

Hub template registry
  Status: Implemented
  templates.yml: key → mechanic id, display, source world, queue policy.

Template create
  Status: Implemented
  /hch template create <key> creates IL editable world + Hub row under hch-worlds/templates/<key>.

Discover dropped template folders
  Status: Implemented
  Drop a Minecraft world into hch-worlds/templates/<name> — on server start (templates.autoDiscoverOnStartup)
  or /hch template worlds discover (also list/reload/edit). Registers worlds.yml EDIT row + Hub templates.yml
  with iron-legacy mechanic. Folder needs level.dat (or region/). Edit with /hch template worlds edit <name>.

Lobby instances
  Status: Implemented
  Worlds at hch-worlds/minigames/<templateKey>-L#. Copy from source. OPEN/BROKEN states.

Lobby join GUI
  Status: Implemented
  Compass → mechanic tab → template → instance picker or auto-join oldest OPEN.
  OP: Create template world button on each mechanic tab (chat: file name + lobby name tag).

Party-aware lobby join
  Status: Implemented
  Leader moves whole party. Capacity checks. Party-too-big trim GUI (skull kick).

Lobby prefetch
  Status: Implemented
  When all OPEN lobbies ≥50% full, background prefetch creates fresh lobby.

Publish destroys lobbies
  Status: Implemented
  Publish evacuates players, destroys lobby rows, refreshes instances.

Publish gate
  Status: Implemented
  Mechanic required. Chat "yes" flow for vanilla publish confirmation.

Mechanic assignment
  Status: Implemented
  /hch template worlds mechanic <key> <mechanicId> before publish.

================================================================================
HOLLOWCROWNHUB — Pre-match limbo
================================================================================

Session per lobby world
  Status: Implemented
  Each *-L# world has own limbo session, countdown, player set.

Limbo spawn & zone
  Status: Implemented
  worlds.yml per template. Wand sets corners. /hch template limbo spawn.

Limbo loadout
  Status: Implemented
  Adventure, cleared inv. Hotbar: Match Team slot 3, Leave barrier slot 9.

Queue countdown
  Status: Implemented
  Fill timer with min-fill % from template. Cancels if fill drops below minimum.

Force start
  Status: Implemented
  /il start bypasses min fill (admin).

Teleport countdown
  Status: Implemented
  Final seconds before match launch. Low-fill cancel during teleport phase.

Match launch handoff
  Status: Implemented
  Hub calls IronLegacyLimboBridge.startGameOnPreparedInstance.

Limbo zone clear
  Status: Implemented
  After spawn-pad teleports, Hub clears blocks/entities in limbo volume.

Leave limbo
  Status: Implemented
  Leave item, /hub, failed start, evacuate → hub (exits session first).

Zone clamp
  Status: Implemented
  Non-OP players cannot leave limbo zone.

================================================================================
HOLLOWCROWNHUB — Parties (persistent social groups)
================================================================================

Create / invite / accept / decline
  Status: Implemented
  /hch party … + invite paper (left accept, right decline).

Leader kick / disband / leave
  Status: Implemented
  Invite TTL, offline disband, leader promote timers.

Party menu GUI
  Status: Implemented
  Nether star hotbar: create, members, invite, pending invites.

Party travels to lobby
  Status: Implemented
  Leader joins lobby → whole online party if capacity fits.

Party hub pull
  Status: Implemented
  Leader /hub pulls eligible online members with capacity routing.

Separate from match teams
  Status: Implemented
  Party accept does NOT auto-add to leader match team (except lobby auto-team below).

================================================================================
HOLLOWCROWNHUB — Teams (Hub-owned, FakeTeamAPI)
================================================================================

TeamManager (per-world instances)
  Status: Implemented
  Create / join / invite / leave / recolor. UUID team ids. Depends on FakeTeamAPI.

Team GUIs
  Status: Implemented
  TeamMainMenu, TeamsMenu, TeamJoinMenu, TeamInviteMenu, TeamColorMenu,
  TeamRecolorMenu, TeamAllyAndRivalMenu (moved from Iron Legacy).

/ally and /rival
  Status: Implemented
  /ally add|remove <team>, /rival add|remove <team> (replaces legacy /enemy).

Chat styling + win announce
  Status: Implemented
  Team-aware chat; Iron Legacy announces winning team and players on match end.

Team cap / colors / invites
  Status: Implemented
  Template teamSizeMax; color pickers; TeamInviteMenu; session cleanup on match end.

Party auto-team on lobby join
  Status: Implemented
  Party placed on one team up to cap when applicable.

Removed legacy stack
  Status: Removed
  API TeamService/TeamView, HubTeamService, HubAlliance*, /enemy, HubTeamMenu,
  IL TeamManager / TeamChatProvider / old IL-only team GUIs.

================================================================================
HOLLOWCROWNHUB — Nametags, TAB, chat
================================================================================

Per-viewer / team nametags
  Status: Implemented
  Team prefix and relationship colors via FakeTeamAPI / TeamManager.

Sneak hides nametag
  Status: Implemented
  When template policy shiftHidesNameTag enabled.

TAB visibility
  Status: Implemented
  Per-template tabVisible.

Chat/death formatting
  Status: Implemented
  HubNametagChatListener prefixes names consistently.

Lobby world template resolution
  Status: Implemented
  *-L# worlds resolve template policy for nametag colors.

================================================================================
HOLLOWCROWNHUB — Match queue
================================================================================

Enqueue solo or party unit
  Status: Implemented
  /hch queue join <templateKey>.

Queue policy gates
  Status: Implemented
  soloOnly, maxUnitSize, soloAutoMerge, joinQueueFirst per template.

Queue bypass
  Status: Implemented
  Permission hollowcrownhub.queue.bypass.

Queue overview GUI
  Status: Implemented
  Admin shell → Queues tab.

Auto-dequeue to limbo
  Status: GAP
  Queue does NOT yet auto-teleport when lobby slot opens. Join via GUI.

================================================================================
HOLLOWCROWNHUB — Plugin policy
================================================================================

Global scopes
  Status: Implemented
  Hub / Limbo / Match whitelist or blacklist per plugin id.

Per-template overrides
  Status: Implemented
  Match-scope plugin list overrides global for that template.

Command blocking
  Status: Implemented
  HubPluginPolicyEnforcer blocks disallowed plugin commands (best-effort).

Policy GUIs
  Status: Implemented
  Plugin Allocation landing + per-template blacklist tab.

Save on close
  Status: Implemented
  Template policy GUIs persist when inventory closed.

Kick on policy change
  Status: Implemented
  Players kicked from lobbies when policy change invalidates them.

================================================================================
HOLLOWCROWNHUB — Admin & services
================================================================================

Admin shell GUI
  Status: Implemented
  Maps, Hub templates, Queues, Lobbies, Plugin Policies.

IL template admin GUI
  Status: Implemented
  List → detail → publish/edit/snapshots/limbo tools.

Debug mechanics / why
  Status: Implemented
  /hch debug mechanics, /hch debug why <player>.

MySQL storage (optional)
  Status: Implemented
  Parties, lobbies, queues when storage.type=MYSQL.

Bukkit services registered by Hub
  GamePlatform, HubWorldApi, TemplateRegistry, PartyService,
  MatchQueueService, limbo/publish/policy APIs (teams via Hub TeamManager)
  MatchQueueService, HubPlayerMenus, HubAdminGui, IronLegacyWorldsCatalog,
  IronLegacyMatchInstanceRuntime, PreMatchLimboService,
  HubTemplatePublishLifecycle, HubPluginPolicyApi

================================================================================
HOLLOWCROWNHUB — Player GUIs (summary)
================================================================================

Join flow (compass)
  LobbyJoinMechanicMenu → LobbyJoinTemplateMenu → LobbyJoinInstanceMenu
  OP Create template on LobbyJoinTemplateMenu (file name + display name chat prompts)

Party (nether star)
  HubPartyMenu + invite/members/pending submenus + HubPartyLobbyTrimMenu

Match Team (limbo slot 3)
  Hub TeamMainMenu / TeamsMenu flow (FakeTeamAPI)

Admin
  HubAdminShellMenu, HubIlTemplateListMenu, HubIlTemplateDetailMenu,
  HubTemplateGeneralSettingsMenu, HubPluginPolicyAdminMenu, LobbyAdminMenu,
  HubQueueOverviewMenu, etc.

================================================================================
HCIRONLEGACY — Match engine
================================================================================

Match states
  Status: Implemented
  LOBBY → FREEZE → RUNNING → ENDED.

Spawn pad assignment
  Status: Implemented
  Per-template pads in worlds.yml (worlds.<key>.spawnPads); 1:1 player-to-pad at match start.
  Legacy global config.yml spawns.pads migrate on first load.

Hard freeze countdown
  Status: Implemented
  Move penalty: launch or half-heart damage.

World border
  Status: Implemented
  Multi-phase shrink from config.yml worldborder.phases (or legacy single shrink);
  per-map overrides in world-game-settings.yml; /il border set for global legacy fields.

Elimination & win
  Status: Implemented
  Optional death messages.

Saturation on spawn
  Status: Implemented
  2.5 bars on match teleport.

Join intent
  Status: Implemented
  Counted vs OP-bypass vs forced participants.

Session team cleanup on match end
  Status: Implemented (Unreleased)
  clearTeamsFor(participants) — not global clearAllTeams.

Replenish schedule
  Status: Implemented
  Tiered chest refill during match. Stops on match end.

Config hooks
  Status: Implemented
  hooks.onGameStart / onGameEnd in config.yml.

================================================================================
HCIRONLEGACY — Templates & worlds
================================================================================

worlds.yml catalog
  Status: Implemented
  Template keys, folders, EDIT/PUBLISHED, version, caps.

Editor worlds
  Status: Implemented
  Creative edit. Inventory save/restore. Resume position via Hub admin API.

Publish / snapshot / rollback
  Status: Implemented
  Versioned snapshots on publish.

Import from Hub
  Status: Implemented
  /hch template worlds importhub.

Stale instance recovery
  Status: Implemented
  On reload, cleans orphaned instance folders.

Hub template merge
  Status: Implemented
  integrations.mergeHubTemplatesIntoList: true.

Hub match provisioning
  Status: Implemented
  integrations.preferHubMatchInstanceProvisioning flag.

================================================================================
HCIRONLEGACY — Limbo bridge (IL registers, Hub calls)
================================================================================

IronLegacyLimboBridge
  Status: Implemented
  Templates, instances, game start, hub return, team assign at launch,
  Velocity routing, kill credit, limbo mob toggles.

Actual limbo UX
  Status: Implemented (owned by Hub PreMatchLimboService)

================================================================================
HCIRONLEGACY — Envoys
================================================================================

Falling crates, per-template spawns, wave/random, admin GUI tree, add-chest wand
  Status: Implemented

================================================================================
HCIRONLEGACY — Replenish
================================================================================

Tiers T1–T4, schedule, replenish wand, chest protection, admin GUI
  Shared loot + schedule: plugins/HCIronLegacy/replenish.yml
  Per-map chest coords: world-envoys/<key>/replenish-locations.yml (migrates from legacy scoped replenish.yml)
  Wand place: chest front faces the player (vanilla-style)
  Wand break: removes chest block + spawn data; break without wand blocked (including OP)
  Status: Implemented

================================================================================
HCIRONLEGACY — Rules & spawns
================================================================================

Build/dig whitelist/blacklist, crafting toggle, spawn pad commands, pickup guard,
random teams admin command
  Status: Implemented

================================================================================
HCIRONLEGACY — Teams (Hub-owned)
================================================================================

Uses Hub TeamManager / FakeTeamAPI
  Status: Implemented
  Match win announces winning team + players. No separate IL TeamManager.

================================================================================
HCIRONLEGACY — Multi-server & admin
================================================================================

Server roles (standalone, hub, game, edit)
  Status: Implemented

Lobby shards + boundary listener
  Status: Implemented

Kill leaderboard + sidebar
  Status: Implemented

Resource pack (optional)
  Status: Implemented

Admin history audit log
  Status: Implemented

Bukkit services registered by IL
  IronLegacyLimboBridge, IronLegacyTemplateAdmin, IronLegacyHubBridge,
  GameWorldScopeResolver, TeamChatProvider, MinigameMechanic (iron-legacy)

================================================================================
CROSS-PLUGIN — Who owns what
================================================================================

Hub owns
  Hub spawn, protection, hotbar, PvP zone, build mode
  Template registry, lobby instances (*-L#), join GUIs
  Pre-match limbo queue, countdown, limbo loadout, zone clamp
  Parties, match teams, alliances, nametags/TAB/chat colors
  Plugin policy, match queue (enqueue), worlds.yml limbo sections (via catalog)

Iron Legacy owns
  worlds.yml template catalog, publish/snapshots, editor worlds (logic)
  Match engine: freeze, pads, border, elimination, win
  Envoys, replenish, build/dig rules, spawn pads
  IronLegacyLimboBridge implementation (Hub drives limbo UX)

Foundation owns
  Shared API types only

================================================================================
KNOWN GAPS
================================================================================

Queue → limbo auto-dequeue
  Expected: queue sends units when lobby slot opens
  Current: enqueue + status only; join via compass GUI

HubPluginPolicyApi
  API for external HC plugins; no in-game test surface

Velocity multi-server
  Routing exists; single-server stack is primary test target
