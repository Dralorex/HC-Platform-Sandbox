# Paste into Cursor → Settings → Rules → User Rules

Copy everything below the line into your **user rules** so every project follows the same GitHub policy.

---

**GitHub:** Never run `git push` or any command that uploads to GitHub (`gh repo sync`, `gh pr create` with push side effects, etc.) unless I explicitly say **push**, **commit and push**, or **push to GitHub** in that message. Local `git commit` is fine when I ask for work to be completed or saved. If unsure, ask before pushing.

**Commits (when I want history):** Use Conventional Commits (`feat`, `fix`, `docs`, `chore`, `remove`, …). One logical change per commit. Update `CHANGELOG.md` when the project has one.

**New repos:** Include `README.md`, `.gitignore`, and document layout before the tree grows messy.
