# Hollow Crown Platform — full in-game test

Use this on a **Paper 1.21.4** test server with **two accounts** (one OP, one normal player) when possible.

Mark each line: ✅ pass · ❌ fail · ⚠️ skipped · ⬜ not tested

## How to read the **Code path** column

Each row traces: **player action → Java entry point → services → disk/API/cross-plugin**.

| Segment | Meaning |
|---------|---------|
| `→` | Call order |
| `· PERSIST:` | YAML/SQL/world folders |
| `· PERM:` | Bukkit permission |
| `· API:` | `api/` SPI contract |
| `· IL/HUB:` | Other plugin in the chain |

### Worked example — `/hch template list` (Phase 1.2 #6)

`hub/plugin.yml` (`/hch`) → `HollowCrownHubPlugin.onEnable:221-224` → `HubCommandExecutor.onCommand:58-59` (`template`) → `HubTemplateCommands.handle:30` (`hollowcrownhub.admin`) → `list:65` → `HubTemplateRegistry.list:49-50` · PERSIST: `plugins/HollowCrownHub/templates.yml` (`loadFromDisk:165-200` on boot) · API: `HubTemplateApiRegistry` → Bukkit `TemplateRegistry` (`HollowCrownHubPlugin:148-152`) · IL/HUB: `HungerGamesPlugin` reads `TemplateRegistry` for join GUI / compass

---

| Abbrev | Module | Deploy JAR |
|--------|--------|------------|
| `foundation/` | HC-Foundation | `foundation/target/HC-Foundation-*.jar` |
| `api/` | hollowcrown-api (not deployed) | — |
| `hub/` | HollowCrownHub | `hub/target/HollowCrownHub-*.jar` |
| `iron-legacy/` | HCIronLegacy | `minigames/iron-legacy/target/HCIronLegacy-*.jar` |

**Known gap:** queue enqueues only — no auto limbo/match (`hub/queue/HubMatchQueueService.java`).

---

## Command migration (deprecated `/il` → current)

| Old | Use now | Code path |
|-----|---------|-----------|
| `/il setlobby` | `/hch sethub` | `HubCommandExecutor:100-121` → `DefaultGamePlatform.setHubSpawn` |
| `/il lobby` / `/il hub` | `/hub` | `hub/plugin.yml` `/hub` → `HubPlayerCommand.onCommand:36` |
| `/il buildmode` | `/hch hubworld buildmode` | `HubWorldAdminCommands` → `HubWorldManager` buildMode set |
| `/il pvp pad` | `/hch hubworld pvp pad` | `HubWorldAdminCommands` → `HubWorldManager` pad item |
| `/il create` | `/hch template create` | `HubTemplateCommands.createWorld` → `HubTemplateWorldBootstrap` |
| Limbo wand | `/hch template limbo wand` | `HubTemplateLimboCommands` → `HubLimboWandListener` |
| `/hch ironlegacy` | removed | `HubCommandExecutor:136-140` stub message |
| `/il game` | removed | `HGCommand:400-404` stub → use Hub `template worlds` |

## Phase 0 — Server setup (before joining)

### 0.1 Deploy JARs

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | Deploy HC-Foundation JAR | STARTUP enable | foundation/plugin.yml load:STARTUP → HollowCrownApiBootstrap.onEnable:11 · PERSIST: — · API: shades api/ · IL/HUB: Hub+IL depend on HC-Foundation |
| 2 | Deploy HollowCrownHub JAR | POSTWORLD enable | hub/plugin.yml → HollowCrownHubPlugin.onEnable:84 · PERSIST: plugins/HollowCrownHub/ · API: registers GamePlatform, PartyService, etc. :136-201 |
| 3 | Deploy HCIronLegacy JAR | POSTWORLD enable | iron-legacy/plugin.yml → HungerGamesPlugin.onEnable:118 → MinigameBootstrap → IronLegacyMechanic · IL/HUB: depend Foundation+Hub |

- ⬜ Subsection complete

### 0.2 Console on startup

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | HC-Foundation log | schema=2 | HollowCrownApiBootstrap.onEnable:12 → HollowCrownApiVersion.SCHEMA (api/) |
| 2 | Hub enable log | storage YAML/MYSQL | HollowCrownHubPlugin.onEnable:88-94 HubSqlRuntime → HubLog action=hub_enable:234 |
| 3 | IL mechanic log | id=iron-legacy | DefaultGamePlatform.registerMechanic:71-77 ← IronLegacyMechanic (iron-legacy/integration/) |
| 4 | No IL disabled | clean load | HungerGamesPlugin stack assert — plugin.yml depend chain |
| 5 | /plugins green | 3 plugins | Bukkit load order: Foundation STARTUP before Hub/IL POSTWORLD |

- ⬜ Subsection complete

### 0.3 Fail-fast

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | IL disables | missing dep | MinigameBootstrap.assertRunnableStack in HungerGamesPlugin.onEnable |
| 2 | LimboBridge warning | Hub warns | HollowCrownHubPlugin.onEnable:204-210 — no IronLegacyLimboBridge service |
| 3 | MYSQL SEVERE | YAML fallback | HubSqlRuntime.tryConnect null → HollowCrownHubPlugin:92-93 |

- ⬜ Subsection complete

### 0.4 Environment prep

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | Hub world exists | world loaded | Server setup — UUID must match config.yml hub.spawn.world-uuid |
| 2 | Source world folder | on disk | HubLobbyService.createLobby:109-116 WorldFolderCopier — templates.yml sourceWorldName |
| 3 | OP perms | admin works | hub/plugin.yml hollowcrownhub.admin · iron-legacy/plugin.yml hcironlegacy.admin |
| 4 | Non-OP player | protection on | HubWorldManager checks isOp() for build/PvP admin bypass |

- ⬜ Subsection complete

## Phase 1 — Hub bootstrap (OP)

