# Hollow Crown Platform

Monorepo for the **Hollow Crown Hub stack**: shared API, Foundation bootstrap, Hub platform, and minigames that register on `GamePlatform`.

Paper **1.21.4**, Java **21**.

## Layout

```
HC-Platform/
├── pom.xml                 # Parent reactor (build everything)
├── api/                    # hollowcrown-api — shared contracts (not a server plugin)
├── foundation/             # HC-Foundation — shades API onto the classpath
├── hub/                    # HollowCrownHub — lobbies, parties, queues, limbo
└── minigames/
    ├── iron-legacy/        # HCIronLegacy — first minigame
    └── (add more here)
```

## Build

From the repo root:

```bash
mvn clean package -DskipTests
```

Output JARs (copy to your server `plugins/` folder):

| Module | JAR |
|--------|-----|
| `foundation` | `HC-Foundation-*.jar` |
| `hub` | `HollowCrownHub-*.jar` |
| `minigames/iron-legacy` | `HCIronLegacy-*.jar` |

Load order on the server: **HC-Foundation** → **HollowCrownHub** → **HCIronLegacy**.

The `api` module is a library only; it is shaded into Foundation and used at compile time by Hub and minigames.

## Adding a new minigame

1. Copy `minigames/iron-legacy` as a template or create `minigames/<your-game>/` with:
   - `pom.xml` (parent `hollowcrown-platform`, depend on `hollowcrown-api` with `provided` scope)
   - `src/main/resources/plugin.yml` with `depend: [HC-Foundation, HollowCrownHub]`
2. Register `MinigameMechanic` on enable via `MinigameBootstrap` (see Iron Legacy).
3. Add a row in Hub `templates.yml` with `mechanicId` matching your `MinigamePluginKey`.
4. Add `<module>minigames/your-game</module>` to the root `pom.xml` `<modules>` list.

See `minigames/README.md` for a short checklist.

## GitHub

This repo replaces separate clones of HC-API, HC-Foundation, HC-Hub, and HC-Iron-Legacy for platform development. Standalone plugin repos can stay for releases or be archived once you migrate.

```bash
git init
git add .
git commit -m "Initial Hollow Crown Platform monorepo"
gh repo create HC-Platform --private --source=. --remote=origin --push
```

(Adjust repo name and visibility as you prefer.)

## Related plugins

Gameplay plugins that do **not** require the Hub stack (Revival, Local Chat, Environmental Hazards, etc.) remain in their own repositories under `0. HC-Plugins`.
