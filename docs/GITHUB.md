# GitHub — manual push only

Your code lives in **local git** until **you** push. The agent will not push unless you explicitly ask.

## One-time: create the repo on GitHub

1. Open [github.com/new](https://github.com/new).
2. Repository name: `HC-Platform` (or your choice).
3. **Do not** add a README, .gitignore, or license (this repo already has them).
4. Create the repository (private recommended).

## One-time: connect your PC

In PowerShell, from this folder:

```powershell
cd "c:\Users\super\0. The Hollow Crown\plugins\0. HC-Plugins\HC-Platform"

# Replace with your URL if different
git remote add origin https://github.com/Dralorex/HC-Platform.git

# Check
git remote -v
git status
git log --oneline -5
```

If `origin` already exists, use `git remote set-url origin <url>` instead.

## When you want to upload (push)

Only run this when **you** decide the local commits are ready:

```powershell
git push -u origin main
```

GitHub will then show the same commits as your PC.

## Later: normal workflow

```powershell
# After agent or you commit locally:
git status
git log --oneline -3

# When YOU choose to upload:
git push
```

## Pull on another PC

```powershell
git clone https://github.com/Dralorex/HC-Platform.git
cd HC-Platform
mvn clean package -DskipTests
```

## What push does not do

- Does not build plugins (`mvn package` is separate).
- Does not run your Minecraft server.
- Does not commit uncommitted edits — only committed snapshots are uploaded.