### 1.1 Core hub setup

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | /hch help | command tree | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand:36-53 |
| 2 | /hch version | version + schema 2 | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand:94-98 → GamePlatform.apiSchema() |
| 3 | /hch sethub | spawn saved | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand:100-121 → DefaultGamePlatform.setHubSpawn → config.yml hub.spawn · PERM: hollowcrownhub.admin · API: HubSpawn |
| 4 | /hch debug mechanics | lists iron-legacy | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand:73-88 → DefaultGamePlatform.registeredMechanicIdStrings · PERM: hollowcrownhub.admin |
| 5 | /hch gui help | GUI help text | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand:55-56 → HubGuiCommands.handle |

- ⬜ Subsection complete

### 1.2 Template registration

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 6 | /hch template list | lists templates | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand:58-59 → HubTemplateCommands.handle:30 → list:65 → HubTemplateRegistry.list:49-50 · PERSIST: templates.yml loadFromDisk:165-200 · PERM: hollowcrownhub.admin · API: HubTemplateApiRegistry→TemplateRegistry · IL/HUB: IL join lists |
| 7 | /hch template set testmap iron-legacy "Test Map" | row created | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand template → HubTemplateCommands.hubMechanicTemplateSet:169 → HubTemplateRegistry.upsert:60-78 saveToDisk:203 · API: getMechanic warn :183 |
| 8 | /hch template setsource testmap <folder> | source set | HubTemplateCommands.setSource:203 → HubTemplateRegistry.setSourceWorld:81-90 · PERSIST: templates.yml · used HubLobbyService.createLobby |
| 9 | /hch template get testmap | row details | HubTemplateCommands.get:131-151 → HubTemplateRegistry.get:53-57 → HubTemplateEntry |
| 10 | /hch template setqueue maxUnitSize 4 | policy saved | HubTemplateCommands.setQueue:218 maxunitsize → HubTemplateRegistry.updateQueuePolicy:123-145 · consumed HubMatchQueueService.enqueue |
| 11 | /hch template setqueue soloOnly false | saved | setQueue soloOnly branch :234-236 → updateQueuePolicy |
| 12 | /hch template setqueue soloAutoMerge true | saved | setQueue soloautomerge :238-240 |
| 13 | /hch template setqueue joinQueueFirst false | saved | setQueue joinqueuefirst :242-244 · read LobbyJoinMenuListener:87 |

- ⬜ Subsection complete

### 1.3 Hub world config

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 14 | /hch hubworld size 64 | radius saved | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand hubworld → HubWorldAdminCommands → HubWorldYaml · PERSIST: hub-world.yml · HubWorldManager.onMove:1031 clamp |
| 15 | /hch hubworld hostilemobs false | hostile off | HubWorldAdminCommands → hub-world.yml mobSpawns.hostile → HubWorldManager.onMobSpawn:1119 |
| 16 | /hch hubworld passivemobs false | passive off | same passive branch → onMobSpawn:1119 |

- ⬜ Subsection complete

### 1.4 Scaffold template

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 17 | /hch template create testmap2 | editor world | HubTemplateCommands.createWorld:81 → HubTemplateWorldBootstrap.createIronLegacyEditableWorld · PERSIST: hch-worlds/templates/<key> + templates.yml |

- ⬜ Subsection complete

## Phase 2 — Hub player flow (normal player)

### 2.1 Hub teleport & first join

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | /hub | teleport + loadout | hub/plugin.yml `/hub` → HubPlayerCommand.onCommand:36 → DefaultGamePlatform.getHubSpawn:46 → teleportAsync:103 → HubWorldManager.applySurvivalHubLoadoutAfterTemplate · PERM: hollowcrownhub.use · PERSIST: config.yml hub.spawn |
| 2 | Check hotbar | compass/party/sword | HubWorldManager applyHubLoadout — reads hub-world.yml compassSlot/items.hub.party/pvpSwordMaterial |
| 3 | New player join | auto hub TP | HubWorldManager.onJoin:790 — if teleportNewPlayersToHub + spawn set → teleport + loadout |

- ⬜ Subsection complete

### 2.2 Join GUI & lobbies

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 4 | /hch join | join GUI | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand:64-65 → HubJoinCommands.handle:21 → LobbyJoinTemplateMenu.open · PERM: hollowcrownhub.join |
| 5 | Right-click compass | same GUI | HubWorldManager.onHubCompassUse:1145 → HubPlayerMenus.openJoinTemplateMenu (DefaultHubPlayerMenus) → LobbyJoinTemplateMenu |
| 6 | Left-click testmap | join/create lobby | LobbyJoinMenuListener.handleTemplateClick jtpl_pick:78 → joinQueueFirst check :87 → HubLobbyService.joinOldestOpenForTemplate · PERSIST: hch-worlds/minigames/<key>-L# |
| 7 | Right-click testmap | instance picker | LobbyJoinMenuListener:84-85 → LobbyJoinInstanceMenu.open |
| 8 | Click OPEN instance | teleport L# | LobbyJoinMenuListener.handleInstanceClick → HubLobbyService.teleportPlayerToInstance |
| 9 | Leave Lobby bed | hub return | LobbyJoinMenuListener leave action → HubPreMatchLimboService.leaveLimbo → HubPlayerCommand spawn |
| 10 | GUI pagination | prev/next | LobbyJoinTemplateMenu / LobbyJoinInstanceMenu nav items jtpl_prev/jtpl_next PDC keys |
| 11 | OP template lore | key + mechanic | LobbyJoinTemplateMenu.build — adds lore if player.isOp() |

- ⬜ Subsection complete

