# Hollow Crown Platform — full in-game test

Use this on a **Paper 1.21.4** test server with **two accounts** (one OP, one normal player) when possible.

Mark each line: ✅ pass · ❌ fail · ⚠️ skipped · ⬜ not tested

**Fix in** column = where to look in the repo when a test fails. Paths are relative to the monorepo root.

| Abbrev | Module | Deploy JAR |
|--------|--------|------------|
| `foundation/` | HC-Foundation | `foundation/target/HC-Foundation-*.jar` |
| `api/` | hollowcrown-api (library — **not** deployed) | — |
| `hub/` | HollowCrownHub | `hub/target/HollowCrownHub-*.jar` |
| `iron-legacy/` | HCIronLegacy | `minigames/iron-legacy/target/HCIronLegacy-*.jar` |

---

## What you are testing

| Plugin | Role | Fix in |
|--------|------|--------|
| **HC-Foundation** | Loads API on classpath (no player commands) | `foundation/` |
| **HollowCrownHub** | Hub spawn, protection, parties, queues, lobbies, limbo, admin GUIs | `hub/` |
| **HCIronLegacy** | Iron Legacy minigame (`iron-legacy` mechanic), match flow, envoy/replenish, teams | `iron-legacy/` |

**Do not deploy** `hollowcrown-api` JAR — library only (`api/`).

**Not in scope:** NPCs, cosmetics (not implemented).

**Known gap (not a fail):** Match queue **enqueues only** — does not auto-send to limbo/match yet (`hub/queue/HubMatchQueueService.java`).

---

## Command migration reference (old IL → current Hub)

| Old (IL checklist) | Use now | Fix in |
|--------------------|---------|--------|
| `/il setlobby` | `/hch sethub` | `hub/command/HubCommandExecutor.java` |
| `/il lobby` / `/il hub` | `/hub` | `hub/command/HubPlayerCommand.java` |
| `/il buildmode` | `/hch hubworld buildmode` | `hub/command/HubWorldAdminCommands.java`, `hub/world/HubWorldManager.java` |
| `/il pvp pad` | `/hch hubworld pvp pad` | `hub/command/HubWorldAdminCommands.java`, `hub/world/HubWorldManager.java` |
| `/il create` / `/il gameedit` | `/hch template create` / `/hch template worlds tp` | `hub/command/HubTemplateCommands.java`, `hub/template/HubTemplateWorldBootstrap.java` |
| `/il game set … gamerule` | `/hch template set <key> gamerule …` | `hub/command/HubIlWorldSetCommand.java` |
| Limbo wand | `/hch template limbo wand` | `hub/command/HubTemplateLimboCommands.java`, `hub/limbo/HubLimboWandListener.java` |
| `/hch ironlegacy` | **Removed** — use `/hch template worlds` or `/hch gui maps` | `hub/command/HubCommandExecutor.java` |
| `/il game` / `/il gameedit` | **Removed** — use `/hch template worlds` or `/hch gui maps` | `iron-legacy/commands/HGCommand.java` |

---

## Phase 0 — Server setup (before joining)

### 0.1 Deploy JARs (correct order)

| # | JAR | Console plugin name | Fix in |
|---|-----|---------------------|--------|
| 1 | `foundation/target/HC-Foundation-*.jar` | HC-Foundation | `foundation/src/main/resources/plugin.yml` |
| 2 | `hub/target/HollowCrownHub-*.jar` | HollowCrownHub | `hub/src/main/resources/plugin.yml` |
| 3 | `minigames/iron-legacy/target/HCIronLegacy-*.jar` | HCIronLegacy | `iron-legacy/src/main/resources/plugin.yml` |

### 0.2 Console on startup (must see)

| Check | Expected | Fix in |
|-------|----------|--------|
| ⬜ HC-Foundation enables at **STARTUP** | Log: `hollowcrown-api schema=2` | `foundation/java/.../HollowCrownApiBootstrap.java`, `api/HollowCrownApiVersion.java` |
| ⬜ HollowCrownHub enables | API schema + storage `YAML` or `MYSQL` | `hub/HollowCrownHubPlugin.java`, `hub/storage/*` |
| ⬜ HCIronLegacy enables | `mechanic registered: id=iron-legacy` | `iron-legacy/HungerGamesPlugin.java`, `iron-legacy/integration/IronLegacyMechanic.java` |
| ⬜ No IL disabled / missing Hub messages | Clean enable | `iron-legacy/integration/IronLegacyMechanic.java`, `api/minigame/MinigameBootstrap.java` |
| ⬜ `/plugins` shows all three green | Correct load order | All three `plugin.yml` (`depend` chains) |

### 0.3 Fail-fast checks

| Check | Symptom | Fix in |
|-------|---------|--------|
| ⬜ IL disables | Foundation or Hub missing | `iron-legacy/HungerGamesPlugin.java` (`depend`) |
| ⬜ `IronLegacyLimboBridge not registered` | IL missing or wrong order | `iron-legacy/integration/IronLegacyLimboBridgeImpl.java`, `hub/HollowCrownHubPlugin.java` |
| ⬜ `storage.type=MYSQL` + JDBC SEVERE | MySQL misconfigured | `hub/storage/HubSqlRuntime.java`, `hub/src/main/resources/config.yml` |

### 0.4 Test environment prep

| Check | Fix in |
|-------|--------|
| ⬜ One built hub world | Server / world setup (not code) |
| ⬜ One template source world on disk | `hub/util/WorldFolderCopier.java`, `hub/lobby/HubLobbyService.java` |
| ⬜ OP has `hollowcrownhub.admin` + `hcironlegacy.admin` | `hub/plugin.yml`, `iron-legacy/plugin.yml` |
| ⬜ Normal player has no OP | Server permissions |

---

## Phase 1 — Hub bootstrap (OP, in hub world)

### 1.1 Core hub setup

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 1 | `/hch help` | Full command tree | `hub/command/HubCommandExecutor.java` |
| 2 | `/hch version` | Hub version + API schema **2** | `hub/command/HubCommandExecutor.java`, `api/HollowCrownApiVersion.java` |
| 3 | `/hch sethub` | Hub spawn saved (UUID + coords + yaw/pitch) | `hub/command/HubCommandExecutor.java`, `hub/platform/DefaultGamePlatform.java` |
| 4 | `/hch debug mechanics` | Lists **`iron-legacy`** | `hub/command/HubDebugWhy.java`, `hub/platform/DefaultGamePlatform.java` |
| 5 | `/hch gui help` | Admin GUI help text | `hub/command/HubGuiCommands.java` |

