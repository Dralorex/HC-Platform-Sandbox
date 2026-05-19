# Hollow Crown Platform

Monorepo for the **Hollow Crown Hub stack**: shared API, Foundation bootstrap, Hub platform, and minigames that register on `GamePlatform`.

- **Paper** 1.21.4 · **Java** 21
- **Git**: conventional commits — see [CONTRIBUTING.md](CONTRIBUTING.md)
- **Layout**: [docs/REPO_LAYOUT.md](docs/REPO_LAYOUT.md)
- **Packaging**: [docs/PACKAGING.md](docs/PACKAGING.md) (three plugin JARs, not one)

## Layout

```
HC-Platform/
├── api/                    # hollowcrown-api (library, not a plugin)
├── foundation/             # HC-Foundation
├── hub/                    # HollowCrownHub
└── minigames/
    ├── iron-legacy/        # HCIronLegacy
    └── (add more here)
```

## Build

```bash
mvn clean package -DskipTests
```

| Module | Deploy to `plugins/`? | JAR |
|--------|-------------------------|-----|
| `foundation` | Yes (first) | `HC-Foundation-*.jar` |
| `hub` | Yes | `HollowCrownHub-*.jar` |
| `minigames/iron-legacy` | Yes | `HCIronLegacy-*.jar` |
| `api` | No | (shaded into Foundation) |

Load order: **HC-Foundation** → **HollowCrownHub** → **HCIronLegacy**.

## Adding a minigame

See [minigames/README.md](minigames/README.md).

## GitHub

**Push is manual** — the agent will not upload unless you say so. See [docs/GITHUB.md](docs/GITHUB.md).

Legacy per-plugin repos are archived; history was merged via `scripts/import-history.ps1`.

## Related plugins

Standalone gameplay plugins (Revival, Local Chat, Environmental Hazards, etc.) stay in separate repositories unless they join the Hub stack.
