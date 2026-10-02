# Changelog

All notable changes to Hollow Crown Platform are documented here.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

- **API** · **Foundation** · **Hub** · **Iron Legacy** — each has its own tag; same rule: bump only modules that changed.
- **Up tags** (user request): bump every edited plugin plus `hc.plugins.zip.tag`, then rebuild. Zip/ship alone does not bump tags.

## [Unreleased]

### Added

- `docs/ARCHITECTURE_FLOW.md` — full architecture flowchart: plugin/service graphs, match thread, and every Java file’s purpose + connections (api, foundation, hub, iron-legacy).

### Changed

- Hub TeamManager FakeTeam sync: dirty-flag heartbeat (idle steady-state ~0 team packets), world-scoped audiences instead of server-wide `getOnlinePlayers`, coalesced per-tick flush on roster/relationship edits, and per-player sync on join/world change. Relationship colors and leadership prompts unchanged.

### Fixed

- Match freeze: apply health/food/sat **once** at countdown start; move-damage unchanged. No hunger locks in matches.
- Hub/lobby: on PvP off / death → healed and full (no hunger lock). PvP on / match → vanilla sat drain and heal.

## [HC-Plugins-a0.1.24] - 2026-09-28

Tagged plugins: **API a0.1.15**, **Foundation a0.1.5**, **Hub a0.1.20**, **Iron Legacy a0.3.23**.

### Added

- Team Members Manager GUI for managing roster from the team menu (Hydraulic).
- Team members can claim leadership if the leader has been offline for 5 minutes (Hydraulic).

### Changed

- Renamed `TeamManagerInstance.allowTeamCustomization` to `gameNotRunning` (Hydraulic).

## [HC-Plugins-a0.1.23] - 2026-09-24

Tagged plugins: **API a0.1.15**, **Foundation a0.1.5**, **Hub a0.1.19**, **Iron Legacy a0.3.22**.

### Added

- In-game QA Excel workbook (`docs/IN_GAME_QA_GoogleSheets.xlsx`) and legend key (`docs/IN_GAME_QA_KEY.xlsx`).
- `/il clearspawn [closest|all]` — remove nearest pad or clear all pads for the current template.

### Fixed

- Startup / discover: prune template rows whose folders are gone from `hch-worlds/templates/` (Hub `templates.yml`, IL `worlds.yml`, lobbies, queues, policies).
- Non-ops no longer see technical match/lobby failure paths; they get `OOPS... something messed up! Please inform a Team Member!` (ops still see detail).
- Hub / pre-match lobby / Iron Legacy: no hunger–saturation healing while hub PvP is on or while a match is FREEZE/RUNNING; clear absorption on PvP activate and match countdown.
- After PvP deactivates, hub/lobby death, or match countdown start: full health, food 20, saturation 6/20 (≡ 3/10), no absorption/regen effects.
- Freeze countdown move penalty: play hurt sound, cancel move instead of teleporting (no camera snap); soft-leash snaps keep look direction.
- Pre-match lobby: locked utility items (sword/compass/party/leave) cannot be dropped or moved out of the hotbar.
- Match end / win return: clear participants and related match state in `stopGame` so hub/lobby PvP deaths no longer show "Your Legacy is Over".

### Changed

- Template lobby commands are `/hch template lobby wand`, `spawn`, and `help`. Internal and saved names that said limbo now say lobby. Hosting lobby worlds stays `/hch lobby`. Existing `worlds.yml` zone sections and plugin-policy scopes are copied onto the lobby keys on load.
- Hub protection is the square around hub spawn again (`/hch hubworld size`). A saved hub circle is turned off on load.

### Removed

- `/il clearspawns` and `/il removespawn` (use `/il clearspawn`).
- `/hch template set lobby spawn`.
- `/hch hubworld lobby wand` (circle selection now belongs on the template lobby wand).

## [HC-Plugins-a0.1.22] - 2026-09-23

Tagged plugins: **API a0.1.14**, **Foundation a0.1.5**, **Hub a0.1.18**, **Iron Legacy a0.3.21**.

### Added

