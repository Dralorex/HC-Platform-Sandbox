# HC-Iron-Legacy In-Game QA Checklist

Use one symbol per line when testing:

| Symbol | Meaning |
|--------|---------|
| ✅ | Pass |
| ❌ | Fail |
| ⚠️ | Couldn’t test |
| ⬜ | Not tested yet |

Test in **section order** when possible: it follows a typical **chronological player + OP setup path** (lobby → tools → maps → limbo → social → match → post-match → persistence).

---

## 1) Hub / Lobby Core Flow

*First join baseline.*

- ⬜ `/il setlobby` sets exact location + facing correctly  
- ⬜ `/il lobby` and `/il hub` always teleport to exact saved lobby spawn  
- ⬜ New player joins and gets correct hub loadout  
- ⬜ Compass opens Game Lobby GUI (right click only)  
- ⬜ Hub utility items are in expected slots  
- ⬜ Unexpected/duplicate locked utility items are cleaned up  
- ⬜ Lobby border clamps normal players correctly  
- ⬜ OP in build mode can bypass lobby border  
- ⬜ Mob spawning is blocked in hub/lobby  
- ⬜ Hub/lobby always stays day + weather off  

---

## 2) Build Mode / OP Editing

*Before editing templates or hub structures.*

- ⬜ `/il buildmode true` sets Creative and allows full inventory freedom  
- ⬜ `/il buildmode false` sets Survival and reapplies hub items  
- ⬜ Turning buildmode back on restores previous build inventory snapshot  
- ⬜ Buildmode OP does not get forced hub item normalization  
- ⬜ Reconnect while buildmode active does not wipe build inventory  

---

## 3) Admin GUI / Settings

*Smoke-test admin tooling early; persistence covered again in §17.*

- ⬜ `/il admin` opens control center  
- ⬜ Core Settings menu opens and updates values correctly  
- ⬜ Left/right click actions match item lore  
- ⬜ Back navigation in admin submenus works  
- ⬜ Config changes persist after `/il reload` or equivalent admin reload path  

---

## 4) Template World Edit / Publish

*Create and freeze map versions before limbo/envoy tuning.*

- ⬜ `/il create <world>` creates template and teleports to editor  
- ⬜ `/il gameedit <world>` teleports to editor  
- ⬜ Editor mode sets: time = 1000, `doDaylightCycle=false`, `doWeatherCycle=false`  
- ⬜ Editor inventory is remembered per player/template  
- ⬜ Leaving and re-entering editor restores exact prior editor inventory  
- ⬜ `/il game set <world> gamerule dodaylightcycle true/false` works  
- ⬜ `/il game set <world> gamerule doweathercycle true/false` works  
- ⬜ Publish applies saved publish gamerule values  
- ⬜ Default publish behavior still: daylight on, weather cycle off  
- ⬜ Snapshot/publish/rollback/delete commands work end-to-end  

---

## 5) Envoy & Replenish — Per-Map Configuration

*Scoped data lives under `plugins/HCIronLegacy/world-envoys/<template-key>/` (`envoys.yml`, `replenish.yml`). Stand **in the template editor world or a runtime match instance** for these—not hub.*

### 5a) Access & scope

- ⬜ Opening **Envoy Admin** from `/il envoy gui` **fails or warns** in hub / non-template worlds  
- ⬜ Opening **Envoy Admin** from Admin Control Center **in a template/match world** shows title including **map key**  
- ⬜ **Replenish Admin** from control center **requires** template/match world (same rule)  
- ⬜ Four top tabs layout: **Spawns · Types · Replenish · General settings** (even spacing)  

### 5b) Envoy spawns & types

- ⬜ Envoy spawn list reads/writes **`world-envoys/<key>/envoys.yml`** only for current map  
- ⬜ Spawn wand / add mode adds/removes spawns for **this** map only  
- ⬜ Removing a spawn from GUI uses **correct world + coords** (not viewer world by mistake)  
- ⬜ Types & packages editors save to **scoped** `envoys.yml`  
- ⬜ **Active packages** tracking menu only lists packages in worlds that resolve to **this template key**  

### 5c) Replenish chests

- ⬜ Replenish chest list reads/writes **`world-envoys/<key>/replenish.yml`** for current map  
- ⬜ Replenish wand places/removes tracked chests for **this** map only  
- ⬜ Removing a chest from GUI uses **correct world** from row display name  
- ⬜ Tier loot edits (T1–T4) in scoped `replenish.yml` apply when triggering tiers from Replenish Admin  

