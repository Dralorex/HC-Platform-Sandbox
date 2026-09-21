# Packaging: one JAR or many?

**Multiple plugin JARs** — not a single uber-JAR.

| Module | Deploy to `plugins/`? | Output |
|--------|----------------------|--------|
| `api` | **No** | Library only (compiled into Foundation’s classpath via shading) |
| `foundation` | **Yes** | `HC-Foundation-*.jar` |
| `hub` | **Yes** | `HollowCrownHub-*.jar` |
| `minigames/iron-legacy` | **Yes** | `HCIronLegacy-*.jar` |
| `lib/FakeTeamAPI-*.jar` | **Yes** | Required soft-dep for Hub teams (`depend: FakeTeamAPI`) |

## On your Paper server

Copy **three** stack JARs from `mvn package` plus **FakeTeamAPI**:

1. `foundation/target/HC-Foundation-*.jar`
2. `hub/target/HollowCrownHub-*.jar`
3. `minigames/iron-legacy/target/HCIronLegacy-*.jar`
4. `lib/FakeTeamAPI-1.21.4-1.0.0.jar` (teams)

Or use the deploy zip at repo root: **`HC-Plugins-a0.1.21.zip`** (alias: `HC-Plugins.zip`). The zip tag (`a0.1.21`) is the bundle label, not each plugin’s tag.

Load order: **HC-Foundation** → **FakeTeamAPI** → **HollowCrownHub** → **HCIronLegacy**.

The API module is a **compile-time / shaded** dependency. Foundation shades `hollowcrown-api` into its JAR; Hub and Iron Legacy expect those classes at runtime via Foundation (they use `provided` scope on the API in Maven).

## Future minigames

Each minigame module produces **its own** plugin JAR. Add it to `plugins/` alongside the three stack JARs.