- Circle lobby zone wand (`/hch hubworld lobby wand`): left-click center, right-click edge (radius + height), Shift+scroll cycles Center/Edge and locks that hotbar slot while holding the wand, particle border + action-bar status only while holding the wand, shift-right-click saves cylindrical hub protection to `hub-world.yml`.

### Changed

- Team invite GUI lists only online players in the same lobby who are not already on a team (Hydraulic).
- `/ally` and `/rival` aliases expanded (Hydraulic).

### Fixed

- Lobby leave barrier / solo hub routing use hub spawn #1 with liquid-safe stand locations.

## [HC-Plugins-a0.1.21] - 2026-09-21

Tagged plugins: **API a0.1.14**, **Foundation a0.1.5**, **Hub a0.1.17**, **Iron Legacy a0.3.21**.

### Added

- New Hub-owned teams system (`TeamManager` + FakeTeamAPI): create/join/invite/color GUIs, `/ally` and `/rival`, chat styling, and match win announcement for the winning team and players.

### Removed

- Legacy Hub `TeamService` / alliance stack and Iron Legacy’s old in-plugin `TeamManager` (teams are Hub-owned end-to-end).
- API `TeamService` / `TeamView` contracts replaced by the Hub team rewrite.

## [HC-Plugins-a0.1.20] - 2026-09-12

Tagged plugins: **API a0.1.13**, **Foundation a0.1.5**, **Hub a0.1.16**, **Iron Legacy a0.3.20**.

### Added

- Lobby join GUI (OP): **Create template world** on Vanilla / Iron Legacy / other mechanic tabs — chat prompts for file name and lobby display name, then creates the world and assigns that mechanic.

### Fixed

- Replenish wand placement faces the player (vanilla-style chest front).
- Breaking a tracked replenish chest with the Replenish Wand removes both the block and spawn data; without the wand, breaks are blocked even for OPs.

## [HC-Plugins-a0.1.19] - 2026-09-12

Tagged plugins: **API a0.1.13**, **Foundation a0.1.5**, **Hub a0.1.15**, **Iron Legacy a0.3.19**.

### Fixed

- Lobby spawn: IL now reloads `worlds.yml` from disk when Hub saves lobby spawn/zone; IL join path prefers Hub catalog (fresh disk) instead of stale in-memory config.
- Hub lobby join logs a warning when falling back to the world's vanilla spawn because lobby.x/y/z is missing.
- Match spawn pads are per-template in `worlds.yml` (`spawnPads`), survive publish, and no longer drop on reload when the editor world is unloaded. Legacy global `config.yml` `spawns.pads` migrate automatically.
- `/il start` is now OP-only (was usable by any player with `hcironlegacy.use`).

## [HC-Plugins-a0.1.18] - 2026-09-09

Tagged plugins: **API a0.1.13**, **Foundation a0.1.5**, **Hub a0.1.14**, **Iron Legacy a0.3.18**.

### Added

- Multi-phase world border shrink schedule (`worldborder.phases` in `config.yml`, per-map overrides via `world-game-settings.yml`) with `BorderShrinkManager`.
- GitHub Actions Maven publish workflow.

## [HC-Plugins-a0.1.17] - 2026-09-07

Tagged plugins: **API a0.1.13**, **Foundation a0.1.5**, **Hub a0.1.14**, **Iron Legacy a0.3.17**.

### Fixed

- Replenish auto-corrects common `replenish.yml` material typos on load (`CHAIMAIL_CHESTPLATE`, `LEATHER_LEGGINS`, `IRON` → `IRON_INGOT`) and saves the fixed file.

## [HC-Plugins-a0.1.16] - 2026-09-07

Tagged plugins: **API a0.1.13**, **Foundation a0.1.5**, **Hub a0.1.14**, **Iron Legacy a0.3.16**.

### Fixed

- Replenish chests were cleared but stayed empty — `chest.update()` after fill reverted inventory; removed. Material parsing hardened for YAML enum/number forms.

## [HC-Plugins-a0.1.15] - 2026-09-07

Tagged plugins: **API a0.1.13**, **Foundation a0.1.5**, **Hub a0.1.14**, **Iron Legacy a0.3.15**.

### Fixed