### 2.3 Party system

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 12 | /hch party create | party UUID | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand party → HubPartyCommands → HubPartyService.createParty · PERSIST: parties.yml · API: PartyService |
| 13 | /hch party invite | invite paper | HubPartyService.invite → HubPartyListener gives paper PDC partyInviteKey |
| 14 | Left-click invite paper | accept | HubPartyListener.onInteract → HubPartyService.acceptInvite |
| 15 | /hch party accept <id> | accept cmd | HubPartyCommands.accept → HubPartyService.acceptInvite |
| 16 | Leader title | joined notify | HubPartyService.acceptInvite — Title to leader |
| 17 | Right-click party item | party GUI | HubWorldManager locked item interact → DefaultHubPlayerMenus.openPartyMenu → HubPartyMenu |
| 18 | GUI Invite Players | heads paged | HubPartyMenu submenu — excludes self/members |
| 19 | GUI Party Members | roster | HubPartyMenu members view |
| 20 | GUI Pending Invites | papers | HubPartyMenu invites tab |
| 21 | /hch party decline | leader notified | HubPartyService.declineInvite |
| 22 | Accept one auto-declines others | messages | HubPartyService.acceptInvite — clears other invites for target |
| 23 | Invite expires 5min | TTL | HubPartyService tickMaintenance inviteTtlMs :66-67 from config party.inviteTtlSeconds |
| 24 | Leader /hub pulls party | all TP | HubPlayerCommand:60-99 party leader branch → teleportAsync each member |
| 25 | /hch party kick | kicked | HubPartyCommands.kick → HubPartyService.kickMember |
| 26 | /hch party leave | leave/promote | HubPartyService.leaveParty — leader promotion logic |
| 27 | /hch party disband | disbanded | HubPartyCommands.disband → HubPartyService.disband |

- ⬜ Subsection complete

### 2.4 Match queue

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 28 | /hch queue join testmap | queued position | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand queue → HubQueueCommands → HubMatchQueueService.enqueue · PERM: hollowcrownhub.queue · API: MatchEnqueueResult |
| 29 | /hch queue status | queue dump | HubQueueCommands.status → HubMatchQueueService.listUnits |
| 30 | /hch queue leave | left queue | HubMatchQueueService.leaveQueue |
| 31 | Party leader queues | whole unit | HubMatchQueueService.enqueue — party locked unit from PartyService |
| 32 | Non-leader queue | NOT_PARTY_LEADER | HubMatchQueueService.enqueue leader check · API: MatchEnqueueResult.Status |
| 33 | Offline member queue | OFFLINE_PARTY_MEMBER | enqueue validates online members |
| 34 | soloOnly deny | SOLO_ONLY | HubTemplateEntry.queueSoloOnly + HubMatchQueueService · bypass hollowcrownhub.queue.bypass |
| 35 | Party too large | PARTY_TOO_LARGE | template queueMaxUnitSize vs party size |
| 36 | OP bypass | gates skipped | HubQueueCommands checks hollowcrownhub.queue.bypass |
| 37 | Disconnect while queued | removed | HubPartyListener.onQuit → HubMatchQueueService.leaveQueue |
| 38 | Party leave queue notify | title+actionbar | HubMatchQueueService.leaveQueue → MatchEnqueueMessages |

- ⬜ Subsection complete

### 2.5 joinQueueFirst

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 39 | Set joinQueueFirst true | saved | HubTemplateRegistry.updateQueuePolicy joinQueueFirst field |
| 40 | Left-click enqueues | not lobby | LobbyJoinMenuListener:87-100 queueFirst branch → matchQueueService.enqueue |

- ⬜ Subsection complete

### 2.6 IL from hub

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 41 | /il help | help text | iron-legacy/plugin.yml `/il` → HungerGamesPlugin.onEnable:228 → HGCommand.onCommand:129 help branch |
| 42 | /il party | Hub party menu | HGCommand party:301 → Bukkit HubPlayerMenus.openPartyMenu |
| 43 | /il leaderboard | top 10 kills | HGCommand lb:312 → KillLeaderboardManager |

- ⬜ Subsection complete

## Phase 3 — Hub lobby admin (OP)

### 3.1 CLI lobby

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | /hch lobby list | OPEN instances | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand lobby → HubLobbyCommands.list → HubLobbyInstanceRegistry.all |
| 2 | /hch lobby create testmap | testmap-L# loads | HubLobbyCommands.create → HubLobbyService.createLobby:92 → WorldFolderCopier · PERSIST: lobby-instances.yml |
| 3 | Console lobby ready | log line | HubLobbyService → HubLog action=lobby_create · IL/HUB: MinigameMechanic.onLobbyInstanceCreated |
| 4 | Player joins instance | teleport | HubLobbyService.joinOldestOpenForTemplate / joinInstance |
| 5 | /hch lobby tp <id> | OP TP | HubLobbyCommands.tp |
| 6 | /hch debug why <player> | snapshot | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand debug why → HubDebugWhy.handle — party/queue/lobby/world |

- ⬜ Subsection complete

### 3.2 Lobby admin GUI

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 7 | /hch lobby gui | chest GUI | HubLobbyCommands.gui → LobbyAdminMenu.open |
| 8 | Click lime OPEN | TP instance | LobbyAdminMenuListener — action TP via lobbyAdminActionKey PDC |
| 9 | Shift+click destroy | destroy | LobbyAdminMenuListener shift → HubLobbyService.destroyLobby |
| 10 | /hch gui lobby | same GUI | HubGuiCommands → DefaultHubAdminGui.openLobby |

- ⬜ Subsection complete

### 3.3 Destroy & BROKEN

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 11 | /hch lobby destroy | unload+delete | HubLobbyService.destroyLobby — evacuate, end limbo, unload world |
| 12 | Destroy fails | BROKEN state | HubLobbyService marks LobbyInstanceState.BROKEN on IO failure |
| 13 | /hch lobby forget | registry row gone | HubLobbyCommands.forget → HubLobbyInstanceRegistry.remove |
| 14 | BROKEN GUI barrier | forget | LobbyAdminMenuListener barrier action |

- ⬜ Subsection complete

### 3.4 Prefetch

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 15 | Last lobby starts match | next L# pre-created | HubLobbyService.onMatchStarted / HubPreMatchLimboService.launchSession → prefetch createLobby |
| 16 | lobby list after start | new OPEN | HubLobbyInstanceRegistry |

- ⬜ Subsection complete

### 3.5 Publish flow

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 17 | Players in L# | inside lobby | HubLobbyService.joinInstance |
| 18 | /hch template worlds publish | destroys lobbies | HubTemplateWorldsCommands → IronLegacyTemplateAdminImpl.publish → HubLobbyService.destroyLobbiesForTemplate |
| 19 | Players notified | rejoin message | HubLobbyService.destroyLobby evacuate message |
| 20 | Fresh lobby prefetch | new OPEN | HubLobbyService.createLobby after publish |

