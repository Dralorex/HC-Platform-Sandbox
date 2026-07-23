# Contributing to Hollow Crown Platform

## Repository layout

See [docs/REPO_LAYOUT.md](docs/REPO_LAYOUT.md) for the canonical folder structure.

## Git commits

Use **Conventional Commits** so history stays searchable:

| Prefix | Use when |
|--------|----------|
| `feat` | New behavior (module, command, API surface) |
| `fix` | Bug fix |
| `docs` | README, CONTRIBUTING, comments only |
| `refactor` | Code change without behavior change |
| `build` | Maven, CI, packaging |
| `chore` | Tooling, gitignore, non-code maintenance |
| `remove` | Deleting features, files, or dead code |

**Scope** (optional): module path in parentheses.

Examples:

```
feat(hub): add queue drain into pre-match limbo
fix(iron-legacy): border shrink timer on empty world
docs: document three-jar server deploy
build(api): bump Paper API to 1.21.4-R0.1-SNAPSHOT
chore: update .gitignore for run/ folder
remove(hub): deprecated lobby join command alias
```

### Rules

1. **One logical change per commit** — do not mix unrelated refactors with features.
2. **Commit message body** — when non-obvious, add a blank line then 1–3 sentences: what changed and why.
3. **Record user-visible changes** in [CHANGELOG.md](CHANGELOG.md) under `## [Unreleased]`.
4. **Additions** — note new modules, commands, permissions, config keys.
5. **Removals** — call out breaking changes explicitly (`BREAKING:` in footer if needed).
6. **Platform docs** — when commands or user-visible features change, update [docs/PLATFORM_COMMANDS.md](docs/PLATFORM_COMMANDS.md) and/or [docs/PLATFORM_FEATURES.md](docs/PLATFORM_FEATURES.md) in the same change set (see `.cursor/rules/platform-docs.mdc`).

## GitHub push

**Manual only.** Do not push to GitHub unless you explicitly request it. See [docs/GITHUB.md](docs/GITHUB.md).

Agents must not run `git push` without your say-so (see `.cursor/rules/git-and-repo-standards.mdc`).

## Branches

- `main` — stable integration branch.
- `feat/<short-name>` — features.
- `fix/<short-name>` — bugfixes.

## Build before push

```bash
mvn clean package -DskipTests
```

Deploy **three** plugin JARs (see [docs/PACKAGING.md](docs/PACKAGING.md)).

## New minigame module

Follow [minigames/README.md](minigames/README.md) and register the module in the root `pom.xml`.