### 1.2 Template registration

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 6 | `/hch template list` | Lists templates | `hub/command/HubTemplateCommands.java`, `hub/template/HubTemplateRegistry.java` |
| 7 | `/hch template set testmap iron-legacy "Test Map"` | Row in `templates.yml` | `hub/template/HubTemplateRegistry.java` |
| 8 | `/hch template setsource testmap <folder>` | Source folder set | `hub/template/HubTemplateRegistry.java` |
| 9 | `/hch template get testmap` | Shows mechanic, display, source, queue policy | `hub/command/HubTemplateCommands.java` |
| 10 | `/hch template setqueue testmap maxUnitSize 4` | Queue policy saved | `hub/template/HubTemplateRegistry.java` |
| 11 | `/hch template setqueue testmap soloOnly false` | Saved | `hub/template/HubTemplateRegistry.java` |
| 12 | `/hch template setqueue testmap soloAutoMerge true` | Saved | `hub/template/HubTemplateRegistry.java` |
| 13 | `/hch template setqueue testmap joinQueueFirst false` | Saved (test `true` in Phase 2) | `hub/template/HubTemplateRegistry.java`, `api/template/TemplateEntry.java` |

### 1.3 Hub world protection config

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 14 | `/hch hubworld size 64` | Radius in `hub-world.yml` | `hub/command/HubWorldAdminCommands.java`, `hub/world/HubWorldYaml.java` |
| 15 | `/hch hubworld hostilemobs false` | Hostile spawns off | `hub/command/HubWorldAdminCommands.java`, `hub/world/HubWorldManager.java` |
| 16 | `/hch hubworld passivemobs false` | Passive spawns off | `hub/command/HubWorldAdminCommands.java`, `hub/world/HubWorldManager.java` |

### 1.4 Optional: scaffold template from scratch

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 17 | `/hch template create testmap2 "Test Map 2"` | Creates `hch-worlds/templates/testmap2`, teleports OP to editor | `hub/command/HubTemplateCommands.java`, `hub/template/HubTemplateWorldBootstrap.java` |

- ⬜ Phase 1 complete

---

## Phase 2 — Hub player flow (normal player account)

### 2.1 Hub teleport & first join

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 1 | `/hub` | Teleport to `/hch sethub` location | `hub/command/HubPlayerCommand.java`, `hub/world/HubWorldManager.java` |
| 2 | Check hotbar | Compass (slot 5), Party (slot 9), PvP sword (slot 0) | `hub/world/HubWorldManager.java`, `hub/world/HubWorldYaml.java` |
| 3 | New player joins server | Auto-teleport + loadout if `teleportNewPlayersToHub: true` | `hub/world/HubWorldManager.java` |

### 2.2 Join GUI & lobby instances

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 4 | `/hch join` | Join Template GUI opens | `hub/command/HubJoinCommands.java`, `hub/gui/LobbyJoinTemplateMenu.java` |
| 5 | Right-click compass → Game Lobby | Same join GUI | `hub/world/HubWorldManager.java`, `hub/gui/LobbyJoinTemplateMenu.java` |
| 6 | Left-click **testmap** | Joins oldest OPEN lobby or creates `testmap-L#` | `hub/gui/LobbyJoinMenuListener.java`, `hub/lobby/HubLobbyService.java` |
| 7 | Right-click **testmap** | Lobby instance picker | `hub/gui/LobbyJoinInstanceMenu.java` |
| 8 | In picker: click OPEN instance | Teleport into `testmap-L#` | `hub/gui/LobbyJoinMenuListener.java`, `hub/lobby/HubLobbyService.java` |
| 9 | In picker: Leave Lobby (red bed) | Hub spawn + hub loadout | `hub/gui/LobbyJoinMenuListener.java`, `hub/limbo/HubPreMatchLimboService.java` |
| 10 | GUI pagination | Prev/next arrows work | `hub/gui/LobbyJoinTemplateMenu.java`, `hub/gui/LobbyJoinInstanceMenu.java` |
| 11 | OP lore on templates | Template key + mechanic id | `hub/gui/LobbyJoinTemplateMenu.java` |

### 2.3 Party system

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 12 | `/hch party create` | Party created; UUID returned | `hub/command/HubPartyCommands.java`, `hub/party/HubPartyService.java` |
| 13 | `/hch party invite <player>` | Target gets invite paper | `hub/party/HubPartyService.java`, `hub/party/HubPartyListener.java` |
| 14 | Other: left-click invite paper | Accepts invite | `hub/party/HubPartyListener.java` |
| 15 | Other: `/hch party accept <id>` | Joins via command | `hub/command/HubPartyCommands.java` |
| 16 | Leader gets title | Member joined notification | `hub/party/HubPartyService.java` |
| 17 | Right-click party item | Party GUI opens | `hub/gui/HubPartyMenu.java`, `hub/integration/DefaultHubPlayerMenus.java` |
| 18 | Party GUI: Invite Players | Heads, paginated, excludes self/members | `hub/gui/HubPartyMenu.java` |
| 19 | Party GUI: Party Members | Roster, Leave Party | `hub/gui/HubPartyMenu.java` |
| 20 | Party GUI: Pending Invites | Accept/decline papers | `hub/gui/HubPartyMenu.java` |
| 21 | `/hch party decline <id>` | Leader notified | `hub/party/HubPartyService.java` |
| 22 | Accepting one invite | Auto-declines others | `hub/party/HubPartyService.java` |
| 23 | Wait 5+ min on invite | Invite expires (TTL 300s) | `hub/party/HubPartyService.java`, `hub/config.yml` |
| 24 | Leader `/hub` | Whole online party teleports | `hub/command/HubPlayerCommand.java`, `hub/party/HubPartyService.java` |
| 25 | `/hch party kick <player>` | Leader kicks member | `hub/command/HubPartyCommands.java` |
| 26 | `/hch party leave` | Leave; leader promoted if leader left | `hub/party/HubPartyService.java` |
| 27 | `/hch party disband` | Leader disbands | `hub/command/HubPartyCommands.java` |