- ⬜ Subsection complete

## Phase 4 — Iron Legacy integration (limbo → match)

### 4.1 IL worlds admin via Hub

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | /hch template worlds help | docs | hub/plugin.yml `/hch` → HollowCrownHubPlugin.onEnable:221-224 → HubCommandExecutor.onCommand template worlds → HubTemplateWorldsCommands.help |
| 2 | /hch template worlds list | worlds.yml rows | HubTemplateWorldsCommands.list → IronLegacyTemplateAdminImpl / TemplateWorldManager |
| 3 | importhub --dry-run | preview | IronLegacyTemplateAdminImpl.importFromHub dry-run |
| 4 | importhub | import rows | IronLegacyTemplateAdminImpl.importFromHub · PERSIST: plugins/HCIronLegacy/worlds.yml |
| 5 | worlds reload | IL reload | HubTemplateWorldsCommands.reload → ConfigFilesManager.reloadAll |
| 6 | /hch gui maps | IL browser | HubGuiCommands → HubIlTemplateListMenu (DefaultHubAdminGui) |
| 7 | Click template detail | detail GUI | HubIlTemplateDetailMenu |
| 8 | /hch template worlds tp | creative editor | IronLegacyTemplateAdminImpl.teleportToEditor → TemplateWorldManager |
| 9 | set display | IL display | HubTemplateCommands routeTemplateSet → HubIlWorldSetCommand.applyWorldFields |
| 10 | set maxplayers | saved | HubIlWorldSetCommand → WorldSettingsManager |
| 11 | set teamsize | saved | HubIlWorldSetCommand |
| 12 | set minimumpercentage | limbo fill % | HubIlWorldSetCommand → worlds.yml · read HubPreMatchLimboService fill gate |
| 13 | set gamerule | saved | HubIlWorldSetCommand gamerule branch |
| 14 | set enabled true | join GUI | HubIlWorldSetCommand enabled field |
| 15 | worlds toggle | flip enabled | IronLegacyTemplateAdminImpl.toggle |
| 16 | worlds snapshots | list IDs | IronLegacyTemplateAdminImpl.listSnapshots → TemplateWorldManager |
| 17 | worlds rollback | rollback | TemplateWorldManager.rollback |
| 18 | iconpreview | preview chest | HubIlIconPreviewMenu + IconManager |

- ⬜ Subsection complete

### 4.2 Limbo zone setup

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 19 | /hch template limbo wand | wand item | HubTemplateLimboCommands.wand → gives stick PDC limboWandTemplateKey |
| 20 | Left-click corner 1 | saved | HubLimboWandListener left click → HubLimboLayout setCorner1 |
| 21 | Right-click corner 2 | saved | HubLimboWandListener right click → setCorner2 |
| 22 | Shift-right confirm | outline | HubLimboWandListener confirm → HubLimboWorldsYaml write IL worlds.yml limboZone |
| 23 | Outline updates | particles | HubLimboWandListener particle task while holding wand |
| 24 | limbo spawn in zone | spawn saved | HubTemplateLimboCommands.spawn → HubLimboWorldsYaml limbo spawn |
| 25 | spawn outside zone | rejected | HubTemplateLimboCommands zone contains check |
| 26 | wand in testmap-L# | auto template key | HubLimboWandListener resolveTemplateFromWorld — HubIlTemplateDetailMenu logic |

- ⬜ Subsection complete

### 4.3 Pre-match limbo

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 27 | Join via compass | limbo loadout | HubLobbyService.join → HubPreMatchLimboService.registerHubLobbyJoin → HubWorldManager limbo loadout |
| 28 | Limbo hotbar items | sword/compass/party/barrier | hub-world.yml items.limbo + items.hub — HubWorldManager.applyLimboLoadout |
| 29 | Walk outside zone | clamped | HubPreMatchLimboArenaListener.onMove / HubPreMatchLimboService.onMoveClamp:60-74 |
| 30 | Action bar fill | N/max Players | HubPreMatchLimboService tickFill actionbar |
| 31 | Below minimumpercentage | no auto start | HubPreMatchLimboService fill gate + IronLegacyLimboBridge minimumpercentage |
| 32 | Fill threshold | 5min countdown | HubPreMatchLimboService queue countdown timer |
| 33 | 10s teleport countdown | titles | HubPreMatchLimboService.tickTeleportCountdown |
| 34 | Barrier leave | hub return | HubPreMatchLimboArenaListener barrier interact → leaveLimbo:451 |
| 35 | Picker red bed leave | hub return | LobbyJoinMenuListener leave lobby bed action |
| 36 | /il hostilemobspawn false | no hostiles | HGCommand:326 → IronLegacyLimboBridgeImpl.setLimboHostileMobSpawns → HubPreMatchLimboArenaListener.onMobSpawn |
| 37 | /il passivemobspawn false | no passives | HGCommand:351 → passive mob toggle |

- ⬜ Subsection complete

### 4.4 Match start & stop

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 38 | /il start in limbo | countdown→match | HGCommand:437 → PreMatchLimboService.forceBeginTeleportCountdown → HubPreMatchLimboService → IronLegacyLimboBridge.launchSession |
| 39 | Match instance world | testmap-M# | IronLegacyLimboBridgeImpl → HubIronLegacyMatchInstanceRuntime.createMatchInstance → DefaultGamePlatform.createMatchWorld |
| 40 | Lobby registry detach | row released | HubLobbyService.releaseForMatch → MinigameMechanic.onLobbyInstanceReleased |
| 41 | /il stop | abort to hub | HGCommand:458 → GameManager.stopGame + PreMatchLimboService.abort |
| 42 | /il joingame | OP bypass | HGCommand:376 joingame branch |

- ⬜ Subsection complete

### 4.5 Admin shell GUIs

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 43 | /hch gui admin | admin home | HubGuiCommands → HubAdminShellMenu |
| 44 | Templates browser | templates.yml read-only | HubHubTemplateBrowserMenu |
| 45 | Queues overview | live depth | HubQueueOverviewMenu → HubMatchQueueService |
| 46 | /il admin | IL control center | HGCommand:289 → AdminControlCenterMenu |

