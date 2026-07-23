# Hollow Crown Platform — full in-game test

Use this on a **Paper 1.21.4** test server with **three accounts** when possible:

| Account | Role |
|---------|------|
| **OP** | Admin setup, `/il start`, GUI config |
| **Player A** | Party leader / team leader |
| **Player B** | Party member / enemy team |

Mark each line: ✅ pass · ❌ fail · ⚠️ skipped · ⬜ not tested

**Current bundle tag:** `HC-Plugins-a0.1.10.zip` (Hub `1.21.4-a0.1.10`, Iron Legacy `1.21.4-a0.3.10`, Foundation `1.21.4-a0.1.4`)

### Changelog → test phase map

Use this to confirm nothing shipped since the last doc pass is untested.

| Change (since a0.1.4) | Test phase |
|------------------------|------------|
| Lobby worlds under `hch-worlds/minigames/<template>-L#` | 0, 3 |
| Lobby prefetch when **all** OPEN lobbies are **≥50%** full | 3b, 3c |
| Auto-join creates new lobby when no OPEN lobby fits party size | 3c |
| Party-too-big **trim GUI** (skull heads) on manual lobby join | 3c |
| Publish destroys lobbies, notifies players, prefetches fresh lobby | 3b, 4e |
| Publish blocked without mechanic; type **yes** in chat → vanilla + retry | 3b |
| Limbo spawn on lobby join + pre-match registration | 3, 4b |
| `/hub` and `/hub join <n>` leave limbo like Leave Lobby | 2, 2d, 4a, 4b |
| `/hch sethub [n]` / `/hch clearhub [n]` numbered hub spawns | 1, 2d |
| Failed lobby / kick / evacuate → hub spawn (leaves limbo) | 4e |
| Limbo zone clears **after** spawn-pad teleports | 4c |
| Re-publish takes fresh snapshot every time | 4b-2 |
| `/hch template worlds edit` only (no `tp` alias) | 4b |
| `/hch template worlds mechanic <world\|key> <mechanic>` | 1, 4a |
| `/hch worldsgui` and compass **mechanic tabs** → worlds → lobbies | 4a |
| Limbo wand auto-detects template from current world | 4b, 4b-3 |
| `/il start` force-starts below min fill | 4b |
| Natural countdown cancels on low fill (non-force) | 4b-4 |
| Spawn-pad misconfig complaint message | 4c |
| Match spawn: 2.5 bars saturation | 4c |
| PvP zone wand / setspawn / pad; outline only while holding wand | 5b |
| PvP: swap **off** sword starts deactivate countdown; death spawn rules | 5, 5b |
| Hub return restores build mode + inventory snapshot | 2c |
| `/hch help` hides admin subcommands from non-ops | 2 |
| Plugin policy command blocking (hub / limbo / match) | 6 |
| Per-template Match policy overrides global Match | 6b, 6c |
| Plugin Allocation landing (Hub / Limbo / Match tabs) | 4d, 6 |
| Template policy GUIs **save on close**; kick from lobbies on change | 6b, 7c |
| Per-template General Settings + blacklist GUI | 6b, 7 |
| **Team Mode**: auto team colors; **Legacy**: wool picker in limbo | 4b-5 |
| Match teams **Team 1 / Team 2 / …**; independent team invites in limbo | 4b-5, 4b-7 |
| Parties separate from teams; team resets each lobby/match | 4b-7, 10 |
| Party hub return with overflow hubs (`hub.instanceCapacity`) | 2, 2d, 4c |
| Alliance FF toggles, last-group break (match) | 7, 8 |
| Per-viewer nametags / TAB / chat / sneak hide | 9 |
| Party leave limbo drags whole party | 4b |
| Match Team menu (slot 3) + lobby entry team prompts | 4b, 4b-5 |
| Multi-template limbo/publish key resolution | 4b-2 |
| Auto-register template in `template-policies.yml` on publish | 6b, 10 |

**Not in-game testable:** `HubPluginPolicyApi` (cooperative API for other HC plugins) — verify via integration code only.

---

## Phase 0 — Server setup (before joining)

### JARs and load order

Copy from `mvn clean package -DskipTests` or deploy zip:

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
- ⬜ **Two or more** template source worlds on disk (multi-template regression)
- ⬜ OP has `hollowcrownhub.admin` and `hcironlegacy.admin` (default for OP)
- ⬜ Each template has limbo zone + spawn set in `plugins/HCIronLegacy/worlds.yml`
- ⬜ After first lobby create, disk has `hch-worlds/minigames/<template>-L1/` under the server root (not under `plugins/`)

