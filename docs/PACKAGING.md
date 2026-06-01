# Packaging: one JAR or many?

**Multiple plugin JARs** — not a single uber-JAR.

| Module | Deploy to `plugins/`? | Output |
|--------|----------------------|--------|
| `api` | **No** | Library only (compiled into Foundation’s classpath via shading) |
| `foundation` | **Yes** | `HC-Foundation-*.jar` |
| `hub` | **Yes** | `HollowCrownHub-*.jar` |
| `minigames/iron-legacy` | **Yes** | `HCIronLegacy-*.jar` |

## On your Paper server

Copy **three** JARs from `mvn package` (each plugin has its own version in `pom.xml`):

1. `foundation/target/HC-Foundation-*.jar`
2. `hub/target/HollowCrownHub-*.jar`
3. `minigames/iron-legacy/target/HCIronLegacy-*.jar`

Or use the deploy zip at repo root: **`HC-Plugins-a0.1.7.zip`** (alias: `HC-Plugins.zip`). The zip tag (`a0.1.7`) is the bundle label, not each plugin’s tag.

Load order: **HC-Foundation** → **HollowCrownHub** → **HCIronLegacy**.

The API module is a **compile-time / shaded** dependency. Foundation shades `hollowcrown-api` into its JAR; Hub and Iron Legacy expect those classes at runtime via Foundation (they use `provided` scope on the API in Maven).

## Future minigames

Each minigame module produces **its own** plugin JAR. Add it to `plugins/` alongside the three stack JARs.