- ⬜ Subsection complete

## Phase 5 — Hub world rules & PvP

### 5.1 Hub protection (non-OP)

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | Break block | blocked | HollowCrownHubPlugin:132 registerEvents HubWorldManager → onBreak:925 — isInHubProtection + !buildMode → cancel |
| 2 | Place block | blocked | onPlace:936 |
| 3 | Crafting | blocked | onCraft:950 PrepareItemCraftEvent cancel |
| 4 | Drop locked item | blocked | onDrop:968 — isLockedUtilityItem |
| 5 | Swap locked slots | blocked | onSwapHands:979 / onInventoryClick:999 |
| 6 | Outside radius | clamped X/Z | onMove:1031 — hub-world.yml protection.radiusBlocks |
| 7 | Time/weather | day/no storm | HubWorldManager scheduled task — setTime/storm off in protection |
| 8 | Mob spawn toggles | respect settings | onMobSpawn:1119 — hub-world.yml mobSpawns.hostile/passive |

- ⬜ Subsection complete

### 5.2 OP build mode

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 9 | /hch hubworld buildmode true | creative+snapshot | HubWorldAdminCommands → HubWorldManager.enableBuildMode — buildModeSnapshots:78 |
| 10 | Build freely | allowed | onBreak/onPlace skip when buildMode contains player |
| 11 | buildmode false | survival+loadout | HubWorldManager.disableBuildMode → applySurvivalHubLoadoutAfterTemplate |
| 12 | Toggle on again | restore build inv | enableBuildMode restores buildModeSnapshots |
| 13 | /hub after limbo | build mode restored | HubPlayerCommand teleport callback → applySurvivalHubLoadoutAfterTemplate checks buildMode |

- ⬜ Subsection complete

### 5.3 Hub opt-in PvP

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 14 | Hold sword 10 ticks | PVP activated title | HubWorldManager startPvpStateTask — activateCountdown map :75 → pvpActive :74 |
| 15 | Release sword | deactivate countdown | deactivateCountdown :76 |
| 16 | PvP on vs on | damage | onEntityDamageByPlayer:961 — both pvpActive |
| 17 | PvP on vs off | no damage | onEntityDamageByPlayer cancel if target not pvpActive |
| 18 | Kill credit | leaderboard++ | onDeath hub PvP → ServicesManager IronLegacyHubBridge.recordKill → KillLeaderboardManager |
| 19 | Die keep inv | keep inventory | onDeath:883 keepInventory keepLevel |
| 20 | 3s invuln ring | yellow particles | pvpInvulnerableUntil :80 + particle task startInvulnerabilityParticleTask |
| 21 | Invuln no damage | no damage dealt/received | HubWorldManager.onDamage:840 — pvpInvulnerableUntil check → cancel |
| 22 | PvP off outside zone | no TP | deactivatePvp — only TP if inPvpZone or has pad |
| 23 | PvP off in zone/pad | TP hub spawn | deactivatePvp teleport hub spawn branch |

- ⬜ Subsection complete

### 5.4 PvP zone admin

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 24 | /hch hubworld pvp wand | blaze rod | HubWorldAdminCommands pvp wand → PDC pvpZoneWandKey |
| 25 | Set corners | hub-world.yml | HubPvpZoneWandListener → HubWorldYaml pvpZone.pos1/pos2 |
| 26 | Outline while holding wand | particles | HubPvpZoneWandListener outline task — recent changelog behavior |
| 27 | Wand outside protection | rejected | HubPvpZoneWandListener isInHubProtection check |
| 28 | /hch hubworld pvp setspawn | zone respawn | HubWorldAdminCommands → hub-world.yml pvpZoneSpawn |
| 29 | Enter zone | auto PvP+sword slot | HubWorldManager inPvpZone check in move/interact handlers |
| 30 | Die in zone | respawn priority | onRespawn:814 — zone spawn > pad > hub spawn |

- ⬜ Subsection complete

### 5.5 PvP pads

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 31 | /hch hubworld pvp pad | pressure plate item | HubWorldAdminCommands pvp pad → pvpPadItemKey PDC |
| 32 | OP places pad | registered | onPlace pad item → hub-world.yml pvpPads list |
| 33 | Step pad PvP ON | respawn activated | onPvpPressurePlate:1082 — pvpRespawnPads :81 |
| 34 | Repeat step | no spam | lastPvpPlateActivationMs dedupe :82 |
| 35 | Re-activate PvP step pad | message again | pvpActive transition allows new activation |
| 36 | Step pad PvP OFF | not activated hint | onPvpPressurePlate inactive branch |
| 37 | OFF warning cooldown | 5min | lastPvpPadInactivePromptAt :83 |
| 38 | Die with pad | respawn at pad | onRespawn uses pvpRespawnPads |

- ⬜ Subsection complete

### 5.6 Limbo PvP

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 39 | Sword activation limbo | same countdown | HubPreMatchLimboArenaListener — delegates HubWorldManager PvP state or parallel limbo PvP handlers |
| 40 | PvP off limbo no TP | stays put | HubPreMatchLimboArenaListener deactivate — no hub teleport |
| 41 | PvP off keeps slot | no forced swap | limbo arena listener slot policy |

- ⬜ Subsection complete

## Phase 6 — Iron Legacy admin & template editing

### 6.1 Template editor

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | /hch template worlds tp testmap | creative editor | HubTemplateWorldsCommands → IronLegacyTemplateAdminImpl.teleportToEditor → TemplateWorldManager.teleportEditor · PERM: hollowcrownhub.admin |
| 2 | Editor world rules | time 1000 no cycles | TemplateWorldManager.applyEditorWorldRules DO_DAYLIGHT_CYCLE/DO_WEATHER_CYCLE false |
| 3 | Editor inventory memory | restored on re-enter | TemplateWorldManager.saveEditorInventoryIfTemplate / restoreEditorInventory in-memory map |
| 4 | Publish template | gamerules applied | IronLegacyTemplateAdminImpl.publish → TemplateWorldManager.publishTemplate applyPublishRulesToWorld · PERSIST: worlds.yml |
| 5 | Delete with players | fails safe | TemplateWorldManager.deleteTemplate players.isEmpty guard |
| 6 | Delete empty | removed disk+yml | TemplateWorldManager.deleteTemplate unload deleteWorldFolder worldsCfg |