### 5d) General settings tab

- ⬜ Adjust **spawn interval**, **min/max wave**, **fall speed**, **parachute**, **spawn height**, **batch** — values persist in scoped `envoys.yml`  
- ⬜ Adjust **replenish interval**, **first refill delay**, **repeats per tier** — persist in scoped `replenish.yml`  
- ⬜ Toggles: **random forager**, **tiered packages** persist  

### 5e) Commands (`/il envoy …`)

- ⬜ `start` / `addloc` / `clearlocs` / `listlocs` / `wave` / `random` resolve **current world’s template** and affect **that** map’s files  
- ⬜ `wave` works **without** a running envoy ticker (uses scoped YAML)  

---

## 6) Limbo Setup + Zone

*Per-template limbo tooling.*

- ⬜ `/il game create limbozone <world>` gives Limbo Wand  
- ⬜ Wand: left = corner 1, right = corner 2, shift-right = confirm  
- ⬜ Limbo outline appears after both corners set  
- ⬜ Outline updates instantly when corners are changed  
- ⬜ `/il game set limbo spawn` works only inside zone  
- ⬜ Players entering limbo spawn at configured limbo spawn  
- ⬜ Limbo players are forced Adventure mode  
- ⬜ Limbo inventory loadout applies correctly  
- ⬜ Leaving limbo via barrier item sends player to exact hub/lobby spawn  
- ⬜ Leaving limbo removes player from limbo count/session  

---

## 7) Hub / Limbo PvP Mode

*Lobby-adjacent combat toggles.*

- ⬜ Holding sword starts activation countdown (timing correct)  
- ⬜ Deactivating sword starts deactivation countdown (timing correct)  
- ⬜ Countdown + activated/deactivated messages display in title/subtitle style  
- ⬜ PvP only active when sword mode is active  
- ⬜ Respawn invincibility is 3s with yellow particle ring  
- ⬜ Invincible players cannot deal or receive PvP damage  
- ⬜ PvP OFF in hub teleports player back to lobby spawn  
- ⬜ PvP OFF in limbo does **not** teleport player anywhere  
- ⬜ Limbo PvP off keeps current held slot (no forced slot swap)  

---

## 8) PvP Pad System

*Depends on PvP mode behavior.*

- ⬜ `/il pvp pad` gives golden pressure plate item  
- ⬜ OP can place pad and it registers  
- ⬜ Stepping on pad with PvP ON sets temp respawn  
- ⬜ Subtitle shows: `PVP Respawn Activated` when new temp spawn is set  
- ⬜ Stepping same pad repeatedly does not spam repeat message  
- ⬜ Re-activating PvP allows same pad to trigger message again  
- ⬜ Stepping pad with PvP OFF shows:  
  - Main: `PVP Mode Not Activated`  
  - Sub: `Hold the Sword to Activate PVP`  
- ⬜ PvP OFF pad warning has 5-minute cooldown  
- ⬜ PvP death respawns at temp pad if active  
- ⬜ If no temp pad, respawns at correct default spawn for area  

---

## 9) Party System

*Pre-queue grouping.*

- ⬜ Party main GUI layout is correct  
- ⬜ Submenu back button is row 4 slot 5  
- ⬜ Prev page arrow only on page 2+ (row 4 slot 1)  
- ⬜ Next page arrow only when needed (row 4 slot 9)  
- ⬜ Invite Players excludes self + existing party members  
- ⬜ Party Members list uses join order  
- ⬜ Leader/member labels display correctly  
- ⬜ Invite papers show: Left accept / Right decline  
- ⬜ Left click accepts invite  
- ⬜ Right click declines invite  
- ⬜ Invites expire after 5 minutes  
- ⬜ Accepting one invite auto-declines others with correct messages  
- ⬜ Manual decline sends correct single decline message  

---

## 10) Limbo Team System

*In limbo before launch.*

- ⬜ Main Limbo Teams menu is 3 rows  
- ⬜ Create Team works  
- ⬜ Invite Players tab works (heads + paging + back layout)  
- ⬜ Team Members tab works (heads + paging + back layout)  
- ⬜ Team Invites tab works (accept/decline behavior)  
- ⬜ Team cap checks enforce limbo team size max  
- ⬜ Team invites only target eligible players in limbo  

---

## 11) Match Start / Countdown / Freeze

