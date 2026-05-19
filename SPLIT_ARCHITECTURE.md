# Hollow Crown split — architecture and build

This document tracks the **platform split**: shared API → Hub → minigames (starting with HCIronLegacy).

**Ordered backlog:** see `plugins/HOLLOW_CROWN_ROADMAP.md` (phases 0–6, checkboxes).

## Deploy order (server `plugins/`)

1. **`HC-Foundation-<version>.jar`** (from `target/` after `mvn package`) — foundation plugin; shades `com.hollowcrown.api` for dependent plugins.
2. **`HC-Hub-<version>.jar`** — Hub (`plugin.yml` name remains `HollowCrownHub`); registers `GamePlatform` in Bukkit `ServicesManager`; `/hch`, `/hub`.
3. **`HCIronLegacy` jar** — depends on both; registers `iron-legacy` mechanic on `GamePlatform`.

If **HC-Foundation** or **HollowCrownHub** is missing or disabled, **HCIronLegacy** disables itself with a clear console error.

## Maven build order

From `plugins/` (reactor `pom.xml` — **recommended**):

```bat
mvn install
```

Or per module:

```bat
cd HC-API
mvn -q install
cd ..\HC-Foundation
mvn -q package
cd ..\HC-Hub
mvn -q package
cd ..\HC-Iron-Legacy
mvn -q package
```

## Templates & lobby instances

- **`templates.yml`**: `templateKey` → `mechanicId`, `displayName`, **`sourceWorldName`** (folder under server root to copy for new lobbies).
- **`/hch template setsource <key> <folder>`** — set copy source (must exist next to other world folders).
- **`/hch lobby create <templateKey>`** — copies source → `<key>-L#`, `WorldCreator`, registers row, fires mechanic **created**.
- **`/hch lobby list | gui | tp <id> | destroy <id> | forget <id>`** — admin; **`/hch lobby gui`** opens OP-only chest: **barrier** = BROKEN (click = forget), **lime pane** = OPEN (click = TP, shift+click = destroy). Arrows + book pagination.
- **destroy** removes row on full delete else **BROKEN** + OP chat + **`forget`** after manual folder delete.

## Hub spawn & commands

- **`/hch sethub`** (permission `hollowcrownhub.admin`, in-game) — saves current location to `config.yml` under `hub.spawn` using **world UUID** + coords + yaw/pitch.
- **`/hch clearhub`** — removes stored spawn.
- **`/hub`** (aliases `lobby`, `spawnhub`, permission `hollowcrownhub.use`) — async teleport to stored spawn if the world is loaded.
- **API:** `GamePlatform.getHubSpawn()`, `setHubSpawn(HubSpawn)`, `clearHubSpawn()` on `HubSpawn` record in `HC-API`. **SCHEMA** bumped when this contract changes.

Artifacts:

| Module | Output |
|--------|--------|
| `HC-API` | Installed to local `.m2` as `com.hollowcrown:hollowcrown-api:1.0.0-SNAPSHOT` |
| `HC-Foundation` | `target/HC-Foundation-<version>.jar` |
| `HC-Hub` | `target/HC-Hub-<version>.jar` |
| `HC-Iron-Legacy` | `target/hcironlegacy-…jar` (existing shade naming) |

## Code layout

- **`HC-API`** — no Bukkit: `MechanicId`, `TemplateKey`, `LobbyInstanceId`, `MinigameMechanic`, `MechanicContext`, `GamePlatform`, `HubSpawn`, `StorageProvider`, `HollowCrownApiVersion.SCHEMA`.
- **`HC-Hub`** — YAML storage stub, `DefaultGamePlatform`, structured `HubLog`, `/hch`, `/hub`, hub spawn persistence. Lobby/template/party logic will land here incrementally.
- **`HCIronLegacy`** — `IronLegacyMechanic` SPI stub; match/gameplay stays here until migrated.

## Iron Legacy integration

- **`config.yml`** on HCIronLegacy: `integrations.preferHollowCrownHubSpawn` (default `false`). When `true`, **`sendToLobby`** and **`sendToLobbyExact`** teleport to **HollowCrownHub** spawn if it is set and the hub world UUID is loaded on this server; otherwise behavior is unchanged (IL `lobby.yml` / shards).

## Product spec (summary)

Full behavioral spec (queue, `L#` lobbies, party→team in limbo, `TeamSize`, profanity, storage fallbacks, delete retries, etc.) lives in your design notes; implement against that as Hub features are built out.

**Integration style:** hot path = Hub → `MinigameMechanic` direct calls; events only where optional listeners need them.

**Versioning:** bump `HollowCrownApiVersion.SCHEMA` when breaking `GamePlatform` or mechanic contracts; log incompatibility on startup when versions diverge (future: Hub checks min IL schema).

## Plugin.yml names (must match)

- Foundation: `name: HC-Foundation`
- Hub: `name: HollowCrownHub`
- IL `depend: [HC-Foundation, HollowCrownHub]`
