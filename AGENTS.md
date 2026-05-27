# Agent instructions

- Read [CONTRIBUTING.md](CONTRIBUTING.md) before committing.
- Preserve [docs/REPO_LAYOUT.md](docs/REPO_LAYOUT.md) when adding modules.
- Update [CHANGELOG.md](CHANGELOG.md) for user-visible changes.
- Cursor rules: `.cursor/rules/git-and-repo-standards.mdc` (always apply), `hollow-crown-platform.mdc`, `release-versioning.mdc` (this repo).
- **Release tags:** separate per plugin (same bump rule for all). **Up tags** only when the user asks — bump every edited plugin plus `hc.plugins.zip.tag`, then rebuild. **Zip/ship** alone does not bump tags. See `release-versioning.mdc`.
- **Never commit** `**/target/` or `HC-Plugins*.zip` — Maven/build output only; use `mvn package` locally.
- **Never `git push`** unless the user explicitly asks in that message.
- **New minigame:** copy `minigames/_template/` → `minigames/<id>/`, wire root `pom.xml`, then implement (see `.cursor/rules/hollow-crown-platform.mdc`).
