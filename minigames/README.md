# Minigames

Each subdirectory is a Paper plugin that **depends on HC-Foundation + HollowCrownHub** and registers a `MinigameMechanic` on the Hub `GamePlatform`.

## New minigame checklist

1. **Module** — `minigames/<id>/` with Maven parent `../../pom.xml`.
2. **plugin.yml** — `depend: [HC-Foundation, HollowCrownHub]`; unique plugin `name`.
3. **MinigamePluginKey** — e.g. `MinigamePluginKey.hollowCrown("my-game", "My Game")`.
4. **On enable** — resolve `GamePlatform` from Bukkit ServicesManager; `MinigameBootstrap.registerMechanic(...)`.
5. **Mechanic callbacks** — implement `onLobbyInstanceCreated` / `onLobbyInstanceReleased` if you use Hub lobby worlds.
6. **Hub template** — `/hch template set <key> <mechanicId> <displayName>` and `setsource` for the world folder.
7. **Root pom** — add `<module>minigames/<id></module>`.

Reference implementation: `iron-legacy/`.

**Starter files (not built):** copy `minigames/_template/` to `minigames/<your-game-id>/` and follow its README.