---

## Phase 1 — Hub bootstrap (OP)

Run in the hub world.

| Step | Command / action | Expected |
|------|------------------|----------|
| 1 | `/hch version` | Shows Hub version + API schema |
| 2 | `/hch sethub` | Hub spawn saved; no error |
| 2a | `/hch sethub 2` at a second location | Numbered hub spawn **2** saved |
| 2b | `/hch clearhub 2` | Spawn **2** removed |
| 3 | `/hch debug mechanics` | Lists **`iron-legacy`** |
| 4 | `/hch template list` | Lists templates (may be empty) |
| 5 | `/hch template set testmap iron-legacy "Test Map"` | Template row created |
| 6 | `/hch template set testmap2 iron-legacy "Test Map 2"` | Second template row created |
| 7 | `/hch template setsource testmap <folderA>` | Source world set |
| 8 | `/hch template setsource testmap2 <folderB>` | Second source world set |
| 9 | `/hch template worlds mechanic testmap iron-legacy` | Mechanic assigned (required before publish) |
| 10 | `/hch hubworld` (run help / set radius if needed) | Hub protection config loads |
| 11 | `/hch gui help` | Admin GUI help text |

- ⬜ All Phase 1 steps pass

---

## Phase 2 — Hub player flow (normal player)

Account **without** OP.

| Step | Command / action | Expected |
|------|------------------|----------|
| 1 | `/hub` | Teleports to hub spawn; hub hotbar/items apply |
| 1a | `/hub join 2` (after OP set spawn 2) | Teleports to numbered hub spawn **2** |
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
| 12 | `/hch help` (non-OP) | Shows **party** help only — no admin subcommands |
| 13 | `/hch help` (OP) | Full admin help visible |

**Known gap (document, not a fail unless you expect auto-start):**

- ⚠️ Queue does **not** auto-send players to limbo/match yet — enqueue only. Use Game Lobby / limbo flow below for match path.

- ⬜ Phase 2 complete

---

## Phase 2d — Numbered hub spawns (OP + player)

| Step | Action | Expected |
|------|--------|----------|
| 1 | OP: `/hch sethub` at main hub point | Default spawn **1** (or primary) saved |
| 2 | OP: `/hch sethub 2` at a second point | Spawn **2** saved |
| 3 | Player: `/hub join 2` | Teleports to spawn **2** |
| 4 | Player: `/hub join 999` (invalid) | Error / fallback to default spawn |
| 5 | OP: `/hch clearhub 2` | Spawn **2** removed; `/hub join 2` no longer works |

- ⬜ Phase 2d complete

---

## Phase 2b — Party messaging (2+ players)