### 2.4 Match queue

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 28 | `/hch queue join testmap` | Queued with position | `hub/command/HubQueueCommands.java`, `hub/queue/HubMatchQueueService.java` |
| 29 | `/hch queue status testmap` | Shows queue units | `hub/command/HubQueueCommands.java` |
| 30 | `/hch queue leave` | Leaves queue | `hub/queue/HubMatchQueueService.java` |
| 31 | Party leader queues | Whole party as one unit | `hub/queue/HubMatchQueueService.java` |
| 32 | Non-leader tries queue join | Denied `NOT_PARTY_LEADER` | `hub/queue/HubMatchQueueService.java`, `api/queue/MatchEnqueueResult.java` |
| 33 | Queue with offline party member | Denied `OFFLINE_PARTY_MEMBER` | `hub/queue/HubMatchQueueService.java` |
| 34 | `soloOnly true` + party queue | Denied `SOLO_ONLY` (unless bypass) | `hub/queue/HubMatchQueueService.java`, `hub/template/HubTemplateEntry.java` |
| 35 | Party larger than `maxUnitSize` | Denied `PARTY_TOO_LARGE` | `hub/queue/HubMatchQueueService.java` |
| 36 | OP with `hollowcrownhub.queue.bypass` | Bypasses gates | `hub/command/HubQueueCommands.java`, `hub/plugin.yml` |
| 37 | Player disconnects while queued | Removed from queue | `hub/party/HubPartyListener.java`, `hub/queue/HubMatchQueueService.java` |
| 38 | Party member leaves queue | Others get title + action bar | `hub/queue/HubMatchQueueService.java`, `hub/command/MatchEnqueueMessages.java` |

### 2.5 `joinQueueFirst` template policy

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 39 | Set `joinQueueFirst true` on template | Saved | `hub/template/HubTemplateRegistry.java` |
| 40 | Left-click template in join GUI | Enqueues instead of lobby join | `hub/gui/LobbyJoinMenuListener.java`, `hub/gui/LobbyJoinTemplateMenu.java` |

### 2.6 IL player commands from hub

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 41 | `/il help` | Lists `/hub`, party, admin | `iron-legacy/commands/HGCommand.java` |
| 42 | `/il party` | Opens **Hub** party menu | `iron-legacy/commands/HGCommand.java`, `hub/integration/DefaultHubPlayerMenus.java` |
| 43 | `/il leaderboard` | Top 10 kills | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/KillLeaderboardManager.java` |

- ⬜ Phase 2 complete

---

## Phase 3 — Hub lobby admin (OP)

### 3.1 CLI lobby management

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 1 | `/hch lobby list` | Shows OPEN instances (`testmap-L#`) | `hub/command/HubLobbyCommands.java`, `hub/lobby/HubLobbyInstanceRegistry.java` |
| 2 | `/hch lobby create testmap` | New `testmap-L1` loads | `hub/lobby/HubLobbyService.java`, `hub/util/WorldFolderCopier.java` |
| 3 | Console log | Lobby instance ready | `hub/lobby/HubLobbyService.java`, `hub/HubLog.java` |
| 4 | Normal player joins via `/hch join` | Teleport into instance | `hub/lobby/HubLobbyService.java` |
| 5 | `/hch lobby tp <id>` | OP teleports to instance | `hub/command/HubLobbyCommands.java` |
| 6 | `/hch debug why <playerName>` | Snapshot: world, party, queue, lobby | `hub/command/HubDebugWhy.java` |

### 3.2 Lobby admin GUI

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 7 | `/hch lobby gui` | Admin lobby chest GUI | `hub/gui/LobbyAdminMenu.java` |
| 8 | Click lime pane (OPEN) | Teleport to instance | `hub/gui/LobbyAdminMenuListener.java` |
| 9 | Shift+click lime pane | Destroy instance | `hub/gui/LobbyAdminMenuListener.java`, `hub/lobby/HubLobbyService.java` |
| 10 | `/hch gui lobby` | Same via admin shell | `hub/command/HubGuiCommands.java`, `hub/gui/DefaultHubAdminGui.java` |

### 3.3 Lobby destroy & BROKEN state

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 11 | `/hch lobby destroy <id>` | Evacuate, unload, delete folder | `hub/lobby/HubLobbyService.java` |
| 12 | Destroy fails (disk lock etc.) | Marked **[BROKEN]** | `hub/lobby/HubLobbyService.java`, `hub/lobby/LobbyInstanceState.java` |
| 13 | `/hch lobby forget <id>` | Removes BROKEN registry row | `hub/command/HubLobbyCommands.java`, `hub/lobby/HubLobbyInstanceRegistry.java` |
| 14 | Barrier in BROKEN GUI | Forget action works | `hub/gui/LobbyAdminMenuListener.java` |

### 3.4 Lobby prefetch

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 15 | Last OPEN lobby starts match | Hub pre-creates next `testmap-L#` | `hub/lobby/HubLobbyService.java`, `hub/limbo/HubPreMatchLimboService.java` |
| 16 | `/hch lobby list` after match start | New OPEN lobby exists | `hub/lobby/HubLobbyInstanceRegistry.java` |

### 3.5 Template publish flow

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 17 | Players in `testmap-L#` lobbies | Players inside | `hub/lobby/HubLobbyService.java` |
| 18 | `/hch template worlds publish testmap` | Destroys old lobbies | `iron-legacy/integration/IronLegacyTemplateAdminImpl.java`, `hub/lobby/HubLobbyService.java` |
| 19 | Players in destroyed lobbies | "Lobbies closed, rejoin" message | `hub/lobby/HubLobbyService.java` |
| 20 | After publish | Fresh lobby pre-created | `hub/lobby/HubLobbyService.java` |

- ⬜ Phase 3 complete

---

## Phase 4 — Iron Legacy integration (limbo → match)