*Transition into runtime instance.*

- ⬜ `/il start` uses staged countdown flow (not instant TP)  
- ⬜ Pre-teleport 5..1 DO NOT MOVE phase works  
- ⬜ Teleport occurs at correct time  
- ⬜ Post-teleport hidden freeze delay works before long countdown starts  
- ⬜ Freeze countdown displays one number at a time center-screen  
- ⬜ Freeze punishment mode follows current toggle (`launchOnMove`)  
- ⬜ BEGIN prompt appears at correct moment  

---

## 12) In-Match — Replenish & Envoy Runtime

*Validate automation **during** an active match on a **cloned instance**.*

### 12a) Replenish schedule & tiers

- ⬜ Replenish schedule starts with match flow (same timing hook as begin-countdown path)  
- ⬜ **First refill** occurs **~20s** after schedule starts (not instant T1 at second zero unless configured)  
- ⬜ Subsequent refills use **interval** from scoped `replenish.yml` (default **30 min** game-time expectation — shorten in QA via General settings for practical runs)  
- ⬜ After each tier cycle, **tier index advances** per `repeatsPerTier` (T1→T4 progression matches config)  
- ⬜ Chests at template-recorded **coordinates** fill on the **instance world** (same XYZ as editor)  

### 12b) Replenish chest protection

- ⬜ Players **cannot break** tracked replenish chests (survival/creative)  
- ⬜ Mobs **cannot destroy** tracked chests (e.g. creeper explosion removes chest from world: **should not**)  
- ⬜ **TNT / explosions** do not break tracked replenish chests  

### 12c) Envoy loot packages

- ⬜ `/il envoy start` from a match/template world uses **scoped** `envoys.yml` for that map  
- ⬜ Falling crates / landing packages behave per scoped **timing/visual** settings  
- ⬜ **Right-click** opens envoy package; **breaking** does **not** consume crate or drop loot incorrectly  
- ⬜ Explosions do **not** break placed envoy package blocks  
- ⬜ Loot rolls match **scoped** types/tiers when tiers enabled  

### 12d) Cross-talk regression

- ⬜ Starting a match on **Map A** does **not** refill chests or consume spawns belonging to **Map B**  
- ⬜ Hub world never receives replenish fills from an unrelated template  

---

## 13) In-Match Death / Audio / Elimination

- ⬜ In-game death removes player from match systems immediately  
- ⬜ Dead player is sent out of match correctly  
- ⬜ Death boom only heard by active players in same match world  
- ⬜ Players in hub/other lobbies/other games do not hear that boom  
- ⬜ Kill leaderboard increments correctly for valid kills  

---

## 14) Border + Endgame

- ⬜ Border shrink starts only when match is actually running  
- ⬜ Border prompt text is correct and styled as requested  
- ⬜ No old verbose border chat spam remains  
- ⬜ Winner only gets winner title/invulnerability behavior  
- ⬜ Winner is returned to hub after delay  
- ⬜ Match cleanup kicks all remaining players safely  
- ⬜ Runtime instance world is destroyed after match  
- ⬜ Next match starts from clean template state (no leftover damage/build)  
- ⬜ Limbo structures/zone state reset correctly for next run  
- ⬜ Replenish schedule **stops** on match end; **match context** cleared (no refill firing with no game)  

---

## 15) Restart / Persistence Validation

*Cold-start and disk state.*

- ⬜ Restart server: lobby spawn persists  
- ⬜ Restart server: PvP pads persist  
- ⬜ Restart server: template metadata persists  
- ⬜ Restart server: publish gamerules persist  
- ⬜ Restart server: kill leaderboard persists  
- ⬜ Restart server: no stale runtime worlds remain active  
- ⬜ Restart server: **`world-envoys/<key>/envoys.yml`** and **`replenish.yml`** persist per template  
- ⬜ Legacy root `envoys.yml` / `replenish.yml` (if still present) do **not** silently override scoped maps after migration  

---

## Final QA Result

*(Fill in after completing sections relevant to your release.)*

- ⬜ **PASS** — all critical systems verified  
- ⬜ **PASS WITH NOTES** — minor polish only  
- ⬜ **FAIL** — blocking gameplay/admin issue  

---

## Notes column *(optional)*

Use for build ID, tester initials, links to tickets, or **actual timings** when you shorten replenish interval for QA.

| Section | Notes |
|---------|-------|
| | |
