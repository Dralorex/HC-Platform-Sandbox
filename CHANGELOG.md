# Changelog

All notable changes to Hollow Crown Platform are documented here.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Cursor rule: scaffold new minigames by copying `minigames/_template/` when requested.
- Minigame copy template under `minigames/_template/` (not a Maven module).
- [docs/GITHUB.md](docs/GITHUB.md) for manual push workflow.
- Cursor rule: never `git push` unless the user explicitly asks.

### Added (earlier)

- Monorepo combining API, Foundation, Hub, and Iron Legacy under `HC-Platform`.
- Maven parent reactor (`hollowcrown-platform` 1.21.4-SNAPSHOT).
- `CONTRIBUTING.md`, `docs/REPO_LAYOUT.md`, `docs/PACKAGING.md`.
- Git history imported from legacy repos (`HC-API`, `HC-Foundation`, `HC-Hub`, `HC-Iron-Legacy`).

### Changed

- Standalone per-plugin GitHub repos superseded for platform development (see their README banners).

## [1.21.4-SNAPSHOT] - 2026-05-19

### Added

- Initial monorepo structure and platform documentation.