### 4.1 IL worlds admin via Hub

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 1 | `/hch template worlds help` | Subcommands documented | `hub/command/HubTemplateWorldsCommands.java` |
| 2 | `/hch template worlds list` | IL `worlds.yml` rows | `hub/command/HubTemplateWorldsCommands.java`, `iron-legacy/managers/TemplateWorldManager.java` |
| 3 | `/hch template worlds importhub --dry-run` | Import preview | `iron-legacy/integration/IronLegacyTemplateAdminImpl.java` |
| 4 | `/hch template worlds importhub` | Hub templates → IL `worlds.yml` | `iron-legacy/integration/IronLegacyTemplateAdminImpl.java` |
| 5 | `/hch template worlds reload` | IL caches reload | `iron-legacy/managers/ConfigFilesManager.java` |
| 6 | `/hch gui maps` | IL template browser GUI | `hub/gui/HubIlTemplateListMenu.java`, `hub/gui/DefaultHubAdminGui.java` |
| 7 | Click template in GUI | Detail: TP, publish, delete, snapshots | `hub/gui/HubIlTemplateDetailMenu.java` |
| 8 | `/hch template worlds tp testmap` | Teleport to editor (creative) | `iron-legacy/integration/IronLegacyTemplateAdminImpl.java` |
| 9 | `/hch template set testmap display "New Name"` | IL display updated | `hub/command/HubIlWorldSetCommand.java` |
| 10 | `/hch template set testmap maxplayers 24` | Saved | `hub/command/HubIlWorldSetCommand.java`, `iron-legacy/managers/WorldSettingsManager.java` |
| 11 | `/hch template set testmap teamsize 2` | Saved | `hub/command/HubIlWorldSetCommand.java` |
| 12 | `/hch template set testmap minimumpercentage 50` | Limbo fill % gate | `hub/command/HubIlWorldSetCommand.java`, `hub/limbo/HubPreMatchLimboService.java` |
| 13 | `/hch template set testmap gamerule doDaylightCycle false` | Saved | `hub/command/HubIlWorldSetCommand.java` |
| 14 | `/hch template set testmap enabled true` | Enabled in join GUI | `hub/command/HubIlWorldSetCommand.java` |
| 15 | `/hch template worlds toggle testmap` | Flips enabled | `iron-legacy/integration/IronLegacyTemplateAdminImpl.java` |
| 16 | `/hch template worlds snapshots testmap` | Lists snapshot IDs | `iron-legacy/managers/TemplateWorldManager.java` |
| 17 | `/hch template worlds rollback testmap latest` | Rollback works | `iron-legacy/managers/TemplateWorldManager.java` |
| 18 | `/hch template worlds iconpreview` | Icon preview chest | `hub/gui/HubIlIconPreviewMenu.java`, `iron-legacy/managers/IconManager.java` |

### 4.2 Limbo zone setup

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 19 | `/hch template limbo wand` | Limbo wand in inventory | `hub/command/HubTemplateLimboCommands.java` |
| 20 | Left-click corner 1 | Corner saved | `hub/limbo/HubLimboWandListener.java` |
| 21 | Right-click corner 2 | Corner saved | `hub/limbo/HubLimboWandListener.java` |
| 22 | Shift-right-click | Zone confirmed; cyan outline while holding wand | `hub/limbo/HubLimboWandListener.java`, `hub/limbo/HubLimboLayout.java` |
| 23 | Outline updates on corner change | Instant update | `hub/limbo/HubLimboWandListener.java` |
| 24 | Inside zone: `/hch template limbo spawn` | Limbo spawn saved | `hub/command/HubTemplateLimboCommands.java`, `hub/limbo/HubLimboWorldsYaml.java` |
| 25 | Set spawn outside zone | Rejected | `hub/command/HubTemplateLimboCommands.java` |
| 26 | Wand in `testmap-L#` lobby | Auto-detects template from world name | `hub/limbo/HubLimboWandListener.java`, `hub/gui/HubIlTemplateDetailMenu.java` |

### 4.3 Pre-match limbo (player experience)

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 27 | Join lobby via compass | Adventure mode, limbo loadout | `hub/limbo/HubPreMatchLimboService.java`, `hub/world/HubWorldManager.java` |
| 28 | Limbo loadout items | Sword, compass, party, Leave Lobby barrier | `hub/world/HubWorldManager.java`, `hub/world/HubWorldYaml.java` |
| 29 | Walk outside limbo zone | Clamped back | `hub/limbo/HubPreMatchLimboArenaListener.java` |
| 30 | Action bar | `N/max Players` fill count | `hub/limbo/HubPreMatchLimboService.java` |
| 31 | Fill below `minimumpercentage` | Match does not auto-start | `hub/limbo/HubPreMatchLimboService.java`, `iron-legacy/integration/IronLegacyLimboBridgeImpl.java` |
| 32 | Fill reaches threshold | 5-min queue countdown | `hub/limbo/HubPreMatchLimboService.java` |
| 33 | 10s teleport countdown | Title announcements | `hub/limbo/HubPreMatchLimboService.java` |
| 34 | Right-click barrier / Leave Lobby | Hub spawn; removed from limbo | `hub/limbo/HubPreMatchLimboService.java`, `hub/limbo/HubPreMatchLimboArenaListener.java` |
| 35 | Leave via lobby picker red bed | Same hub return | `hub/gui/LobbyJoinMenuListener.java` |
| 36 | `/il hostilemobspawn false` | No hostile mobs in limbo | `iron-legacy/commands/HGCommand.java`, `hub/limbo/HubPreMatchLimboArenaListener.java` |
| 37 | `/il passivemobspawn false` | No passive mobs in limbo | `iron-legacy/commands/HGCommand.java`, `hub/limbo/HubPreMatchLimboArenaListener.java` |