- Replenish chest fill silently dropped many loot entries (`nextInt(1,1)` in stack split), placed items in sequential slots instead of random slots, and now merges same-material stacks when a random slot already holds that item.
- Replenish material lookup uses `Material.matchMaterial` and skips invalid entries with a log line instead of failing silently.
- Jar default `replenish.yml` T3 `IRON` → `IRON_INGOT`.

## [HC-Plugins-a0.1.14] - 2026-09-07

Tagged plugins: **API a0.1.13**, **Foundation a0.1.5**, **Hub a0.1.14**, **Iron Legacy a0.3.14**.

### Changed

- Replenish tier loot and schedule now read from shared `plugins/HCIronLegacy/replenish.yml`; per-map chest coordinates stay in `world-envoys/<key>/replenish-locations.yml`.

### Added

- Startup auto-discover for worlds dropped into `hch-worlds/templates/` — registers IL `worlds.yml` + Hub `templates.yml` with `iron-legacy` mechanic (`templates.autoDiscoverOnStartup` in Hub config, default true).

## [HC-Plugins-a0.1.13] - 2026-07-23

Tagged plugins: **API a0.1.12**, **Foundation a0.1.5**, **Hub a0.1.13**, **Iron Legacy a0.3.13**.

### Fixed

- HCIronLegacy failed to enable when Hub `templates.yml` had a listing with empty `mechanicId` (disk adopt) — API now allows unset mechanic; Hub list is defensive; IL worlds adopt/discover sets `iron-legacy`.

## [HC-Plugins-a0.1.12] - 2026-07-23

Tagged plugins: **Hub a0.1.12**, **Iron Legacy a0.3.12** (API a0.1.11 and Foundation a0.1.4 unchanged).

### Fixed

- `/hch template worlds edit` crashed with Bukkit's generic "unexpected error" on first open — Paper rejects `setArmorContents(null)`; editor (and other inventory wipes) now clear with empty armor slots.

## [HC-Plugins-a0.1.11] - 2026-07-23

Tagged plugins: **API a0.1.11**, **Hub a0.1.11**, **Iron Legacy a0.3.11** (Foundation unchanged at a0.1.4).

### Added

- `/hch template worlds discover` — scans `hch-worlds/templates/` for dropped world folders and registers them in IL `worlds.yml` + Hub `templates.yml` (also runs on `list` / `reload`; `edit` auto-adopts a matching folder).
- Copy-paste platform docs: `docs/PLATFORM_FEATURES.md`, `docs/PLATFORM_COMMANDS.md`, `docs/PLATFORM_REFERENCE.md`.

### Changed

- Lobby: parties are auto-placed on one numbered match team (up to template `teamSizeMax`) when joining together; solo players still get the Match Team prompt. Party members see their team name on entry.
- `/hub` and hub spawn returns now save/restore template editor inventory and resume position (same as leaving via Iron Legacy lobby routing).

### Fixed

- Team relationship nametag colors in hub lobby instance worlds (`…/template-L#`) — template policy now resolves from the lobby session / lobby folder so green/red/blue/orange colors render while waiting in lobby.
- Team member cap in lobby respects template `teamSizeMax` when adding members (not only the global `teams.memberCap` default).
- Nametags re-sync for all players in the world when anyone creates, joins, or leaves a match team (immediate + next-tick refresh).
- Match team cleanup is **session-scoped**: ending one match or destroying one lobby clears teams only for that world's players; other active lobbies are untouched. Global `clearAllTeams` remains admin/backup only.
- Dropping a custom world into `hch-worlds/templates/<name>` was invisible to HCH until manually registered — discover/ensure now finds managed folders.

## [HC-Plugins-a0.1.10] - 2026-06-01

Tagged plugins: **API a0.1.10**, **Hub a0.1.10**, **Iron Legacy a0.3.10** (Foundation unchanged at a0.1.4).

### Added

- **Match Team menu** (lobby slot 3): independent from parties — create team, invite players, accept/decline invites, view roster (mirrors party UX). Team cap uses template `teamSizeMax` in lobby.
- **Hub instance capacity** (`hub.instanceCapacity`, default 20): party hub return and match-end evacuation keep parties together and overflow extra members to the next numbered hub when one instance is full.

### Changed

