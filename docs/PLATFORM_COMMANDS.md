# Hollow Crown Platform — Command List

Copy-paste reference for all player and admin commands.

**Maintainers:** update this file in the same change set whenever you add, remove, rename, or change command behavior, permissions, or aliases. Also update `docs/PLATFORM_FEATURES.md` if behavior changed, and `CHANGELOG.md` under `[Unreleased]`.

**Feature descriptions:** `docs/PLATFORM_FEATURES.md`  
**GUIs & integration:** `docs/PLATFORM_REFERENCE.md`

**Bundle baseline:** HC-Plugins a0.1.19

================================================================================
PERMISSIONS
================================================================================

HollowCrownHub
  hollowcrownhub.use              default: true     /hub, basic access
  hollowcrownhub.join             default: true     Join GUI + lobby teleport
  hollowcrownhub.party            default: true     /hch party … + party menu
  hollowcrownhub.queue            default: true     /hch queue …
  hollowcrownhub.queue.bypass     default: op       Bypass queue policy gates
  hollowcrownhub.admin            default: op       All /hch admin + GUIs

HCIronLegacy
  hcironlegacy.use                default: true     /il player commands
  hcironlegacy.admin              default: op       /il admin sections

HC-Foundation
  (no commands)

================================================================================
HC-FOUNDATION
================================================================================

(no commands)

================================================================================
/hub  —  aliases: lobby, spawnhub
Permission: hollowcrownhub.use
================================================================================

/hub
  Teleport to hub #1. Party leader pulls online party (capacity routing).
  Exits limbo first. Saves template editor inventory if leaving editor.

/hub join <hubNumber>
  Teleport to numbered hub spawn (/hch sethub <n>).

================================================================================
/ally
================================================================================

/ally <teamName>
  Team leader sends alliance invite to another team.

/ally accept <teamName>
  Team leader accepts pending alliance.

================================================================================
/enemy
================================================================================

/enemy <teamName>
  Team leader marks another team as designated enemy (orange nametags).

================================================================================
/hch  —  aliases: hollowcrownhub, hchub
================================================================================

--- Meta ---

/hch help
  Show command help. Admin sections hidden from non-ops.

/hch version
  [admin] Hub version + API schema.

/hch debug mechanics
  [admin] List registered minigame mechanic ids.

/hch debug why <player>
  [admin] Snapshot: world, limbo, party, team, queue state.

--- Player join ---

/hch join [gui] [page]
  [hollowcrownhub.join] Open join game GUI (same as compass).

--- Party  [hollowcrownhub.party] ---

/hch party create
  Create a party (you become leader).

/hch party invite <player>
  Send invite paper to player.

/hch party leave
  Leave current party.

/hch party disband
  Leader disbands party.

/hch party kick <player>
  Leader removes member.

/hch party accept <partyId>
  Accept invite by party UUID.

/hch party decline <partyId>
  Decline invite.

/hch party list
  [admin] List all parties on server.

--- Queue  [hollowcrownhub.queue] ---

/hch queue join <templateKey>
  Add solo or party unit to template queue.

/hch queue leave
  Leave queue.

/hch queue status [templateKey]
  Show your position or dump template queue.

--- Hub spawn ---