### 4.4 Match start & stop

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 38 | `/il start` (OP, in limbo) | Staged countdown → match | `iron-legacy/commands/HGCommand.java`, `iron-legacy/integration/IronLegacyLimboBridgeImpl.java` |
| 39 | Players in match instance | Cloned world (`testmap-M#`) | `hub/integration/HubIronLegacyMatchInstanceRuntime.java`, `iron-legacy/managers/GameManager.java` |
| 40 | Match registry | Hub lobby row detached; world kept | `hub/lobby/HubLobbyService.java`, `api/mechanic/MinigameMechanic.java` |
| 41 | `/il stop` (OP) | Match aborts; players to hub | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/GameManager.java` |
| 42 | `/il joingame <world>` (OP) | Bypass join | `iron-legacy/commands/HGCommand.java` |

### 4.5 Hub admin shell GUIs

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 43 | `/hch gui admin` | Multi-tab admin home | `hub/gui/HubAdminShellMenu.java` |
| 44 | Templates browser | Read-only `templates.yml` + queue policy | `hub/gui/HubHubTemplateBrowserMenu.java` |
| 45 | Queues overview | Live queue depth per template | `hub/gui/HubQueueOverviewMenu.java` |
| 46 | `/il admin` | IL Admin Control Center | `iron-legacy/gui/AdminControlCenterMenu.java` |

- ⬜ Phase 4 complete

---

## Phase 5 — Hub world rules & PvP

### 5.1 Hub protection (non-OP)

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 1 | Break block in hub | Blocked | `hub/world/HubWorldManager.java` |
| 2 | Place block in hub | Blocked | `hub/world/HubWorldManager.java` |
| 3 | Open crafting table | Crafting blocked | `hub/world/HubWorldManager.java` |
| 4 | Drop compass/sword/party item | Blocked (locked items) | `hub/world/HubWorldManager.java` |
| 5 | Swap locked hotbar slots | Blocked | `hub/world/HubWorldManager.java` |
| 6 | Walk outside protection radius | Clamped on X/Z | `hub/world/HubWorldManager.java`, `hub/world/HubWorldYaml.java` |
| 7 | Time/weather in hub | Always day, no storm | `hub/world/HubWorldManager.java` |
| 8 | Hostile/passive mob toggles | Respects hubworld settings | `hub/world/HubWorldManager.java` |

### 5.2 OP build mode

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 9 | `/hch hubworld buildmode true` | Creative; inventory snapshot | `hub/command/HubWorldAdminCommands.java`, `hub/world/HubWorldManager.java` |
| 10 | Build freely in hub | Allowed | `hub/world/HubWorldManager.java` |
| 11 | `/hch hubworld buildmode false` | Survival; hub loadout restored | `hub/world/HubWorldManager.java` |
| 12 | Toggle buildmode on again | Previous build inventory restored | `hub/world/HubWorldManager.java` |
| 13 | `/hub` after template/limbo | Build mode restored if active | `hub/command/HubPlayerCommand.java`, `hub/world/HubWorldManager.java` |

### 5.3 Hub opt-in PvP

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 14 | Hold sword ~0.5s (10 ticks) | "PVP mode Activated" title | `hub/world/HubWorldManager.java` |
| 15 | Release sword | 5-tick deactivation countdown | `hub/world/HubWorldManager.java` |
| 16 | PvP on vs PvP on | Damage works | `hub/world/HubWorldManager.java` |
| 17 | PvP on vs PvP off | No damage | `hub/world/HubWorldManager.java` |
| 18 | Kill in hub PvP | IL kill credit on leaderboard | `iron-legacy/integration/IronLegacyHubBridgeImpl.java`, `iron-legacy/managers/KillLeaderboardManager.java` |
| 19 | Die in hub PvP | Keep inventory + levels | `hub/world/HubWorldManager.java` |
| 20 | Respawn after PvP death | 3s invulnerability + yellow ring | `hub/world/HubWorldManager.java` |
| 21 | Invulnerable player | Cannot deal or receive PvP damage | `hub/world/HubWorldManager.java` |
| 22 | PvP off outside zone/pad | No teleport | `hub/world/HubWorldManager.java` |
| 23 | PvP off inside zone or with pad | Teleport to hub spawn | `hub/world/HubWorldManager.java` |

### 5.4 PvP zone (admin)

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 24 | `/hch hubworld pvp wand` | Blaze rod wand | `hub/command/HubWorldAdminCommands.java` |
| 25 | Set corners in hub protection | Zone in `hub-world.yml` | `hub/world/HubPvpZoneWandListener.java`, `hub/world/HubWorldYaml.java` |
| 26 | Outline only while holding wand | Recent behavior | `hub/world/HubPvpZoneWandListener.java` |
| 27 | Wand outside hub protection | Rejected | `hub/world/HubPvpZoneWandListener.java` |
| 28 | `/hch hubworld pvp setspawn` | PvP zone respawn saved | `hub/command/HubWorldAdminCommands.java` |
| 29 | Enter PvP zone | Auto-activates PvP; forces sword slot | `hub/world/HubWorldManager.java` |
| 30 | Die in zone | Respawn: zone spawn > pad > hub spawn | `hub/world/HubWorldManager.java` |

### 5.5 PvP pads

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 31 | `/hch hubworld pvp pad` | Golden pressure plate item | `hub/command/HubWorldAdminCommands.java` |
| 32 | OP places pad in hub | Registered in `hub-world.yml` | `hub/world/HubWorldManager.java` |
| 33 | Step on pad with PvP ON | `PVP Respawn Activated` subtitle | `hub/world/HubWorldManager.java` |
| 34 | Step same pad repeatedly | No message spam | `hub/world/HubWorldManager.java` |
| 35 | Re-activate PvP, step pad again | Message shows again | `hub/world/HubWorldManager.java` |
| 36 | Step pad with PvP OFF | Not activated + sword hint | `hub/world/HubWorldManager.java` |
| 37 | PvP OFF pad warning | 5-minute cooldown | `hub/world/HubWorldManager.java` |
| 38 | Die with active pad | Respawn at pad location | `hub/world/HubWorldManager.java` |

### 5.6 Limbo PvP (different from hub)

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 39 | PvP sword activation in limbo | Same countdown behavior | `hub/limbo/HubPreMatchLimboArenaListener.java` |
| 40 | PvP off in limbo | Does not teleport | `hub/limbo/HubPreMatchLimboArenaListener.java` |
| 41 | PvP off in limbo | Keeps current held slot | `hub/limbo/HubPreMatchLimboArenaListener.java` |

- ⬜ Phase 5 complete

---

## Phase 6 — Iron Legacy admin & template editing

### 6.1 Template editor behavior

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 1 | `/hch template worlds tp testmap` | Creative editor mode | `iron-legacy/integration/IronLegacyTemplateAdminImpl.java` |
| 2 | Editor world | Time = 1000, no daylight/weather cycle | `iron-legacy/managers/TemplateWorldManager.java` |
| 3 | Leave and re-enter editor | Editor inventory remembered | `iron-legacy/managers/TemplateWorldManager.java` |
| 4 | Publish template | Gamerule values applied | `iron-legacy/managers/TemplateWorldManager.java` |
| 5 | Delete template with players inside | Fails safely | `iron-legacy/integration/IronLegacyTemplateAdminImpl.java` |
| 6 | Delete empty template | Removed from `worlds.yml` + disk | `iron-legacy/managers/TemplateWorldManager.java` |

### 6.2 Spawn pads

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 7 | `/il addspawn` | Spawn pad at feet | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/SpawnManager.java` |
| 8 | `/il listspawns` | Correct count | `iron-legacy/managers/SpawnManager.java` |
| 9 | `/il showpads 10` | Particle visualization | `iron-legacy/managers/SpawnManager.java` |
| 10 | `/il addspawnring 8 20 here` | Ring of pads | `iron-legacy/managers/SpawnManager.java` |
| 11 | `/il removespawn` | Nearest pad removed | `iron-legacy/managers/SpawnManager.java` |
| 12 | `/il clearspawns` | All pads cleared | `iron-legacy/managers/SpawnManager.java` |

