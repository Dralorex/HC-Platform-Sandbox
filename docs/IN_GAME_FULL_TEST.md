# Hollow Crown Platform — full in-game test

Use this on a **Paper 1.21.4** test server with **two accounts** (one OP, one normal player) when possible.

Mark each line: ✅ pass · ❌ fail · ⚠️ skipped · ⬜ not tested

---

## Phase 0 — Server setup (before joining)

### JARs and load order

Copy from `mvn clean package -DskipTests`:

| # | File | Plugin name in console |
|---|------|-------------------------|
| 1 | `foundation/target/HC-Foundation-*.jar` | **HC-Foundation** |
| 2 | `hub/target/HollowCrownHub-*.jar` | **HollowCrownHub** |
| 3 | `minigames/iron-legacy/target/HCIronLegacy-*.jar` | **HCIronLegacy** |

Do **not** put `hollowcrown-api` in `plugins/` (library only).

### Console on startup (must see)

- ⬜ `HC-Foundation` enables (loads at **STARTUP**)
- ⬜ `HollowCrownHub` enables — log mentions API schema, storage `YAML` or `MYSQL`
- ⬜ `HCIronLegacy` enables — log mentions **mechanic registered: id=iron-legacy**
- ⬜ No `HCIronLegacy disabled` / missing Hub messages
- ⬜ `/plugins` shows all three green, correct order

### Fail fast if

- ❌ Iron Legacy disables → Foundation or Hub missing / wrong names
- ❌ `IronLegacyLimboBridge not registered` → IL disabled or load order wrong
- ❌ `storage.type=MYSQL` but SEVERE JDBC errors → fix config or use YAML

### Test world prep

- ⬜ One world as **hub** (flat lobby or built hub)
- ⬜ One **template source** world folder on disk (for copy → lobby / match)
- ⬜ OP has `hollowcrownhub.admin` and `hcironlegacy.admin` (default for OP)

---

## Phase 1 — Hub bootstrap (OP)

Run in the hub world.

| Step | Command / action | Expected |
|------|------------------|----------|
| 1 | `/hch version` | Shows Hub version + API schema |
| 2 | `/hch sethub` | Hub spawn saved; no error |
| 3 | `/hch debug mechanics` | Lists **`iron-legacy`** |
| 4 | `/hch template list` | Lists templates (may be empty) |
| 5 | `/hch template set testmap iron-legacy "Test Map"` | Template row created |
| 6 | `/hch template setsource testmap <folderName>` | Source world set (folder exists under server root) |
| 7 | `/hch hubworld` (run help / set radius if needed) | Hub protection config loads |
| 8 | `/hch gui help` | Admin GUI help text |

- ⬜ All Phase 1 steps pass

---

## Phase 2 — Hub player flow (normal player)

Account **without** OP (or survival player).

| Step | Command / action | Expected |
|------|------------------|----------|
| 1 | `/hub` | Teleports to hub spawn; hub hotbar/items apply |
| 2 | `/hch join` | Opens **join template** GUI |
| 3 | Left-click **testmap** in join GUI | Teleports to lobby **or** creates `testmap-L#` and joins |
| 4 | Right-click **testmap** | Opens **lobby instance** picker for that template |
| 5 | `/hch party create` | Party created |
| 6 | `/hch party invite <otherPlayer>` | Other gets invite (paper / message) |
| 7 | Other: `/hch party accept <id>` | Joins party |
| 8 | Leader `/hub` | **Whole party** teleports to hub (online members) |
| 9 | `/hch queue join testmap` | Queued message with position |
| 10 | `/hch queue status testmap` | Shows queue units |
| 11 | `/hch queue leave` | Leaves queue |

**Known gap (document, not a fail unless you expect auto-start):**

- ⚠️ Queue does **not** auto-send players to limbo/match yet — enqueue only. Use Game Lobby / limbo flow below for match path.

- ⬜ Phase 2 complete

---

## Phase 3 — Hub lobbies (OP)

| Step | Command / action | Expected |
|------|------------------|----------|
| 1 | `/hch lobby list` | Shows OPEN instances for templates |
| 2 | `/hch lobby create testmap` | New world `testmap-L1` (or L2…) loads |
| 3 | Console / IL log | **Hub lobby instance ready** for template testmap |
| 4 | `/hch lobby gui` | Admin lobby GUI opens |
| 5 | Normal player joins that instance via `/hch join` | Teleport into `testmap-L#` |
| 6 | `/hch lobby tp <id>` | OP teleports to instance |
| 7 | `/hch lobby destroy <id>` | World unloaded + folder removed (or BROKEN + forget) |
| 8 | `/hch debug why <playerName>` | Snapshot: party, queue, world context |

