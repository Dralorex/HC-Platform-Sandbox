# Changelog

All notable changes to Hollow Crown Platform are documented here.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

- **API** · **Foundation** · **Hub** · **Iron Legacy** — each has its own tag; same rule: bump only modules that changed.
- **Up tags** (user request): bump every edited plugin plus `hc.plugins.zip.tag`, then rebuild. Zip/ship alone does not bump tags.

## [Unreleased]

### Changed

- Stop tracking Maven `target/` build output and local `HC-Plugins*.zip` deploy bundles in git.

### Added

- In-game test plan (`docs/IN_GAME_FULL_TEST.md`): per-test **Code path** column tracing command → class → persistence → API/cross-plugin links; generator script `scripts/generate_in_game_test_doc.py`.
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