### 6.3 Game rules & toggles

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 13 | `/il rules` | Rule summary | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/BuildRuleManager.java` |
| 14 | `/il crafting on` / `off` | Crafting toggle | `iron-legacy/commands/HGCommand.java`, `iron-legacy/listeners/RulesListener.java` |
| 15 | `/il build enable/disable/add/remove/list` | Build whitelist/blacklist | `iron-legacy/managers/BuildRuleManager.java` |
| 16 | `/il dig enable/disable/add/remove/list` | Dig whitelist/blacklist | `iron-legacy/managers/BuildRuleManager.java` |
| 17 | `/il set teamsize 2` | Team size updated | `iron-legacy/commands/HGCommand.java`, `iron-legacy/config.yml` |
| 18 | `/il set countdown 10` | Countdown updated | `iron-legacy/commands/HGCommand.java` |
| 19 | `/il border set 500 50 60 120` | Border shrink configured | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/GameManager.java` |
| 20 | `/il toggle deathmsgs` | Death message toggle | `iron-legacy/commands/HGCommand.java` |
| 21 | `/il toggle cannon` | Launch cannon toggle | `iron-legacy/commands/HGCommand.java` |
| 22 | `/il toggle defaultteams` | Auto-team toggle | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/TeamManager.java` |
| 23 | `/il toggle launchonmove` | Freeze punishment toggle | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/GameManager.java` |
| 24 | `/il reload` | Configs, spawns, rules, templates reload | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/ConfigFilesManager.java` |

### 6.4 IL admin GUIs

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 25 | `/il gui` | Main or Teams menu | `iron-legacy/gui/MainMenu.java`, `iron-legacy/gui/TeamsMenu.java` |
| 26 | `/il admin` | Admin Control Center | `iron-legacy/gui/AdminControlCenterMenu.java` |
| 27 | Core Settings menu | Values update + persist | `iron-legacy/gui/CoreSettingsAdminMenu.java` |
| 28 | Game Worlds list/settings | Per-map settings | `iron-legacy/gui/GameWorldsListMenu.java`, `iron-legacy/gui/GameWorldSettingsMenu.java` |
| 29 | Worlds Admin / Hub Settings | Hub integration settings | `iron-legacy/gui/WorldsAdminRootMenu.java`, `iron-legacy/gui/WorldsHubSettingsMenu.java` |
| 30 | Team Name Database admin | CRUD works | `iron-legacy/gui/TeamNameDatabaseAdminMenu.java`, `iron-legacy/managers/TeamNameDatabaseManager.java` |
| 31 | Back navigation in submenus | Returns to parent | `iron-legacy/gui/MenuListener.java`, `iron-legacy/gui/MenuView.java` |

- ⬜ Phase 6 complete

---

## Phase 7 — Envoy & Replenish (per-map scoped)

**Stand in template editor or match instance** — not hub.

Scoped data: `plugins/HCIronLegacy/world-envoys/<template-key>/envoys.yml` + `replenish.yml`

### 7.1 Access & scope

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 1 | `/il envoy gui` in hub | Fails or warns | `iron-legacy/gui/EnvoyAdminMenu.java`, `iron-legacy/util/WorldEnvoyStorage.java` |
| 2 | `/il envoy gui` in template world | Envoy Admin with map key in title | `iron-legacy/gui/EnvoyAdminMenu.java` |
| 3 | Replenish Admin from control center | Same world requirement | `iron-legacy/gui/ReplenishAdminMenu.java` |
| 4 | Four tabs visible | Spawns · Types · Replenish · General | `iron-legacy/gui/EnvoyAdminMenu.java` |

### 7.2 Envoy setup

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 5 | Add envoy spawn via GUI/wand | Scoped `envoys.yml` only | `iron-legacy/gui/EnvoySpawnsMenu.java`, `iron-legacy/util/WorldEnvoyStorage.java` |
| 6 | Remove spawn from GUI | Correct world + coords | `iron-legacy/gui/EnvoySpawnsMenu.java` |
| 7 | Types & packages editors | Scoped `envoys.yml` | `iron-legacy/gui/EnvoyTypesMenu.java`, `iron-legacy/gui/EnvoyPackagesMenu.java` |
| 8 | General settings: interval, wave, fall speed, etc. | Persist in scoped YAML | `iron-legacy/gui/EnvoyGeneralSettingsMenu.java` |
| 9 | `/il envoy addloc` | Adds location for current template | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/EnvoyManager.java` |
| 10 | `/il envoy listlocs` | Lists scoped locations | `iron-legacy/managers/EnvoyManager.java` |
| 11 | `/il envoy clearlocs` | Clears scoped locations | `iron-legacy/managers/EnvoyManager.java` |
| 12 | `/il envoy wave` | Works without running ticker | `iron-legacy/managers/EnvoyManager.java` |
| 13 | `/il envoy random` | Random spawn behavior | `iron-legacy/managers/EnvoyManager.java` |
| 14 | `/il addchests give` + wand mode | Envoy chest placement | `iron-legacy/managers/AddChestModeManager.java` |