- ⬜ Phase 3 complete

---

## Phase 4 — Iron Legacy + Hub integration

### 4a) Hub return and menus

| Step | Action | Expected |
|------|--------|----------|
| 1 | In hub: right-click **compass** (locked hotbar) | **Game Lobby** GUI opens |
| 2 | Pick **testmap** (enabled template) | Limbo or template lobby flow starts |
| 3 | `/il help` | Help lists `/hub`, party, admin sections |
| 4 | `/il party` | Opens **Hub** party menu (not IL-only) |
| 5 | `/hub` | Same spawn as `/hch sethub` |

- ⬜ 4a complete

### 4b) Limbo (Hub + IL)

Use `/hch template worlds` / limbo wand **or** IL game menus per your setup.

| Step | Action | Expected |
|------|--------|----------|
| 1 | `/hch template worlds` help | Subcommands documented |
| 2 | Get limbo wand (Hub/IL command per your GUI) | Wand in inventory |
| 3 | Set zone corners + confirm | Outline visible; `worlds.yml` updated under `plugins/HCIronLegacy/` |
| 4 | Stand in zone: set limbo spawn | Spawn saved |
| 5 | Join limbo via Game Lobby | Adventure mode, limbo loadout, zone clamp |
| 6 | Wait for fill / `/il start` (OP) | Countdown → match transition |
| 7 | Barrier / leave item | Returns to hub spawn; removed from limbo session |

- ⬜ 4b complete

### 4c) Match (minimal smoke)

| Step | Action | Expected |
|------|--------|----------|
| 1 | After limbo start: players in **match instance** world | New or cloned world, not hub |
| 2 | `/il stop` (OP) | Match stops; players sent toward hub |
| 3 | Teams / spawn pads (if configured) | See Iron Legacy checklist §11+ |

- ⬜ 4c complete

### 4d) Templates via Hub (admin)

| Step | Command | Expected |
|------|---------|----------|
| 1 | `/hch gui maps` | Template / IL browser opens |
| 2 | `/hch template worlds list` | Reads IL `worlds.yml` rows |
| 3 | `/il admin` | IL control center still works |

- ⬜ 4d complete

---

## Phase 5 — Hub world rules (hub spawn world)

| Step | Action | Expected |
|------|--------|----------|
| 1 | Non-OP breaks block in hub | Blocked (unless build mode) |
| 2 | OP hub build mode (via `/hch hubworld` / hotbar) | Can build if enabled |
| 3 | Hold **sword** in hub | PvP activation countdown → PvP on |
| 4 | PvP on: hit another player | Damage works; kill credit behaves |
| 5 | Drop sword / deactivate | PvP off; teleport to hub spawn (hub rules) |
| 6 | Golden pressure plate pad (if configured) | Respawn pad messaging |

- ⬜ Phase 5 complete

---

## Phase 6 — Persistence and reload

| Step | Action | Expected |
|------|--------|----------|
| 1 | Restart server | All three plugins enable again |
| 2 | `/hub` | Still correct spawn |
| 3 | `plugins/HollowCrownHub/templates.yml` | Still has testmap |
| 4 | `plugins/HollowCrownHub/parties.yml` | Party state reasonable after restart |
| 5 | `/hch lobby list` | Lobby instances restored or cleanly empty (YAML/SQL) |

- ⬜ Phase 6 complete

---

## Phase 7 — Deep Iron Legacy (optional, long)

For envoy, replenish, border, teams, velocity, etc., use the full checklist:

**`minigames/iron-legacy/IN_GAME_QA_CHECKLIST.md`**

Run sections 5–17 after Phase 4 passes.

---

## Quick smoke (15 minutes)

If short on time, only do:

1. Phase 0 console checks  
2. `/hch sethub` → `/hub` → `/hch join` → join lobby  
3. `/hch party create` + invite + accept  
4. Compass → Game Lobby → limbo → `/il start`  
5. `/il stop` → back to hub  

---

## Bug report template

When something fails, note:

```
What you did:
What you expected:
What happened:
Console log (HC-Foundation / HollowCrownHub / HCIronLegacy):
/plugins list:
Template key / world name:
```

Paste into an issue or Discord for fixes.