| Step | Action | Expected |
|------|--------|----------|
| 1 | A invites B who is **already in a party** | Message: **`[B's colored name] is already in a party`** (bronze-ish if no team color) |
| 2 | Leader A leaves party | Next member promoted; others see: **Party Leader [A's colored name] has left the Party. You have been promoted to Party Leader!** |
| 3 | Leader A kicks B | B removed; others see: **Party Leader [A's colored name] has been kicked from the Party. You have been promoted to Party Leader!** |

- ⬜ Phase 2b complete

---

## Phase 2c — Build mode restore (OP)

| Step | Action | Expected |
|------|--------|----------|
| 1 | OP: `/hch hubworld buildmode true` in hub | Creative + prior inventory snapshot restored |
| 2 | Enter limbo or match, then `/hub` or match end | Returns to hub in **creative build mode** with saved hotbar/inventory |
| 3 | `/hch hubworld buildmode false` | Survival hub utility loadout (compass, party item, PvP sword) |

- ⬜ Phase 2c complete

---

## Phase 3 — Hub lobbies (OP)

| Step | Command / action | Expected |
|------|------------------|----------|
| 1 | `/hch lobby list` | Shows OPEN instances for templates |
| 2 | `/hch lobby create testmap` | New world `testmap-L1` (or L2…) loads |
| 3 | Console / IL log | **Hub lobby instance ready** for template testmap |
| 4 | `/hch lobby gui` | Admin lobby GUI opens |
| 5 | Normal player joins that instance via `/hch join` | Teleport into `testmap-L#` at **limbo spawn** (not world default spawn if configured) |
| 6 | `/hch lobby tp <id>` | OP teleports to instance |
| 7 | End match or `/hch lobby destroy <id>` | World unloaded + folder removed (or BROKEN + forget) |
| 8 | If folder stuck: create again | Uses **L2/L3** instead of failing on stale `L1` folder |
| 9 | `/hch debug why <playerName>` | Snapshot: party, queue, world context |

- ⬜ Phase 3 complete

---

## Phase 3b — Lobby prefetch & publish lifecycle (OP + players)

| Step | Action | Expected |
|------|--------|----------|
| 1 | `/hch lobby list testmap` — note only **one** OPEN instance | Baseline |
| 2 | Fill that lobby; start match (natural or `/il start`) | Match begins; lobby row detached from registry |
| 3 | `/hch lobby list testmap` shortly after | **New** OPEN lobby prefetched (`match_start` in console: `action=lobby_prefetch`) |
| 3a | Fill **every** OPEN lobby to **≥50%** capacity (without starting a match) | Another OPEN lobby prefetched (`reason=capacity` or similar in console) |
| 4 | Put players in a **second** OPEN lobby for same template (if any) | Skip if only one instance exists |
| 5 | OP: `/hch template worlds publish testmap` (or GUI Publish on template detail) | Old lobby instance(s) destroyed; players in those worlds evacuated to hub |
| 5a | Publish with **no mechanic** set; type **`yes`** in chat when prompted | Mechanic set to **vanilla**; publish retries and succeeds |
| 6 | Evacuated players | Chat: **All lobbies for this game were closed because the world was updated. Please join a new lobby from the game menu.** |
| 7 | `/hch lobby list testmap` after publish | Fresh OPEN lobby exists (prefetch `reason=publish`) |
| 8 | `/hch lobby forget <id>` on a **BROKEN** row only | Row removed from registry (does not delete disk if already broken) |

- ⬜ Phase 3b complete

---

## Phase 3c — Auto-join capacity & party trim GUI

Requires a party larger than a lobby's remaining slots (or a nearly-full lobby).

| Step | Action | Expected |
|------|--------|----------|
| 1 | Create party of 3+; pick a lobby instance with **1–2 slots left** via compass lobby list | Trim GUI opens (skull heads) |
| 2 | Confirm trim | Last-joined party members kicked until party fits; leader + remainder join |
| 3 | Party of 2; auto-join when **no** OPEN lobby has room | New lobby created automatically (`testmap-L#`) and party joins |
| 4 | All OPEN lobbies ≥50% full; auto-join compass flow | Fresh lobby prefetched if needed (see Phase 3b step 3a) |

- ⬜ Phase 3c complete

---

## Phase 4 — Iron Legacy + Hub integration

### 4a) Hub return and menus

| Step | Action | Expected |
|------|--------|----------|
| 1 | In hub: right-click **compass** (locked hotbar) | **Game Lobby** GUI opens with **mechanic tabs** (e.g. Iron Legacy, Vanilla) |
| 1a | Select mechanic tab → pick template world | Worlds for that mechanic listed |
| 1b | Auto-join button on a world | Joins fitting lobby or creates new one |
| 1c | Lobby list button on a world | Paginated lobby instance picker |
| 2 | `/hch worldsgui` or `/hch gui worldsgui` | Same worlds browser as compass (admin path) |
| 3 | Pick **testmap** (enabled template) | Limbo or template lobby flow starts |
| 4 | `/il help` | Help lists `/hub`, party, admin sections |
| 5 | `/il party` | Opens **Hub** party menu (not IL-only) |
| 6 | `/hub` and `/hub join <n>` | Same spawn as `/hch sethub`; **leaves limbo** same as Leave Lobby item |

- ⬜ 4a complete

### 4b) Limbo (Hub + IL)

| Step | Action | Expected |
|------|--------|----------|
| 1 | `/hch template worlds help` | Subcommands documented |
| 2 | `/hch template worlds edit <world\|key>` | Teleports to template editor (**only** edit path; no `tp` alias) |
| 3 | Get limbo wand (Hub/IL command per your GUI) | Wand in inventory |
| 3a | `/hch template limbo wand` (no world arg) | Universal wand; auto-detects template from **current world** |
| 3b | Admin GUI → `/hch gui maps` → template → **Limbo wand** button | Same wand as CLI |
| 4 | Set zone corners + confirm (shift-right-click) | Outline while selecting; `worlds.yml` updated under `plugins/HCIronLegacy/` |
| 5 | Stand in zone: `/hch template limbo spawn` or `/hch template set limbo spawn` | Spawn saved |
| 5a | `/hch template worlds reload` after manual YAML edit | IL picks up changes without full restart |
| 6 | Join limbo via Game Lobby | Adventure mode, limbo loadout, zone clamp |
| 7 | **Lobby entry team prompt** (solo / leader / member) | Title + chat tailored to party role |
| 8 | Limbo hotbar **slot 3**: **Match Team** item | Opens Match Team menu |
| 9 | Party menu in limbo → **Match Team** button | Same menu as slot 3 |
| 10 | **Leave Lobby** item (leader, party in same session) | **Whole party** pulled to hub; leader: *Left limbo queue. Your party came with you.*; members: *[Leader colored name] took the party back to the hub.* |
| 11 | Wait for fill **or** `/il start` (OP) | Countdown → match transition |
| 12 | `/il start` with **below minimum players** | **Force starts** anyway (no "not enough players" cancel) |
| 13 | Barrier / leave item (solo) | Returns to hub spawn; removed from limbo session |
| 14 | Limbo hotbar: **Party** item opens Hub party menu | Same menu as `/hch party` / `/il party` |
| 15 | Limbo hotbar: **Leave Lobby** (non-leader solo) | Solo returns to hub only |

- ⬜ 4b complete

### 4b-4) Natural fill vs force start (min fill %)

Set template **Min fill %** high enough that 1–2 players is below threshold (General Settings or `/hch template set … minimumpercentage`).

| Step | Action | Expected |
|------|--------|----------|
| 1 | Join limbo with **below** required fill; wait for natural countdown | At start attempt: **Match start canceled: not enough players** or **Timer canceled: not enough players. A new 5 minute countdown starts once fill is sufficient.** |
| 2 | Same session: OP `/il start` with still-below fill | **Force starts** — no cancel message |

- ⬜ 4b-4 complete

### 4b-5) Match Team menu — Team Mode vs Legacy Mode

Teams are **independent from parties**. Use Match Team (slot 3) or Party menu → Match Team.

#### Create & invite (no party required)

| Step | Action | Expected |
|------|--------|----------|
| 1 | Solo player opens Match Team | **Create Team** (Team Mode) or wool swatches (Legacy) |
| 2 | Click **Create Team** (Team Mode) | Becomes **Team 1** (or next free number); nametag `[Team 1] Name` |
| 3 | Leader: **Invite Players** → pick online player | Target gets chat hint; **Team Invites** shows pending |
| 4 | Target: Match Team → **Team Invites** → accept | Joins same **Team N**; both see green teammate names |
| 5 | Invite when team at **teamSizeMax** | **Team is full** — cannot accept more |
| 6 | Party member **not** auto-added when accepting **party** invite | Must accept **team** invite separately |

#### Team Mode (default)

| Step | Action | Expected |
|------|--------|----------|
| 1 | Create team | Name **Team 1**, **Team 2**, … (not usernames) |
| 2 | Re-enter same lobby after prior match | Fresh **Team N** allocation (old team cleared on lobby join) |
| 3 | Look at teammate in limbo | **`[Team N]`** prefix + green name |

#### Legacy Mode

| Step | Action | Expected |
|------|--------|----------|
| 1 | Create team via wool swatch | **Team N** with chosen legacy color |
| 2 | **Team Members** / **Leave Team** | Roster and leave work like party menu |

- ⬜ 4b-5 complete

### 4b-7) Party vs team independence

| Step | Action | Expected |
|------|--------|----------|
| 1 | A and B in **party** but on **different teams** (or B solo on team) | Allowed |
| 2 | Match ends (`/il stop` or natural) | **Party persists**; **teams cleared** (`teams.yml` empty) |
| 3 | Party leader `/hub` after match | Whole party routed to same hub when capacity allows |
| 4 | Party larger than `hub.instanceCapacity` with 2+ hubs configured | Overflow members sent to next hub (not over cap) |
| 5 | Re-join lobby limbo | New team must be created/invited; old Team N not reused from prior match |

- ⬜ 4b-7 complete

### 4b-6) Limbo alliances & nametags (before match start)

Requires **Allies** ON in template General Settings. Need **3 teams** (3 party leaders) still in limbo.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Team A leader: `/ally Team 2` in limbo | Team 2 leader gets invite + `/ally accept Team 1` hint |
| 2 | Team 2 leader: `/ally accept Team 1` | Both teams: **An ally has been formed with …**; nametags refresh |
| 3 | Team A viewers look at Team 2 | **Blue** player names (allies) in limbo |
| 4 | Team A leader: `/enemy Team 3` | Team A members: **Team 3 has been deemed an enemy!**; Team 3 shows **orange** to Team A |
| 5 | Allied hit test in limbo (Ally FF **OFF**) | Damage **blocked**; action bar hint |
| 6 | Start match (`/il start` or natural countdown) | Same `[Team N]` prefixes and ally/enemy colors **carry into match** |
| 7 | `/ally` or `/enemy` in **hub** (not limbo/match) | **You are not on a team** / config not found — blocked outside template worlds |

- ⬜ 4b-6 complete

### 4b-2) Multi-template limbo (regression)

Repeat key steps for **testmap2** (second template with different Hub key / IL worldName):

| Step | Action | Expected |
|------|--------|----------|
| 1 | `/hch lobby create testmap2` | Lobby loads |
| 2 | Player joins testmap2 lobby | Spawns at **testmap2's** limbo spawn (not testmap's) |
| 3 | Limbo zone clamp works | Player kept inside testmap2 zone |
| 4 | `/hch template worlds publish testmap2` | Publishes successfully (key resolves via source-world fallback) |
| 5 | Re-publish after edits | New matches use updated snapshot |

- ⬜ 4b-2 complete

### 4c) Match (minimal smoke)

| Step | Action | Expected |
|------|--------|----------|
| 1 | After limbo start: players in **match instance** world | New or cloned world, not hub |
| 2 | On spawn: health, hunger, saturation | Full health + hunger; **2.5 bars saturation** (not full) |
| 3 | Limbo zone after pads | Zone cleared to **air**; non-player entities removed (**after** teleport to pads) |
| 4 | `/il stop` (OP) | Match stops; players sent toward hub |
| 5 | Lobby folder after match | Lobby world folder deleted (or BROKEN + L# fallback on next create) |
| 6 | Spawn pad failure (misconfigured template) | Kicked players see: **Someone messed up! Go complain to someone that "SpawnPads" have NOT been set up correctly!** |
| 7 | Teams / spawn pads (if configured) | See Iron Legacy checklist §11+ |

- ⬜ 4c complete

### 4e) Evacuation paths (hub spawn)

| Step | Action | Expected |
|------|--------|----------|
| 1 | Player in limbo; OP `/hch lobby destroy <id>` for their lobby | Teleport to hub spawn; leaves limbo session |
| 2 | Failed match start / IL kick path (if reproducible) | Hub spawn, not limbo or void |
| 3 | Template publish while player still in old lobby (see Phase 3b) | Hub spawn + publish notice |

- ⬜ 4e complete

### 4d) Templates via Hub (admin)

| Step | Command | Expected |
|------|---------|----------|
| 1 | `/hch gui` or `/hch gui admin` | Admin home opens |
| 2 | Click **Plugin policies** tile | Opens **Plugin Allocation** landing (Hub / Limbo / Match tabs) — not the old flat editor |
| 3 | `/hch gui maps` | Template browser → per-template detail (publish, rollback, limbo wand, edit) |
| 4 | `/hch gui templates` | `templates.yml` browser |
| 5 | `/hch gui queues` | Queue overview GUI |
| 6 | `/hch gui lobby` | Lobby instance admin GUI |
| 7 | `/hch template worlds list` | Reads IL `worlds.yml` rows |
| 8 | `/hch template worlds importhub` | Adds Hub `templates.yml` rows missing from IL catalog |
| 9 | `/il admin` | IL control center still works |

- ⬜ 4d complete

---

## Phase 5 — Hub world rules (hub spawn world)

| Step | Action | Expected |
|------|--------|----------|
| 1 | Non-OP breaks block in hub | Blocked (unless build mode) |
| 2 | OP hub build mode (via `/hch hubworld` / hotbar) | Can build if enabled |
| 3 | Hold **sword** in hub **outside** PvP zone | 10-second activation countdown → PvP on |
| 3a | Swap **off** the PvP sword (different hotbar slot) | Deactivate countdown starts (same as intentional deactivate) |
| 4 | PvP on: hit another player | Damage works; kill credit behaves |
| 5 | Golden ring during hub/limbo PvP | Visible while PvP mode active |
| 6 | Die in **hub PvP** while in PvP **zone** | Respawn at PvP zone spawn (not default hub spawn if zone set) |
| 7 | Die in **hub PvP** outside zone | Respawn at default hub spawn |
| 8 | Die in **match** (not hub PvP) | **No** golden invulnerability ring after respawn/death |
| 9 | Drop sword / deactivate | PvP off; teleport to hub spawn only if in zone or on pad (see 5b) |
| 10 | Golden pressure plate pad (if configured) | Respawn pad messaging |

- ⬜ Phase 5 complete

---

## Phase 5b — PvP zones (hub protection area)

| Step | Action | Expected |
|------|--------|----------|
| 1 | OP: `/hch hubworld pvp wand` | Blaze rod wand in inventory |
| 2 | Select zone corners inside hub protection | Cyan outline **while holding wand** |
| 3 | Swap to another hotbar slot | Outline **stops** (wand-only preview) |
| 4 | Shift-right-click confirm zone | Zone saved to hub config |
| 5 | `/hch hubworld pvp setspawn` inside zone | PvP respawn point saved |
| 6 | `/hch hubworld pvp pad` | Golden pressure plate item for respawn pad |
| 6a | Walk into PvP zone while **selecting the sword** (hotbar slot 1) | PvP activates **immediately** — no 10s countdown |
| 6b | Deactivate PvP in zone (swap off sword), leave zone, re-enter | Sword selected again + PvP re-activates **every** re-entry |
| 7 | Activate PvP (hold sword), fight inside zone | PvP works; golden ring while active |
| 8 | Deactivate PvP **inside** zone (swap off sword; wait for 5s countdown **or** die during countdown) | Teleport to hub spawn; PvP off |
| 9 | Activate PvP **outside** zone (hub protection but not zone), then deactivate | **No** teleport (only zone/pad triggers return) |
| 10 | Step on PvP pad without PvP active | Title: **PVP Mode Not Activated** / hold sword hint (rate-limited) |

- ⬜ Phase 5b complete

---

## Phase 6 — Plugin allocation (OP admin GUI)

Path: **Admin home → Plugin policies → Plugin Allocation landing**

### 6a) Hub and Limbo scopes

| Step | Action | Expected |
|------|--------|----------|
| 1 | Open **Hub** tab | Scope-locked whitelist/blacklist menu |
| 2 | Toggle wool (white = whitelist, black = blacklist) | Mode switches; saves to `plugin-policies.yml` |
| 3 | Click a plugin **book** | Adds/removes from list |
| 4 | **HollowCrownHub** and **HC-Foundation** | Always allowed (not blockable) |
| 5 | Repeat for **Limbo** tab | Same behavior in limbo scope |
| 6 | In hub: run command from **blacklisted** plugin | Blocked with scope message |
| 7 | Back button | Returns to Plugin Allocation landing |

- ⬜ 6a complete

### 6b) Match scope — per-template

| Step | Action | Expected |
|------|--------|----------|
| 1 | Open **Match** tab | Paginated alphabetical template list |
| 2 | Click **testmap** | Per-template landing: **General Settings** + **Plugin Allocation** |
| 3 | **General Settings** tab | Toggles and ± controls (see Phase 7) |
| 4 | **Plugin Allocation** tab | Per-template allow/block list |
| 5 | Blacklist plugin X for testmap only | Blocked in testmap **match** worlds |
| 6 | Same plugin in testmap2 match (not listed) | **Works** if global Match allows (per-template overrides global Match) |
| 7 | Publish new template | Auto-added to template list + `template-policies.yml` row |

- ⬜ 6b complete

### 6c) Cross-scope policy (limbo vs match)

Pick a third-party plugin with a simple command (e.g. economy, homes — **not** Hub/IL).

| Step | Action | Expected |
|------|--------|----------|
| 1 | Blacklist plugin X in **Limbo** tab only | Saved |
| 2 | In limbo: run plugin X command | **That plugin is disabled here (limbo).** |
| 3 | Same players in **match** world (plugin X not on template blacklist) | Command **works** |
| 4 | Add plugin X to template **Plugin Allocation** blacklist | Blocked in that template's match with **(match)** scope message |

- ⬜ 6c complete

### 6d) Whitelist mode

| Step | Action | Expected |
|------|--------|----------|
| 1 | Hub tab: switch to **whitelist** (white wool) | Only listed plugins allowed in hub |
| 2 | Unlisted plugin command in hub | Blocked |
| 3 | Listed plugin command in hub | Works |
| 4 | Per-template Match whitelist (blacklist tab + white wool) | Only listed plugins work in that match |

- ⬜ 6d complete

### 6e) Global Match fallback (`plugin-policies.yml`)

Per-template Match policy **overrides** the global Match list when the player is in a known template's match world. The landing **Match** tab opens the template list only; global Match scope lives in `plugins/HollowCrownHub/plugin-policies.yml` (or legacy unlocked policy GUI if you open it without a locked scope).

| Step | Action | Expected |
|------|--------|----------|
| 1 | In `plugin-policies.yml`, blacklist plugin Y under **match** scope | Saved on reload/restart |
| 2 | Start match on template with **empty** per-template plugin list | Plugin Y command blocked (**match** scope message) |
| 3 | Remove Y from global match list; add Y only to **testmap** template blacklist | Blocked in testmap match only; works in testmap2 match |

- ⬜ 6e complete

---

## Phase 7 — Per-template general settings (OP)

Open: **Plugin Allocation → Match → [template] → General Settings**

| Step | Toggle / control | Default | Test |
|------|------------------|---------|------|
| 1 | **Color Mode** (Team / Legacy) | Team | Toggle; match nametags follow mode (Phase 9) |
| 2 | **Sneak hides name tag** | ON | Sneak: hidden from non-teammates; teammates still see name |
| 3 | **TAB list visible** | OFF | TAB shows nothing in match when OFF |
| 4 | **Allies (/ally)** | OFF | Enable before Phase 8 |
| 5 | **Max teams per alliance** | 2 | ± adjusts cap |
| 6 | **Ally Friendly Fire** | ON | Allied teams can damage each other when ON |
| 7 | **Team Friendly Fire** | ON | Same-team can damage each other when ON |
| 8 | **Team size max** | template | ± updates worlds.yml |
| 9 | **Min fill %** | template | ± updates limbo fill requirement |
| 10 | **Max players** | template | ± updates worlds.yml |
| 11 | **Teleport to template editor** | — | Same as `/hch template worlds edit <key>` |

**Friendly fire matrix (match, allies enabled, two allied teams + one enemy):**

| Ally FF | Team FF | Same team hit | Allied team hit | Enemy hit |
|---------|---------|---------------|-----------------|-----------|
| OFF | ON | Allowed if Team FF ON | **Blocked** | Allowed |
| ON | OFF | **Blocked** if Team FF OFF | Allowed if Ally FF ON | Allowed |
| OFF | OFF | Blocked | Blocked | Allowed |

- ⬜ Phase 7 complete

---

## Phase 7c — Policy save-on-close (OP)

| Step | Action | Expected |
|------|--------|----------|
| 1 | Put players in a **testmap** lobby (not in match) | Baseline |
| 2 | OP: open **General Settings** for testmap; change a toggle (e.g. Allies ON→OFF); **close** GUI without a separate Save button | Changes persisted to `template-policies.yml` |
| 3 | Players still in testmap lobby after close | Kicked to hub with republish-style message (policy changed) |
| 4 | Re-open General Settings | Toggle reflects saved value |

- ⬜ Phase 7c complete

---

## Phase 8 — Alliance system (match, allies enabled)

Requires **Allies** ON in template General Settings. Need **3 teams** (3 party leaders minimum).

| Step | Action | Expected |
|------|--------|----------|
| 1 | Team A leader: `/ally TeamB` | Team B leader gets invite prompt + `/ally accept TeamA` hint |
| 2 | Team B leader: `/ally accept TeamA` | Both teams (all members): **An ally has been formed with [team]** |
| 3 | Non-leader tries `/ally` | Denied (leader-only) |
| 4 | Team A leader: `/enemy TeamC` | Team A members: **[TeamC] has been deemed an enemy!**; Team C shows **orange** to Team A viewers |
| 5 | Transitive ally: A↔B, B↔C | A and C treat each other as allies (blue) |
| 6 | Exceed max teams per alliance | Alliance rejected / cap enforced |
| 7 | Only one alliance group left alive | Title + chat: **Only one team will make it out alive.**; further `/ally` disabled |
| 8 | Allied players hit each other (Ally FF OFF) | Damage **blocked**; action bar hint |
| 9 | `/ally` or `/enemy` in **hub** (not limbo/match) | Blocked — not in a template lobby/match world |
| 10 | `/ally` / `/enemy` in **limbo** (allies enabled) | Works; see Phase **4b-6** for limbo colors + persistence |
| 11 | Allies **disabled** in template settings; `/ally` in match | **Alliances are not enabled in this match.** |
| 12 | Pending invite ignored ~60s | Invite expires silently; `/ally accept` says **No pending alliance invite** |
| 13 | Same-team hit with **Team FF OFF** | Damage blocked |

- ⬜ Phase 8 complete

---

## Phase 9 — Match nametags, TAB, chat (2+ players, in match)

Requires match started with teams assigned. Test in **Team Mode** first, then **Legacy Mode**.

### 9a) Team Mode (default)

| Step | Action | Expected |
|------|--------|----------|
| 1 | Look at teammate | **`[Team 1]`** (or Team 2, …) prefix + **green** player name — **same Team N for all teammates** |
| 2 | Look at enemy | **`[Team N]`** prefix + **red** player name |
| 3 | Look at ally (if allies on) | **blue** player name |
| 4 | Look at designated enemy (`/enemy`) | **orange** player name |
| 5 | TAB (if TAB visible ON) | Same relationship colors in list |
| 6 | TAB (if TAB visible OFF) | TAB key shows **no players** |
| 7 | Chat message | **`[Team N] PlayerName:`** message with relationship coloring per viewer |
| 8 | Death message | Victim name prefixed with **`[Team N]`** |
| 9 | Sneak (shift hide ON) | Nametag hidden from enemies/allies; **teammates still see name** |
| 10 | Ally/enemy set in limbo before start | Colors unchanged after match transition (regression with 4b-6 step 6) |

- ⬜ 9a complete

### 9c) Limbo nametags (pre-match)

Same as match relationship rules, but tested **before** `/il start`.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Two parties form **Team 1** and **Team 2** in limbo | Each sees own team **green**, other team **red** |
| 2 | `/ally accept` between teams in limbo | Cross-team names turn **blue** |
| 3 | `/enemy` marks third team | **Orange** for designated enemy (viewer-side) |
| 4 | Player with no team yet | Plain name (no `[Team N]` prefix) until assigned |

- ⬜ 9c complete

### 9b) Legacy Mode

| Step | Action | Expected |
|------|--------|----------|
| 1 | Toggle template to **Legacy** in General Settings | Saves |
| 2 | Start new match | Team prefix + name use **legacy 16-color** uniformly (no green/red/blue/orange per viewer) |

- ⬜ 9b complete

---

## Phase 10 — Persistence and reload

| Step | Action | Expected |
|------|--------|----------|
| 1 | Restart server | All three plugins enable again |
| 2 | `/hub` | Still correct spawn |
| 3 | `plugins/HollowCrownHub/templates.yml` | Still has testmap / testmap2 |
| 4 | `plugins/HollowCrownHub/template-policies.yml` | Per-template rows persisted |
| 5 | `plugins/HollowCrownHub/plugin-policies.yml` | Hub/Limbo/Match policy persisted |
| 6 | `plugins/HollowCrownHub/parties.yml` | Party state reasonable after restart |
| 7 | `plugins/HCIronLegacy/worlds.yml` | Limbo zones, spawns, publish flags persisted |
| 8 | `hch-worlds/minigames/` and `hch-worlds/templates/` | Lobby/template folders sane (may be empty after destroy) |
| 9 | `/hch lobby list` | Lobby instances restored or cleanly empty (YAML/SQL) |

- ⬜ Phase 10 complete

---

## Phase 11 — Deep Iron Legacy (optional, long)

For envoy, replenish, border, teams, velocity, etc., use the full checklist:

**`minigames/iron-legacy/IN_GAME_QA_CHECKLIST.md`**

Run sections 5–17 after Phase 4 passes.

---

## Quick smoke (20 minutes)

If short on time, only do:

1. Phase 0 console checks + `hch-worlds/minigames/` folder appears after lobby create  
2. `/hch sethub` + `/hch sethub 2` → `/hub` and `/hub join 2`  
3. `/hch join` or compass **mechanic tab** → join lobby (limbo spawn + team prompt)  
4. `/hch party create` + invite + accept + **leave limbo drags party**  
5. Match Team slot 3: **Team 1 / Team 2** naming + limbo nametag colors (4b-5, 9c)  
6. `/ally accept` + `/enemy` in limbo → colors persist into match (4b-6)  
7. Natural low-fill cancel **or** `/il start` force start  
8. Match spawn: 2.5 bars saturation; limbo zone cleared after pads  
9. Admin → **Plugin policies** → save-on-close kick (7c) → Limbo blacklist vs Match allow (6c)  
10. Publish with **yes** vanilla + evacuate message + prefetch (3b)  
11. `/il stop` → back to hub; lobby folder cleanup; build mode restore if OP (2c)  

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
Hub version / IL version:
plugin-policies.yml scope (if policy test):
template-policies.yml row (if template test):
```

Paste into an issue or Discord for fixes.