- ⬜ Subsection complete

### 6.2 Spawn pads

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 7 | /il addspawn | pad at feet | iron-legacy/plugin.yml `/il` → HungerGamesPlugin.onEnable:228 → HGCommand.onCommand:465 → SpawnManager.add · PERSIST: config.yml spawns.pads |
| 8 | /il listspawns | count | HGCommand:480 → SpawnManager.getSpawnPads().size() |
| 9 | /il showpads 10 | particles | HGCommand:493 BukkitRunnable Particle.DUST/END_ROD |
| 10 | /il addspawnring 8 20 here | ring | HGCommand:481 → SpawnManager.addRing |
| 11 | /il removespawn | nearest removed | HGCommand:473 → SpawnManager.removeNearest |
| 12 | /il clearspawns | all cleared | HGCommand:472 → SpawnManager.clear |

- ⬜ Subsection complete

### 6.3 Game rules & toggles

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 13 | /il rules | summary | HGCommand:542 → BuildRuleManager.summaryLines |
| 14 | /il crafting on/off | toggle | HGCommand:548 → BuildRuleManager.setCraftingEnabled → RulesListener.onPrepareItemCraft:85 |
| 15 | /il build * | whitelist | HGCommand:557 → BuildRuleManager → RulesListener place checks |
| 16 | /il dig * | whitelist | HGCommand:595 → BuildRuleManager dig → RulesListener break checks |
| 17 | /il set teamsize 2 | updated | HGCommand:646 → config game.teamSize saveConfig |
| 18 | /il set countdown 10 | updated | HGCommand:652 → config game.countdownSeconds |
| 19 | /il border set ... | configured | HGCommand:678 → config worldborder.* → GameManager.scheduleBorderShrink |
| 20 | /il toggle deathmsgs | toggle | HGCommand:705 → game.suppressDeathMessages → GameManager.onDeath |
| 21 | /il toggle cannon | toggle | HGCommand:712 → game.playCannonOnDeath |
| 22 | /il toggle defaultteams | toggle | HGCommand:719 → ui.openTeamsFirst + GameManager.assignAutoTeamsIfNeeded |
| 23 | /il toggle launchonmove | toggle | HGCommand:726 → game.launchOnMove → GameManager.onMove:648 |
| 24 | /il reload | reload all | HGCommand:405 → ConfigFilesManager.reloadAll SpawnManager BuildRuleManager TemplateWorldManager |

- ⬜ Subsection complete

### 6.4 IL admin GUIs

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 25 | /il gui | main/teams | HGCommand:254 → MainMenu or TeamsMenu per ui.openTeamsFirst |
| 26 | /il admin | control center | HGCommand:289 → AdminControlCenterMenu |
| 27 | Core Settings | persist | CoreSettingsAdminMenu.onClick → saveConfig |
| 28 | Game Worlds settings | per-map | GameWorldsListMenu → GameWorldSettingsMenu → WorldSettingsManager.save · PERSIST: world-game-settings.yml |
| 29 | Worlds Hub Settings | hub integration | WorldsHubSettingsMenu → IronLegacyLimboBridgeImpl mob toggles |
| 30 | Team Name Database | CRUD | TeamNameDatabaseAdminMenu → TeamNameDatabaseManager · PERSIST: Hub teams.yml |
| 31 | Back navigation | parent menu | MenuListener.onClick → MenuView back handlers |

- ⬜ Subsection complete

## Phase 7 — Envoy & Replenish (per-map scoped)

### 7.1 Access & scope

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | /il envoy gui in hub | fails/warns | HGCommand:195 → TemplateWorldManager.resolveTemplateKeyForWorld empty → error message |
| 2 | /il envoy gui in template | map key in title | HGCommand:200 → EnvoyAdminMenu(templateKey) |
| 3 | Replenish Admin scope | template world required | AdminControlCenterMenu:129 resolveTemplateKeyForWorld → ReplenishAdminMenu |
| 4 | Four tabs | Spawns Types Replenish General | EnvoyAdminMenu.build slots 10/12/14/16 |

- ⬜ Subsection complete

### 7.2 Envoy setup

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 5 | Add spawn GUI/wand | scoped envoys.yml | EnvoySpawnsMenu → AddChestModeManager → EnvoyManager.addLocation · PERSIST: world-envoys/<key>/envoys.yml via WorldEnvoyStorage |
| 6 | Remove spawn GUI | correct coords | EnvoySpawnsMenu → EnvoyManager.removeNearest |
| 7 | Types & packages | scoped yaml | EnvoyTypesMenu EnvoyPackagesMenu EnvoyPackageEditMenu → EnvoyManager.saveYamlTemplate |
| 8 | General settings | persist | EnvoyGeneralSettingsMenu → scoped envoys.yml timing/visuals |
| 9 | /il envoy addloc | adds loc | HGCommand:151 → EnvoyManager.addLocation |
| 10 | /il envoy listlocs | lists | HGCommand:167 → EnvoyManager.getSpawnLocations |
| 11 | /il envoy clearlocs | clears | HGCommand:159 → EnvoyManager.clearLocations |
| 12 | /il envoy wave | without ticker | HGCommand:175 → EnvoyManager.spawnWave |
| 13 | /il envoy random | random drops | HGCommand:185 → EnvoyManager.spawnRandomForagerDrops |
| 14 | /il addchests give+wand | placement mode | HGCommand:214 → AddChestModeManager |

- ⬜ Subsection complete

