# Changelog

All notable changes to Hollow Crown Platform are documented here.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

- **API** · **Foundation** · **Hub** · **Iron Legacy** — each has its own tag; same rule: bump only modules that changed.
- **Up tags** (user request): bump every edited plugin plus `hc.plugins.zip.tag`, then rebuild. Zip/ship alone does not bump tags.

## [Unreleased]

## [HC-Plugins-a0.1.7] - 2026-05-29

Tagged plugins: **API a0.1.7**, **Hub a0.1.7**, **Iron Legacy a0.3.7** (Foundation unchanged at a0.1.4).

### Added

- In-limbo "Match Team" entry in the party menu — leader picks a team color before match start; party becomes the match team.
- Lobby-entry team prompt: leader, party member, and solo players each see a tailored title and chat hint about the team system.
- Per-template policy store (`hub/template-policies.yml`): color mode (Team/Legacy), shift-hides-nametag, TAB visibility, allies on/off, max-teams-per-alliance, per-template plugin allow/block list.
- New Plugin Allocation admin GUI (4-row landing → Hub / Limbo / Match tabs); Match opens a paginated template list, then a per-template landing with **General Settings** and **Plugin Allocation** tabs.
- General Settings tab: toggle Color Mode, toggle sneak hides nametag, toggle TAB visibility, toggle allies, toggle ally-friendly-fire (default ON), toggle team-friendly-fire (default ON), ±max teams per alliance, ±team size, ±min fill %, ±max players, teleport to template editor.
- Alliance system (toggleable per template): `/ally <team>` and `/ally accept <team>` (leader-only) form transitive alliances up to a per-template cap; `/enemy <team>` paints another team orange for your team's view. When only one alliance group is left alive, alliances dissolve and "Only one team will make it out alive." is broadcast.
- Per-viewer nametag/TAB rendering for match worlds: `[TeamName] PlayerName` with the team's RGB color in the prefix; in Team Mode the player name shows green / red / blue / orange based on the viewer's relationship (teammate / enemy / ally / designated enemy). Legacy Mode uses the team's 16-color uniformly for all viewers.
- Sneak hides nametag from non-teammates while sneaking (per-template toggle, default ON).
- TAB list visibility per match template (default OFF) — when off, TAB shows no players in that match.
- Chat and death messages prefixed with `[TeamName] PlayerName` while in a match world.
- Auto-register templates in `template-policies.yml` on template publish.
- Limbo spawn/zone/publish key resolution now maps Hub template keys to canonical Iron Legacy worlds.yml keys via source-world fallback, fixing multi-template cases where non-primary templates would not enter limbo correctly or failed to publish reliably.
- Expanded `docs/IN_GAME_FULL_TEST.md` with changelog cross-reference and phases for lobby prefetch/publish, PvP zones, plugin policy scopes, alliances, and multi-template regression.

### Changed

- Match scope policy resolution now consults the per-template plugin allocation list first (overrides global Match policy when the player is in a known template's match world).
- Admin "Plugin policies" tile now opens the new hierarchical landing menu instead of the flat policy editor.

### Fixed

- Lobby "Leave Lobby" item drags the whole party with the leader (online members in the same limbo session leave together).

## [HC-Plugins-a0.1.6] - 2026-05-27

Tagged plugins: **API a0.1.6**, **Hub a0.1.6**, **Iron Legacy a0.3.6** (Foundation unchanged at a0.1.4).

### Fixed

- Limbo zone clears to air after spawn-pad teleports (not before); entities inside the zone are removed.
- Template publish takes a fresh world snapshot every time (subsequent edits apply to new matches).
- Lobby folder cleanup and L2/L3 fallback when a stale `…-L#` folder blocks recreation.
- `/hch template worlds edit <world|key>` is the only CLI path into the template editor (`tp` removed).

## [HC-Plugins-a0.1.5] - 2026-05-27

Tagged plugins: **API a0.1.5**, **Hub a0.1.5**, **Iron Legacy a0.3.5** (Foundation unchanged at a0.1.4).

### Changed

- Stop tracking Maven `target/` build output and local `HC-Plugins*.zip` deploy bundles in git.

### Added

- Lobby prefetch when the last OPEN lobby starts a match.
- Template publish flow: destroy old lobbies, notify players, pre-create a fresh lobby.
- `PreMatchLimboService.registerHubLobbyJoin` API for Hub lobby → limbo registration.

### Changed

- Hub lobby instance folders under `hch-worlds/minigames/<template>-L#`.
- Lobby join teleports to limbo spawn from `worlds.yml` and registers pre-match limbo.
- Per-plugin Maven versions (API, Foundation, Hub, Iron Legacy) and `HC-Plugins-a0.1.4` deploy zip tag.
- Release rules: bump tags only on explicit request; zip/ship does not bump versions.
- PvP zone wand outline visible only while holding the wand.

### Fixed

- Limbo zone clears to air (and removes entities) after all players teleport to spawn pads on match start, not before.
- Template publish saves world snapshots again on every publish (fixes edits after the first publish not applying to matches).
- Template editor access is `/hch template worlds edit <world|key>` only (`tp` / `teleport` aliases removed).
- Lobby/match world teardown: synchronous hub evacuation before unload, longer folder-delete retries, and orphan `…-L#` folder detection so the next lobby uses L2/L3 when a stale folder remains.
- Match world cleanup on game-end: evacuate stragglers to hub spawn, then best-effort folder delete with retries (fixes lobby/match world folder lingering after match end on Windows).
- Plugin policy admin GUI: per-scope whitelist/blacklist with auto-discovered plugins; best-effort enforcement blocks disallowed plugin commands in hub/limbo/match.
- `HubPluginPolicyApi` (api module): cooperative service so other Hollow Crown plugins can ask "am I allowed in this scope?" and self-disable when blocked.
- Golden PvP invulnerability ring only shows while hub/limbo PvP mode is active (not after in-game deaths).
- Limbo zone resets on match start (restores from template source world when loaded, otherwise clears blocks).
- Match spawn sets saturation to 2.5 bars (vanilla new-world level) with full health and hunger.
- Party invite when target is already in a party shows their colored name and a clear message.
- Party leader leave/kick promotes the next leader with a promotion notice (colored former leader name).
- `/il start` force-starts limbo countdown without minimum-player fill cancellation.
- Spawn-pad setup failures in limbo show a dedicated complaint message before returning players to hub.
- Failed lobby starts and all kick/evacuate paths now send players to configured hub spawn (leaves limbo first).
- `/hub` now leaves lobby/pre-match limbo the same way as the lobby join GUI Leave button.
- Limbo wand template resolution from current world (`HubIlTemplateDetailMenu`).

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
- Lobby join teleports to limbo spawn from `worlds.yml`.
- Joining a Hub lobby registers players in pre-match limbo (natural start and `/hg start`).
- Limbo wand auto-detects template from current world; leave lobby/party GUI buttons.
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