- Parties and match teams are **separate** — joining a party no longer auto-adds you to the leader's team. Teams reset when entering a new lobby and after each match; parties persist.
- Lobby team names use **Team 1**, **Team 2**, … again (stale teams cleared on lobby join; IL no longer binds party display names at launch).

### Fixed

- Hub PvP zone: re-entering the zone while PvP is off again selects the sword and activates PvP (every time, not only the first entry).
- Hub PvP zone: entering the zone while holding the hub sword (or selecting the sword inside the zone) activates PvP **immediately** instead of waiting for the 10-second countdown used outside the zone.

## [HC-Plugins-a0.1.9] - 2026-05-29

Tagged plugins: **API a0.1.9**, **Hub a0.1.9**, **Iron Legacy a0.3.9** (Foundation unchanged at a0.1.4).

### Changed

- Match teams use numbered names again (**Team 1**, **Team 2**, …) with a shared **`[Team N]`** prefix on every teammate’s nametag (e.g. `[Team 1] Steve`). Pre-match lobby shows the same prefixes with relationship colors (green teammates, red others, blue allies, orange designated enemies); allies and enemies set before launch carry into the match.

## [HC-Plugins-a0.1.8] - 2026-06-01

Tagged plugins: **API a0.1.8**, **Hub a0.1.8**, **Iron Legacy a0.3.8** (Foundation unchanged at a0.1.4).

### Added

- Lobby prefetch when **all** open lobbies for a template are above **50%** capacity (not only when none exist); auto-join creates a new lobby when no open lobby fits the party size.
- Party-too-big **trim GUI** (skull heads) when manually joining a specific lobby; kicks last-joined members until the party fits.
- Compass join flow: **mechanic tabs** → worlds → auto-join / lobby list; **Vanilla** mechanic tab for worlds without a minigame mechanic.
- `/hch template worlds mechanic <world|key> <mechanicName>` — mechanics are no longer auto-assigned on template create; multiple worlds may share one mechanic.
- `/hch worldsgui` (top-level and via `/hch gui worldsgui`); `/hch template worlds gui` redirects here.
- Pre-match lobby **Match Team** item in hotbar **slot 3**; match teams named after the **party leader's username** (e.g. `Steve`, `Steve 2` if taken).
- `/hub join <hubNumber>` for numbered hub spawns; `/hch sethub [n]` and `/hch clearhub [n]` for in-game setup.
- Typing **yes** in chat after a blocked publish sets **vanilla** mechanic and retries publish automatically.
- Template policy GUIs **save on close**; changed policies kick players from that template’s lobbies with a republish-style message.

### Changed

- `/hch help` and tab completion hide admin subcommands from non-ops (players see **party** help only).
- Hub PvP: swapping **off** the hub PvP sword starts the deactivate countdown; death respawn uses hub spawn unless the player was in a PvP zone (then PvP zone spawn; pad selection still prioritized until PvP deactivates).
- World command tab completion suggests **YAML keys** only, not full folder paths.
- Publish blocked until a mechanic is set (or **vanilla**); typing **yes** in chat applies vanilla and retries publish.
- **Team Mode** (non-Legacy): lobby no longer shows wool color picker — teams auto-form with fixed prefix colors; player names use green / red / blue / orange by relationship during the match. **Legacy Mode** still uses the wool picker. Team names use the **leader's username** and show in the Match Team menu after forming or joining.

### Fixed

- Extra-world publish path ensures Iron Legacy template row exists before publish.
- Lobby instance menu back-navigation preserves mechanic context.

## [HC-Plugins-a0.1.7] - 2026-05-29

Tagged plugins: **API a0.1.7**, **Hub a0.1.7**, **Iron Legacy a0.3.7** (Foundation unchanged at a0.1.4).

### Added