### 7.3 Replenish setup

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 15 | Replenish wand | scoped replenish.yml | ReplenishModeManager → ReplenishManager.add → WorldEnvoyStorage.replenishPath |
| 16 | Remove chest GUI | correct world | ReplenishChestsMenu → ReplenishManager.removeNearest |
| 17 | Tier loot T1-T4 | persist | replenish.yml tiers.* — ReplenishManager.fillChest reads |
| 18 | General interval/delay | persist | EnvoyGeneralSettingsMenu replenish schedule fields |
| 19 | Toggles forager/tiers | persist | EnvoyGeneralSettingsMenu wave.randomForager tiers.enabled |
| 20 | /il replenish T1 | fills chests | HGCommand:240 → ReplenishManager.replenishTier |
| 21 | /il replenish mode give | wand mode | HGCommand:228 → ReplenishModeManager |

- ⬜ Subsection complete

### 7.4 Cross-map regression

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 22 | Map A vs B separate | own folders | WorldEnvoyStorage.safeFolder per templateKey |
| 23 | Match Map A only | Map B unaffected | GameManager.startGame ReplenishManager.setMatchContext + EnvoyManager.start scoped key |
| 24 | Hub never refills | no match context | ReplenishManager.startSchedule requires matchTemplateKey · IlGameWorldScopeResolver hub excluded |

- ⬜ Subsection complete

## Phase 8 — Full match gameplay

### 8.1 Match start sequence

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | /il start from limbo | staged countdown | HGCommand:437 → HubPreMatchLimboService.forceBeginTeleportCountdown → launchSession · API: PreMatchLimboService IronLegacyLimboBridge |
| 2 | Pre-teleport 5..1 | DO NOT MOVE | GameManager.startGame:326-344 pre countdown titles |
| 3 | Teleport timing | correct moment | HubPreMatchLimboService.tickTeleportCountdown → launchSession → GameManager.doTeleportPhase |
| 4 | Post-teleport freeze delay | hidden delay | GameManager.doTeleportPhase softLeashEndMs + runTaskLater 5*20 |
| 5 | Freeze countdown | center numbers | GameManager.beginCountdown sendPrompt per second |
| 6 | launchOnMove | punishment | GameManager.onMove:648 reads launchOnMove toggle |
| 7 | BEGIN prompt | correct moment | GameManager.beginCountdown t<=0 BEGIN title |

- ⬜ Subsection complete

### 8.2 Replenish runtime

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 8 | Schedule starts | with match | GameManager.doTeleportPhase → ReplenishManager.startSchedule |
| 9 | First refill ~20s | delay | ReplenishManager.startScheduleInternal firstRefillDelaySeconds |
| 10 | Subsequent interval | from yaml | replenish.yml schedule.intervalMinutes |
| 11 | Tier progression | T1→T4 | ReplenishManager scheduler tierIndex/repeatsPerTier |
| 12 | Coords on instance | same XYZ | ReplenishManager.resolveFillWorld + fillChest BlockCoord |
| 13 | Break replenish chest | blocked | ReplenishChestProtectionListener.onBreak:27 |
| 14 | Explosion on chest | protected | ReplenishChestProtectionListener.onEntityExplode/onBlockExplode |

- ⬜ Subsection complete

### 8.3 Envoy runtime

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 15 | /il envoy start in match | scoped yaml | HGCommand:142 → EnvoyManager.start yamlForTemplate |
| 16 | Falling crates | visuals | EnvoyManager ticker → FallingCrate |
| 17 | Right-click package | opens loot | EnvoyListener.onInteractBlock → EnvoyManager.tryOpen |
| 18 | Break crate wrong | no loot drop | EnvoyListener.onBlockBreak cancel |
| 19 | Explosion package | not destroyed | EnvoyListener explode handlers strip package blocks |
| 20 | Tiered packages | scoped loot | CrateType + envoys.yml tiers |

- ⬜ Subsection complete

### 8.4 Death & audio

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 21 | Die in match | removed from game | GameManager.onDeath:696 removeFromGame |
| 22 | Sent out of world | hub return | GameManager.sendPlayerToHub → HubIntegrationRouting.returnToHubOrLobby |
| 23 | Death boom scoped | same world only | GameManager.onDeath playSound loop currentWorld.getPlayers |
| 24 | Hub doesn't hear boom | excluded | same loop — hub players not in match world |
| 25 | Kill leaderboard | increments | Hub PvP: IronLegacyHubBridge.recordKill · limbo: HubPreMatchLimboArenaListener → KillLeaderboardManager |
| 26 | /il giftdrop | gift drops | HGCommand:206 → GiftDropManager → GiftDropListener |

- ⬜ Subsection complete

### 8.5 Border & endgame

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 27 | Border shrink RUNNING only | scheduled | GameManager.scheduleBorderShrink state==RUNNING guard |
| 28 | Border prompt | styled once | scheduleBorderShrink sendPrompt |
| 29 | Winner title+invuln | last standing | GameManager.evaluateWinCondition showTitle setInvulnerable |
| 30 | Winner hub delay | return | evaluateWinCondition runTaskLater → sendPlayerToHub |
| 31 | Cleanup evacuate | all out | GameManager.stopGame evacuatePlayersFromInstance |
| 32 | Instance destroyed | M# gone | TemplateWorldManager.destroyMatchInstanceByWorld → HubIronLegacyMatchInstanceRuntime |
| 33 | Next match clean | fresh clone | DefaultGamePlatform.createMatchWorld WorldFolderCopier |
| 34 | Limbo reset | next run | HubPreMatchLimboService.cleanupLimboSession |
| 35 | Replenish/envoy stop | on end | GameManager.stopGame ReplenishManager.stopSchedule + envoy stop hook |

- ⬜ Subsection complete

### 8.6 Teams

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 36 | /il teams in limbo | Hub party menu | HGCommand:273 isInLimbo → HubPlayerMenus.openPartyMenu |
| 37 | Create team limbo | works | TeamsMenu → TeamManager.createTeam → HubTeamService |
| 38 | Invite/join/color menus | paging | InviteMenu JoinTeamMenu TeamColorMenu MenuListener |
| 39 | Team cap | enforced | TeamManager.acceptTeamInvite → HubTeamService memberCap |
| 40 | /il randomteams 2 | randomize | HGCommand:633 runRandomTeams wipeAllTeamsHard |
| 41 | Team chat SPI | prefix/color | IronLegacyTeamChatProvider registered HungerGamesPlugin:203 · API: TeamChatProvider |