/hch sethub [n]
  [admin] Save hub spawn at feet (#1 default, or numbered instance).

/hch clearhub [n]
  [admin] Remove numbered hub spawn.

--- Hub world  [admin] ---

/hch hubworld size <blocks>
  Protection radius from hub spawn.

/hch hubworld buildmode <true|false>
  Toggle creative build mode. Alias: bm

/hch hubworld pvp wand
  Give PvP zone selection wand.

/hch hubworld pvp setspawn
  Set PvP zone spawn at your feet.

/hch hubworld pvp pad
  Give PvP pad item.

/hch hubworld hostilemobs <true|false>
  Allow hostile mob spawns in hub. Aliases: hostilemobspawn, …

/hch hubworld passivemobs <true|false>
  Allow passive mob spawns in hub.

--- Lobby  [admin] ---

/hch lobby list
  List OPEN/BROKEN lobby rows.

/hch lobby gui [page]
  Open lobby admin chest GUI.

/hch lobby create <templateKey>
  Create new OPEN lobby instance (-L#).

/hch lobby destroy <id>
  Evacuate, unload, delete folder, remove row.

/hch lobby forget <id>
  Remove BROKEN row after manual folder delete.

/hch lobby tp <id>
  Teleport to lobby instance world.

--- Template — Hub registry  [admin] ---

/hch template list
  List Hub templates.yml rows.

/hch template create <key> [display…]
  Create IL editable world + Hub row (in-game, OP).

/hch template get <key>
  Show template row details.

/hch template remove <key>
  Remove Hub template row.

/hch template set <key> <mechanicId> [display]
  Register template mechanic + display name.

/hch template set <key> display|enabled|maxplayers|teamsize|icon|minimumpercentage|gamerule …
  Set IL worlds.yml fields when IL loaded.

/hch template set limbo spawn
  Alias for limbo spawn setter.

/hch template setsource <key> <worldFolder>
  Set source world folder for lobby copies.

/hch template setqueue <key> maxUnitSize|soloOnly|soloAutoMerge|joinQueueFirst <value>
  Queue policy for template.

--- Template — Limbo  [admin] ---

/hch template limbo wand
  Give limbo zone wand (left/right corner, shift-right save).

/hch template limbo spawn
  Set limbo spawn at feet for template in current world.

/hch template limbo help
  Limbo wand help.

--- Template — IL worlds  [admin, requires IL] ---

/hch template worlds help
/hch template worlds ?
  Subcommand help.

/hch template worlds list
  Summarize all IL template rows (also scans hch-worlds/templates/ for new folders).

/hch template worlds discover
  Scan hch-worlds/templates/ for dropped world folders; register worlds.yml + Hub templates.yml rows.

/hch template worlds reload
  Reload IL configs, templates, icons, rules (also discovers managed template folders).

/hch template worlds importhub [key] [--dry-run] [--sync]
  Import Hub registry rows into IL YAML.

/hch template worlds mechanic <world|key> <mechanicId>
  Assign mechanic (required before publish).

/hch template worlds edit <world|key>
  Teleport to template editor (inventory save/restore).

/hch template worlds publish <world|key>
  Snapshot + publish (destroys lobbies, chat yes gate).

/hch template worlds delete <world|key>
  Delete template row + world folder.

/hch template worlds rollback <world|key> [snapshot|latest]
  Rollback to snapshot.

/hch template worlds snapshots <world|key>
  List snapshot ids.

/hch template worlds toggle <world|key>
  Toggle template enabled flag.

/hch template worlds iconpreview [iconKey]
  Preview template GUI icon item.

/hch template worlds gui
  Redirect to /hch worldsgui.

--- GUI shortcuts  [admin] ---

/hch gui help
/hch gui ?
  GUI help.

/hch gui admin
/hch gui home
/hch gui shell
  Open admin shell menu.

/hch gui maps
/hch gui ironlegacy
/hch gui il
  IL template list GUI.

/hch gui templates
/hch gui hubtemplates
/hch gui tpl
  Hub templates browser.

/hch gui queues
/hch gui queue
/hch gui q
  Queue overview GUI.

/hch gui lobby
/hch gui lobbies
/hch gui l
  Lobby admin GUI.

/hch worldsgui
/hch worldgui
  Alias → IL template list GUI.

/hch ironlegacy
  [admin] Deprecated → points to template worlds / gui maps.

================================================================================
/il  —  alias: ironlegacy
Permission: hcironlegacy.use (admin subcommands: op / hcironlegacy.admin)
================================================================================

--- Help & menus ---

/il
/il help
  Show help (default when no args).

/il party
  Open Hub party menu (requires Hub).

/il teams
  In limbo: Hub Match Team menu. Else OP: team admin GUI.

/il gui
  [OP] Open MainMenu or TeamsMenu (config ui.openTeamsFirst).

/il admin
  [OP] AdminControlCenterMenu.

/il leaderboard
/il lb
  Top 10 kill leaderboard.

--- Admin utility ---

/il joingame <worldname>
  [OP] Bypass join to world.

/il reload
  [OP] Reload config, files, spawns, rules, templates, icons.

--- Match & limbo control  [OP; role: GAME or STANDALONE for start/stop] ---

/il start [limbo_world|template]
  [OP] Force limbo countdown (ignores min fill).

/il stop
  Abort limbo queues + stop active match → hub.

/il hostilemobspawn <true|false|t|f>
  [OP, HUB/STANDALONE] Toggle hostile mob spawns in limbo.

/il passivemobspawn <true|false|t|f>
  [OP, HUB/STANDALONE] Toggle passive mob spawns in limbo.

--- Spawn pads  [OP]  (per template → worlds.yml worlds.<key>.spawnPads) ---

/il addspawn
  Add spawn pad at feet for the current template world.

/il clearspawns
  Clear spawn pads for the current template.

/il removespawn
  Remove nearest spawn pad for the current template.

/il listspawns
  List spawn pad count for the current template.

/il addspawnring <count> <radius> [here|spawn]
  Ring of pads around you (or world spawn).
  Create ring of pads.

/il showpads [seconds]
  Show pad particles temporarily.

--- Teams & rules  [OP] ---

/il randomteams <size> [forceop]
  Randomly assign teams for testing.

/il rules
  Show current build/dig/craft rules.

/il crafting <on|off>
  Toggle crafting in match.

/il build enable
/il build disable
/il build mode <whitelist|blacklist>
/il build add <material>
/il build remove <material>
/il build list
  Build permission management.

/il dig enable
/il dig disable
/il dig mode <whitelist|blacklist>
/il dig add <material>
/il dig remove <material>
/il dig list
  Dig permission management.

--- Live match settings  [OP] ---

/il set teamsize <n>
/il set countdown <seconds>
/il set penaltyheight <blocks>
/il set titlecolor <color>

/il border set <start> <end> <delaySec> <durationSec>
  World border parameters.

/il toggle deathmsgs
/il toggle cannon
/il toggle defaultteams
/il toggle launchonmove
  Feature toggles.

--- Loot systems  [OP] ---

/il giftdrop
  Trigger gift drop for self.

/il envoy start
/il envoy stop
/il envoy addloc
/il envoy clearlocs
/il envoy listlocs
/il envoy wave [count]
/il envoy random [count]
/il envoy gui
  Envoy crate system.

/il addchests start
/il addchests stop
/il addchests give
  Envoy chest marking mode.

/il replenish T1
/il replenish T2
/il replenish T3
/il replenish T4
  Manual tier trigger.

/il replenish mode start
/il replenish mode stop
/il replenish mode give
  Replenish wand mode.

--- Deprecated ---

/il game
/il gameedit
  DEPRECATED → use /hch template worlds … or /hch gui maps

================================================================================
HOTBAR & GUI ENTRY (no command — for reference)
================================================================================

Compass (hub-world.yml slot, default 5)
  Opens LobbyJoinMechanicMenu

Nether star (slot 9, hub)
  Opens HubPartyMenu

Match Team item (slot 3, limbo only)
  Opens HubTeamMenu

Barrier (slot 9, limbo only)
  Leave lobby → hub

================================================================================
COMMAND OWNERSHIP QUICK MAP
================================================================================

Hub spawn           /hch sethub, /hub
Join game           /hch join, compass GUI
Party               /hch party, /il party
Match team          limbo slot 3 GUI (not a command)
Lobby admin         /hch lobby, /hch gui lobby
Template registry   /hch template set/list/…
IL worlds admin     /hch template worlds …, /hch gui maps
Limbo zone/spawn    /hch template limbo …
Template policy     GUIs only (admin shell → policies)
Match queue         /hch queue
Alliances           /ally, /enemy
Match control       /il start, /il stop
Spawn pads          /il addspawn, ring, …
Envoys/replenish    /il envoy, /il replenish, /il admin
Hub PvP zone        /hch hubworld pvp …