### 7.3 Replenish setup

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 15 | Replenish wand places chest | Scoped `replenish.yml` | `iron-legacy/managers/ReplenishModeManager.java`, `iron-legacy/util/WorldEnvoyStorage.java` |
| 16 | Remove chest from GUI | Correct world from row | `iron-legacy/gui/ReplenishChestsMenu.java` |
| 17 | Tier loot edits T1–T4 | Persist in scoped YAML | `iron-legacy/gui/ReplenishAdminMenu.java` |
| 18 | General: interval, first refill delay, repeats | Persist | `iron-legacy/gui/ReplenishAdminMenu.java` |
| 19 | Toggles: random forager, tiered packages | Persist | `iron-legacy/gui/ReplenishAdminMenu.java` |
| 20 | `/il replenish T1` (manual) | Fills tier chests | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/ReplenishManager.java` |
| 21 | `/il replenish mode give` + wand | Replenish placement mode | `iron-legacy/managers/ReplenishModeManager.java` |

### 7.4 Cross-map regression

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 22 | Configure Map A and Map B separately | Each has own `world-envoys/<key>/` | `iron-legacy/util/WorldEnvoyStorage.java` |
| 23 | Start match on Map A | Map B chests/spawns unaffected | `iron-legacy/managers/ReplenishManager.java`, `iron-legacy/managers/EnvoyManager.java` |
| 24 | Hub world | Never receives replenish fills | `iron-legacy/managers/ReplenishManager.java`, `iron-legacy/integration/IlGameWorldScopeResolver.java` |

- ⬜ Phase 7 complete

---

## Phase 8 — Full match gameplay

### 8.1 Match start sequence

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 1 | `/il start` from limbo | Staged countdown (not instant TP) | `iron-legacy/managers/GameManager.java`, `iron-legacy/integration/IronLegacyLimboBridgeImpl.java` |
| 2 | Pre-teleport phase | 5..1 "DO NOT MOVE" | `iron-legacy/managers/GameManager.java` |
| 3 | Teleport timing | Correct moment | `iron-legacy/managers/GameManager.java` |
| 4 | Post-teleport freeze | Hidden delay before long countdown | `iron-legacy/managers/GameManager.java` |
| 5 | Freeze countdown | One number at a time, center screen | `iron-legacy/managers/GameManager.java` |
| 6 | `launchOnMove` toggle | Punishment follows setting | `iron-legacy/managers/GameManager.java` |
| 7 | BEGIN prompt | Correct moment | `iron-legacy/managers/GameManager.java` |

### 8.2 In-match replenish runtime

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 8 | Replenish schedule starts with match | Hooks into begin-countdown | `iron-legacy/managers/ReplenishManager.java` |
| 9 | First refill timing | ~20s after schedule start | `iron-legacy/managers/ReplenishManager.java` |
| 10 | Subsequent refills | Interval from scoped `replenish.yml` | `iron-legacy/managers/ReplenishManager.java` |
| 11 | Tier progression | T1→T4 per `repeatsPerTier` | `iron-legacy/managers/ReplenishManager.java` |
| 12 | Chest fill coordinates | Same XYZ as editor, on instance | `iron-legacy/managers/ReplenishManager.java` |
| 13 | Break tracked replenish chest | Blocked | `iron-legacy/listeners/ReplenishChestProtectionListener.java` |
| 14 | Creeper/TNT on tracked chest | Chest not destroyed | `iron-legacy/listeners/ReplenishChestProtectionListener.java` |

### 8.3 In-match envoy runtime

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 15 | `/il envoy start` in match | Uses scoped `envoys.yml` | `iron-legacy/managers/EnvoyManager.java` |
| 16 | Falling crates / landing | Per timing/visual settings | `iron-legacy/crates/FallingCrate.java`, `iron-legacy/listeners/EnvoyListener.java` |
| 17 | Right-click envoy package | Opens loot | `iron-legacy/listeners/EnvoyListener.java` |
| 18 | Breaking envoy crate | Does not incorrectly drop loot | `iron-legacy/listeners/EnvoyListener.java` |
| 19 | Explosion on envoy block | Block not destroyed | `iron-legacy/listeners/EnvoyListener.java` |
| 20 | Tiered packages | Loot matches scoped types/tiers | `iron-legacy/managers/EnvoyManager.java`, `iron-legacy/crates/CrateType.java` |

### 8.4 Death, elimination, audio

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 21 | Player dies in match | Removed from match systems | `iron-legacy/managers/GameManager.java` |
| 22 | Dead player | Sent out of match world | `iron-legacy/managers/GameManager.java`, `iron-legacy/integration/HubIntegrationRouting.java` |
| 23 | Death boom sound | Only same-match players hear it | `iron-legacy/managers/GameManager.java` |
| 24 | Players in hub/other lobbies | Do not hear death boom | `iron-legacy/managers/GameManager.java` |
| 25 | Valid kill | Leaderboard increments | `iron-legacy/managers/KillLeaderboardManager.java` |
| 26 | `/il giftdrop` | Gift drops for online players | `iron-legacy/managers/GiftDropManager.java`, `iron-legacy/listeners/GiftDropListener.java` |

### 8.5 Border & endgame

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 27 | Border shrink | Starts only when RUNNING | `iron-legacy/managers/GameManager.java` |
| 28 | Border prompt text | Correct styling, no spam | `iron-legacy/managers/GameManager.java` |
| 29 | Last player standing | Winner title + invulnerability | `iron-legacy/managers/GameManager.java` |
| 30 | Winner return | Hub after delay | `iron-legacy/managers/GameManager.java`, `iron-legacy/integration/HubIntegrationRouting.java` |
| 31 | Match cleanup | All remaining players evacuated | `iron-legacy/managers/GameManager.java` |
| 32 | Instance world | Destroyed after match | `hub/integration/HubIronLegacyMatchInstanceRuntime.java` |
| 33 | Next match from same template | Clean state | `iron-legacy/managers/TemplateWorldManager.java`, `hub/util/WorldFolderCopier.java` |
| 34 | Limbo zone/session | Reset for next run | `hub/limbo/HubPreMatchLimboService.java` |
| 35 | Replenish/envoy | Stops on match end | `iron-legacy/managers/ReplenishManager.java`, `iron-legacy/managers/EnvoyManager.java` |

### 8.6 Teams (limbo + match)

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 36 | `/il teams` in limbo | Hub party or limbo teams menu | `iron-legacy/commands/HGCommand.java`, `iron-legacy/gui/TeamsMenu.java` |
| 37 | Create team in limbo | Works | `iron-legacy/managers/TeamManager.java`, `hub/team/HubTeamService.java` |
| 38 | Invite/join/color menus | Paging + back buttons | `iron-legacy/gui/InviteMenu.java`, `iron-legacy/gui/JoinTeamMenu.java`, `iron-legacy/gui/TeamColorMenu.java` |
| 39 | Team cap | Enforces limbo team size max | `iron-legacy/managers/TeamManager.java`, `hub/team/HubTeamService.java` |
| 40 | `/il randomteams 2` | Wipes and randomizes | `iron-legacy/commands/HGCommand.java`, `iron-legacy/managers/TeamManager.java` |
| 41 | Team chat prefix/color | Works if chat plugin uses API | `iron-legacy/integration/IronLegacyTeamChatProvider.java`, `api/chat/TeamChatProvider.java` |

- ⬜ Phase 8 complete

---

## Phase 9 — Persistence & restart

### 9.1 Cold restart (YAML storage)

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 1 | Restart server | All three plugins enable | All plugin entrypoints |
| 2 | `/hub` | Correct spawn | `hub/platform/DefaultGamePlatform.java`, `hub/src/main/resources/config.yml` |
| 3 | `templates.yml` | testmap row persists | `hub/template/HubTemplateRegistry.java` |
| 4 | `hub-world.yml` | PvP zone, pads, radius persist | `hub/world/HubWorldYaml.java` |
| 5 | `parties.yml` | Party state reasonable | `hub/party/HubPartyService.java`, `hub/storage/HubYamlStorageProvider.java` |
| 6 | `lobby-instances.yml` | OPEN lobbies restored or empty | `hub/lobby/HubLobbyInstanceRegistry.java` |
| 7 | IL `worlds.yml` | Template + limbo zone persist | `iron-legacy/managers/WorldSettingsManager.java` |
| 8 | `world-envoys/<key>/` | Scoped YAML persists | `iron-legacy/util/WorldEnvoyStorage.java` |
| 9 | Kill leaderboard | Persists across restart | `iron-legacy/managers/KillLeaderboardManager.java` |
| 10 | No stale runtime worlds | No orphaned `*-M#` / `*-L#` loaded | `hub/lobby/HubLobbyService.java`, `iron-legacy/managers/GameManager.java` |