- In-lobby "Match Team" entry in the party menu — leader picks a team color before match start; party becomes the match team.
- Lobby-entry team prompt: leader, party member, and solo players each see a tailored title and chat hint about the team system.
- Per-template policy store (`hub/template-policies.yml`): color mode (Team/Legacy), shift-hides-nametag, TAB visibility, allies on/off, max-teams-per-alliance, per-template plugin allow/block list.
- New Plugin Allocation admin GUI (4-row landing → Hub / Lobby / Match tabs); Match opens a paginated template list, then a per-template landing with **General Settings** and **Plugin Allocation** tabs.
- General Settings tab: toggle Color Mode, toggle sneak hides nametag, toggle TAB visibility, toggle allies, toggle ally-friendly-fire (default ON), toggle team-friendly-fire (default ON), ±max teams per alliance, ±team size, ±min fill %, ±max players, teleport to template editor.
- Alliance system (toggleable per template): `/ally <team>` and `/ally accept <team>` (leader-only) form transitive alliances up to a per-template cap; `/enemy <team>` paints another team orange for your team's view. When only one alliance group is left alive, alliances dissolve and "Only one team will make it out alive." is broadcast.
- Per-viewer nametag/TAB rendering for match worlds: `[TeamName] PlayerName` with the team's RGB color in the prefix; in Team Mode the player name shows green / red / blue / orange based on the viewer's relationship (teammate / enemy / ally / designated enemy). Legacy Mode uses the team's 16-color uniformly for all viewers.
- Sneak hides nametag from non-teammates while sneaking (per-template toggle, default ON).
- TAB list visibility per match template (default OFF) — when off, TAB shows no players in that match.
- Chat and death messages prefixed with `[TeamName] PlayerName` while in a match world.
- Auto-register templates in `template-policies.yml` on template publish.
- Lobby spawn/zone/publish key resolution now maps Hub template keys to canonical Iron Legacy worlds.yml keys via source-world fallback, fixing multi-template cases where non-primary templates would not enter lobby correctly or failed to publish reliably.
- Expanded `docs/IN_GAME_FULL_TEST.md` with changelog cross-reference and phases for lobby prefetch/publish, PvP zones, plugin policy scopes, alliances, and multi-template regression.

### Changed