- ⬜ Subsection complete

## Phase 9 — Persistence & restart

### 9.1 Cold restart YAML

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | Restart enables all | 3 plugins | Foundation→Hub→IL onEnable chain |
| 2 | /hub spawn persists | correct TP | DefaultGamePlatform.reloadHubSpawnFromConfig config.yml hub.spawn |
| 3 | templates.yml | testmap row | HubTemplateRegistry loadFromDisk on boot |
| 4 | hub-world.yml | pvp/pads/radius | HubWorldYaml load in HubWorldManager ctor |
| 5 | parties.yml | party state | HubPartyService loadFromDisk:54 |
| 6 | lobby-instances.yml | OPEN restored | HubLobbyInstanceRegistry load |
| 7 | IL worlds.yml | limbo zone | TemplateWorldManager.reload worlds.yml |
| 8 | world-envoys/<key>/ | scoped yaml | WorldEnvoyStorage on disk |
| 9 | Kill leaderboard | persists | KillLeaderboardManager.reload kills.yml |
| 10 | No stale worlds | clean | TemplateWorldManager.recoverStaleInstances HubLobbyService reconciliation |

- ⬜ Subsection complete

### 9.2 MySQL optional

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 11 | Parties MySQL | survive restart | HubPartyJdbcSync + HubPartyService |
| 12 | Lobby instances MySQL | survive | HubLobbyJdbcSync |
| 13 | Queue snapshots | hc_queue_snapshot | HubMatchQueueService jdbcLoad HubSqlMigrations |
| 14 | MySQL down | YAML fallback | HubSqlRuntime.tryConnect null → HubYamlStorageProvider |

- ⬜ Subsection complete

### 9.3 Reconnect edge cases

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 15 | All offline 3min disband | party gone | HubPartyService.tickMaintenance allOfflineDisbandSeconds config |
| 16 | Leader offline promote | new leader | tickMaintenance leaderAbandonedPromoteSeconds |
| 17 | Quit while queued | removed | HubPartyListener.onQuit → matchQueueService.leaveQueue |
| 18 | Build mode reconnect | inv kept | HubWorldManager buildModeSnapshots restore |

- ⬜ Subsection complete

## Phase 10 — Optional integrations

### 10.1 Velocity

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 1 | velocity.enabled proxy | cross-server | VelocityBridgeManager.connectPlayer BungeeCord Connect · PERSIST: gameflow.yml velocity.* |
| 2 | Post-match hub route | hubServerId | HubIntegrationRouting.returnToHubOrLobby velocity.connectPlayer |
| 3 | Same server no hop | direct lobby | HubIntegrationRouting.hubTargetIsThisServer → sendToLobbyExact |

- ⬜ Subsection complete

### 10.2 Lobby sharding

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 4 | Shard boundaries | clamped | HubShardBoundaryListener.onTeleport LobbyShardManager.assignPlayer |
| 5 | lobbyShardPlayers cap | new shard | LobbyShardManager.assignPlayer shardCap gameflow.yml |

- ⬜ Subsection complete

### 10.3 Resource pack

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 6 | Hub return pack prompt | if configured | LobbyManager.sendToLobbyExact → ResourcePackManager.applyIfEnabled · PERSIST: resourcepack.yml |

- ⬜ Subsection complete

### 10.4 Environmental Hazards

| # | Action | Expected | Code path |
|---|--------|----------|-----------|
| 7 | Publish/delete cleanup | hazards cleared | EnvironmentalHazardsBridge.onTemplatePublished/onTemplateDeleted · API: EnvironmentalHazardsLifecycle |
| 8 | Match end cleanup | hazards cleared | EnvironmentalHazardsBridge.onMatchWorldDestroyed |

- ⬜ Subsection complete

---

## 15-minute smoke test

| Step | Action | Code path |
|------|--------|-----------|
| 1 | Phase 0 console | Foundation/Hub/IL onEnable |
| 2 | sethub → hub → join → lobby | HubCommandExecutor + HubPlayerCommand + HubLobbyService |
| 3 | party create/invite/accept | hub/party/* |
| 4 | compass → limbo → /il start | HubWorldManager.onHubCompassUse + HubPreMatchLimboService + IronLegacyLimboBridgeImpl |
| 5 | /il stop → /hub | GameManager.stopGame + HubPlayerCommand |

---

## Cross-plugin quick index

| Symptom | Start here | API contract |
|---------|------------|--------------|
| Command not found | `hub/plugin.yml` or `iron-legacy/plugin.yml` | — |
| Template list/queue/join | `hub/template/HubTemplateRegistry` | `api/template/TemplateRegistry` |
| Lobby L# | `hub/lobby/HubLobbyService` | `api/platform/GamePlatform` |
| Limbo | `hub/limbo/HubPreMatchLimboService` | `api/integration/PreMatchLimboService` |
| Match M# | `hub/integration/HubIronLegacyMatchInstanceRuntime` | `api/integration/IronLegacyMatchInstanceRuntime` |
| Match gameplay | `iron-legacy/managers/GameManager` | — |
| Hub PvP/protection | `hub/world/HubWorldManager` | `api/integration/HubWorldApi` |
| Parties | `hub/party/HubPartyService` | `api/party/PartyService` |
| Queue | `hub/queue/HubMatchQueueService` | `api/queue/MatchQueueService` |

---

## Bug report template

```
What you did:
Expected:
Actual:
Console (Foundation / Hub / IL):
/plugins:
Template key / world:
Code path row # from this doc:
Suspected file from Code path:
```

---

## Final sign-off

| Result | When |
|--------|------|
| ⬜ PASS | Phases 0–9 critical paths |
| ⬜ PASS WITH NOTES | minor issues only |
| ⬜ FAIL | blocking issue |

| Phase | Tester | Date | Notes |
|-------|--------|------|-------|
| 0–5 Hub | | | |
| 6–8 IL | | | |
| 9 Persistence | | | |
| 10 Optional | | | |

Also see `minigames/iron-legacy/IN_GAME_QA_CHECKLIST.md` for extra envoy/replenish micro-checks (some `/il` commands there are deprecated — use migration table above).
