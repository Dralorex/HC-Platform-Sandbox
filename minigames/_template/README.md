# Minigame module template

Copy this folder to `minigames/<your-game-id>/` and replace placeholders.

**This folder is not built** (not listed in the root `pom.xml`). It is documentation + starter files only.

## Copy steps

1. Copy `_template` → `minigames/my-game` (use lowercase id with hyphens).
2. Find-replace in all files:
   - `YOUR_GAME_ID` → e.g. `sky-wars` (Hub `mechanicId` / template row)
   - `YourGame` → PascalCase Java prefix, e.g. `SkyWars`
   - `yourgame` → command alias, e.g. `skywars`
   - `Your Game` → display name
3. Add to root `pom.xml`:
   ```xml
   <module>minigames/my-game</module>
   ```
4. Build: `mvn clean package -DskipTests` from repo root.
5. Register Hub template (in-game or edit `plugins/HollowCrownHub/templates.yml`):
   ```
   /hch template set my-map YOUR_GAME_ID "Your Game"
   /hch template setsource my-map <world-folder-name>
   ```
6. Deploy JARs: Foundation + Hub + your minigame (see [docs/PACKAGING.md](../../docs/PACKAGING.md)).

## Reference

Full game: [../iron-legacy/](../iron-legacy/).
