# Repository layout

Canonical structure for **Hollow Crown Platform**. Preserve this when adding modules or docs.

```
HC-Platform/                          # Git root (this repo)
├── pom.xml                           # Parent POM, module list
├── README.md
├── CHANGELOG.md
├── CONTRIBUTING.md
├── .gitignore
├── .gitattributes
├── HC-Platform.code-workspace        # VS Code / Cursor workspace
│
├── api/                              # Library: hollowcrown-api (NOT a plugin)
│   ├── pom.xml
│   └── src/main/java/com/hollowcrown/api/
│
├── foundation/                       # Plugin: HC-Foundation (load first)
│   ├── pom.xml
│   └── src/main/resources/plugin.yml
│
├── hub/                              # Plugin: HollowCrownHub
│   ├── pom.xml
│   └── src/main/java/com/hollowcrown/hub/
│
├── minigames/                        # All Hub-integrated minigames
│   ├── README.md                     # How to add a minigame
│   ├── pom.xml                       # Optional grouping POM
│   └── iron-legacy/                  # Plugin: HCIronLegacy
│       ├── pom.xml
│       └── src/
│
├── docs/                             # Project documentation
│   ├── PACKAGING.md
│   └── REPO_LAYOUT.md
│
└── scripts/                          # Maintainer scripts
    ├── build.ps1
    ├── import-history.ps1
    └── saved-poms/                   # Backup module POMs for import script
```

## Module responsibilities

| Path | Artifact | Server plugin? |
|------|----------|----------------|
| `api/` | `hollowcrown-api` | No |
| `foundation/` | `hc-foundation` | Yes |
| `hub/` | `hollowcrown-hub` | Yes |
| `minigames/*/` | per `pom.xml` | Yes |

## Adding a minigame

1. Create `minigames/<id>/` with `pom.xml` + `src/`.
2. Add `<module>minigames/<id></module>` to root `pom.xml`.
3. Register mechanic on Hub `GamePlatform` (see Iron Legacy).
4. Document in `CHANGELOG.md` under `[Unreleased]`.

## What does not belong here

- Standalone gameplay plugins (Revival, Local Chat, Environmental Hazards, etc.) — separate repos unless they join the Hub stack.