- Match scope policy resolution now consults the per-template plugin allocation list first (overrides global Match policy when the player is in a known template's match world).
- Admin "Plugin policies" tile now opens the new hierarchical landing menu instead of the flat policy editor.

### Fixed

- Lobby "Leave Lobby" item drags the whole party with the leader (online members in the same lobby session leave together).

## [HC-Plugins-a0.1.6] - 2026-05-27

Tagged plugins: **API a0.1.6**, **Hub a0.1.6**, **Iron Legacy a0.3.6** (Foundation unchanged at a0.1.4).

### Fixed

- Lobby zone clears to air after spawn-pad teleports (not before); entities inside the zone are removed.
- Template publish takes a fresh world snapshot every time (subsequent edits apply to new matches).
- Lobby folder cleanup and L2/L3 fallback when a stale `…-L#` folder blocks recreation.
- `/hch template worlds edit <world|key>` is the only CLI path into the template editor (`tp` removed).

## [HC-Plugins-a0.1.5] - 2026-05-27

Tagged plugins: **API a0.1.5**, **Hub a0.1.5**, **Iron Legacy a0.3.5** (Foundation unchanged at a0.1.4).

### Changed

- Stop tracking Maven `target/` build output and local `HC-Plugins*.zip` deploy bundles in git.

### Added

- In-game test plan (`docs/IN_GAME_FULL_TEST.md`): per-test **Code path** column tracing command → class → persistence → API/cross-plugin links; generator script `scripts/generate_in_game_test_doc.py`.
- Copy-paste QA exports: `docs/IN_GAME_FULL_TEST_CHECKLIST.txt` (plain text) and `docs/IN_GAME_FULL_TEST_CHECKLIST.csv` (spreadsheet); export via `scripts/export_in_game_checklist.py`.
- Google Sheets QA workbook CSV: `docs/IN_GAME_QA_GoogleSheets.csv` (+ import steps in `docs/IN_GAME_QA_GoogleSheets_IMPORT.txt`); regenerate with `scripts/export_google_sheets_qa.py`.
- Excel QA workbook: `docs/IN_GAME_QA_GoogleSheets.xlsx`.
- QA key/legend workbook: `docs/IN_GAME_QA_KEY.xlsx` (Status options, columns A–Z, modules, permissions, phases, bug template).
- Lobby prefetch when the last OPEN lobby starts a match.
- Template publish flow: destroy old lobbies, notify players, pre-create a fresh lobby.
- `PreMatchLobbyService.registerHubLobbyJoin` API for Hub lobby → lobby registration.

### Changed

- Hub lobby instance folders under `hch-worlds/minigames/<template>-L#`.
- Lobby join teleports to lobby spawn from `worlds.yml` and registers pre-match lobby.
- Per-plugin Maven versions (API, Foundation, Hub, Iron Legacy) and `HC-Plugins-a0.1.4` deploy zip tag.
- Release rules: bump tags only on explicit request; zip/ship does not bump versions.
- PvP zone wand outline visible only while holding the wand.

### Fixed

- Lobby zone clears to air (and removes entities) after all players teleport to spawn pads on match start, not before.
- Template publish saves world snapshots again on every publish (fixes edits after the first publish not applying to matches).
- Template editor access is `/hch template worlds edit <world|key>` only (`tp` / `teleport` aliases removed).
- Lobby/match world teardown: synchronous hub evacuation before unload, longer folder-delete retries, and orphan `…-L#` folder detection so the next lobby uses L2/L3 when a stale folder remains.
- Match world cleanup on game-end: evacuate stragglers to hub spawn, then best-effort folder delete with retries (fixes lobby/match world folder lingering after match end on Windows).
- Plugin policy admin GUI: per-scope whitelist/blacklist with auto-discovered plugins; best-effort enforcement blocks disallowed plugin commands in hub/lobby/match.
- `HubPluginPolicyApi` (api module): cooperative service so other Hollow Crown plugins can ask "am I allowed in this scope?" and self-disable when blocked.
- Golden PvP invulnerability ring only shows while hub/lobby PvP mode is active (not after in-game deaths).
- Lobby zone resets on match start (restores from template source world when loaded, otherwise clears blocks).
- Match spawn sets saturation to 2.5 bars (vanilla new-world level) with full health and hunger.
- Party invite when target is already in a party shows their colored name and a clear message.
- Party leader leave/kick promotes the next leader with a promotion notice (colored former leader name).
- `/il start` force-starts lobby countdown without minimum-player fill cancellation.
- Spawn-pad setup failures in lobby show a dedicated complaint message before returning players to hub.
- Failed lobby starts and all kick/evacuate paths now send players to configured hub spawn (leaves lobby first).
- `/hub` now leaves lobby/pre-match lobby the same way as the lobby join GUI Leave button.
- Lobby wand template resolution from current world (`HubIlTemplateDetailMenu`).

## [HC-Plugins-a0.1.4] - 2026-05-27

### Changed

- Per-plugin versions (API, Foundation, Hub, Iron Legacy) are independent; deploy zip uses single tag **a0.1.4**.

## [1.21.4-a0.0.4] - 2026-05-27

### Added

- PvP zone system: blaze rod wand (`/hch hubworld pvp wand`) with outline preview.
- `/hch hubworld pvp setspawn` and `/hch hubworld pvp pad`.
- Lobby prefetch: when the last OPEN lobby starts a match, Hub pre-creates the next lobby.
- Publishing a template destroys old lobbies, notifies players to rejoin, and pre-creates a fresh lobby.
- Hub lobby worlds under `hch-worlds/minigames/<template>-L#`.

### Fixed

- Lobby creation path validation on Windows (absolute world container).
- Lobby join teleports to lobby spawn from `worlds.yml`.
- Joining a Hub lobby registers players in pre-match lobby (natural start and `/hg start`).
- Lobby wand auto-detects template from current world; leave lobby/party GUI buttons.
- Hub return restores build mode when applicable.

### Changed

- PvP deactivation only teleports to hub when in a PvP zone or on a PvP pad.
- Removed `/hch hubworld pvppad` (use `/hch hubworld pvp pad`).

## [1.21.4-SNAPSHOT] - 2026-05-19

### Added

- Initial monorepo structure and platform documentation.
- Monorepo combining API, Foundation, Hub, and Iron Legacy under `HC-Platform`.
- `CONTRIBUTING.md`, `docs/REPO_LAYOUT.md`, `docs/PACKAGING.md`.

### Changed

- Standalone per-plugin GitHub repos superseded for platform development (see their README banners).