### 9.2 MySQL storage (optional)

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 11 | Parties persist in MySQL | Survive restart | `hub/party/HubPartyJdbcSync.java`, `hub/storage/HubSqlMigrations.java` |
| 12 | Lobby instances persist | Survive restart | `hub/lobby/HubLobbyJdbcSync.java` |
| 13 | Queue snapshots persist | `hc_queue_snapshot` populated | `hub/storage/HubSqlMigrations.java`, `hub/queue/HubMatchQueueService.java` |
| 14 | MySQL down at startup | SEVERE log + YAML fallback | `hub/storage/HubSqlRuntime.java`, `hub/HollowCrownHubPlugin.java` |

### 9.3 Edge cases on reconnect

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 15 | All party members offline 3+ min | Party auto-disbands | `hub/party/HubPartyService.java` |
| 16 | Leader offline 3+ min | New leader promoted | `hub/party/HubPartyService.java` |
| 17 | Player quits while in queue | Auto-removed | `hub/party/HubPartyListener.java` |
| 18 | Reconnect in build mode | Build inventory not wiped | `hub/world/HubWorldManager.java` |

- ⬜ Phase 9 complete

---

## Phase 10 — Multi-server / optional integrations

Skip unless you run Velocity, resource packs, or Environmental Hazards.

### 10.1 Velocity proxy

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 1 | `velocity.enabled: true` + proxy | Cross-server connect works | `iron-legacy/managers/VelocityBridgeManager.java`, `iron-legacy/gameflow.yml` |
| 2 | Post-match return | Routes to `hubServerId` | `iron-legacy/integration/HubIntegrationRouting.java` |
| 3 | Hub target is this server | No useless Velocity hop | `iron-legacy/integration/HubIntegrationRouting.java` |

### 10.2 Lobby sharding

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 4 | Hub shard boundaries | Players clamped to shard | `iron-legacy/listeners/HubShardBoundaryListener.java` |
| 5 | `lobbyShardPlayers` cap | New shard when full | `iron-legacy/managers/LobbyShardManager.java`, `iron-legacy/gameflow.yml` |

### 10.3 Resource pack

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 6 | Return to hub after match | Resource pack prompt (if configured) | `iron-legacy/managers/ResourcePackManager.java`, `iron-legacy/resourcepack.yml` |

### 10.4 Environmental Hazards plugin

| # | Action | Expected | Fix in |
|---|--------|----------|--------|
| 7 | Template publish/delete | Hazard cleanup triggered | `iron-legacy/integration/EnvironmentalHazardsBridge.java`, `api/hazard/*` |
| 8 | Match end | Hazard cleanup triggered | `iron-legacy/integration/EnvironmentalHazardsBridge.java` |

- ⬜ Phase 10 complete (or ⚠️ skipped)

---

## 15-minute smoke test (minimum)

| Step | Action | Fix in |
|------|--------|--------|
| 1 | Phase 0 console checks | `foundation/`, `hub/HollowCrownHubPlugin.java`, `iron-legacy/HungerGamesPlugin.java` |
| 2 | `/hch sethub` → `/hub` → `/hch join` → join lobby | `hub/command/*`, `hub/lobby/HubLobbyService.java` |
| 3 | `/hch party create` + invite + accept | `hub/party/*` |
| 4 | Compass → Game Lobby → limbo → `/il start` | `hub/gui/*`, `hub/limbo/*`, `iron-legacy/integration/IronLegacyLimboBridgeImpl.java` |
| 5 | `/il stop` → `/hub` | `iron-legacy/managers/GameManager.java`, `hub/command/HubPlayerCommand.java` |

---

## Cross-plugin integration map (when unsure where to fix)

| Symptom area | Primary fix in | API contract (if shared) |
|--------------|----------------|--------------------------|
| Plugin won't load / schema error | `foundation/`, `api/minigame/MinigameBootstrap.java` | `api/HollowCrownApiVersion.java` |
| Hub spawn, `/hub`, loadout | `hub/world/HubWorldManager.java` | `api/integration/HubWorldApi.java` |
| Templates, join GUI, queue policy | `hub/template/*`, `hub/gui/LobbyJoin*.java` | `api/template/TemplateRegistry.java` |
| Lobby instances `*-L#` | `hub/lobby/HubLobbyService.java` | `api/platform/GamePlatform.java` |
| Parties & invites | `hub/party/*` | `api/party/PartyService.java` |
| Match queue | `hub/queue/HubMatchQueueService.java` | `api/queue/MatchQueueService.java` |
| Pre-match limbo | `hub/limbo/HubPreMatchLimboService.java` | `api/integration/PreMatchLimboService.java` |
| Limbo → match launch | `iron-legacy/integration/IronLegacyLimboBridgeImpl.java` | `api/integration/IronLegacyLimboBridge.java` |
| Match instances `*-M#` | `hub/integration/HubIronLegacyMatchInstanceRuntime.java` | `api/integration/IronLegacyMatchInstanceRuntime.java` |
| Match gameplay (border, death, winner) | `iron-legacy/managers/GameManager.java` | — |
| IL template publish/rollback | `iron-legacy/managers/TemplateWorldManager.java` | `api/integration/IronLegacyTemplateAdmin.java` |
| Hub PvP kill credit | `iron-legacy/integration/IronLegacyHubBridgeImpl.java` | `api/integration/IronLegacyHubBridge.java` |
| Hub admin GUIs for IL | `hub/gui/HubIl*.java` | `api/integration/HubAdminGui.java` |
| Storage YAML vs MySQL | `hub/storage/*` | `api/storage/StorageProvider.java` |

---

## Bug report template

```
What you did:
What you expected:
What happened:
Console log (HC-Foundation / HollowCrownHub / HCIronLegacy):
/plugins list:
Template key / world name:
Player account(s) used:
Storage backend (YAML / MYSQL):
Suspected fix in (from table):
```

---

## Final sign-off

| Result | When |
|--------|------|
| ⬜ **PASS** | All critical paths in Phases 0–9 verified |
| ⬜ **PASS WITH NOTES** | Minor polish only |
| ⬜ **FAIL** | Blocking gameplay or admin issue |

| Phase | Tester | Date | Notes |
|-------|--------|------|-------|
| 0–5 Hub core | | | |
| 6–8 Iron Legacy | | | |
| 9 Persistence | | | |
| 10 Optional | | | |

---

## Related checklists

- Deep Iron Legacy sections (envoy, replenish, border detail): `minigames/iron-legacy/IN_GAME_QA_CHECKLIST.md` — use **Fix in** column above for code paths; that file still lists some **deprecated `/il` commands** (see migration table at top of this doc).
