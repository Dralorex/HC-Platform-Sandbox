package com.hollowcrown.hungergames.commands;

import com.hollowcrown.hub.team.TeamManager;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.ChatInput;
import com.hollowcrown.hungergames.managers.*;
import com.hollowcrown.hub.gui.TeamMainMenu;
import com.hollowcrown.hub.gui.TeamsMenu;
import com.hollowcrown.hungergames.gui.AdminControlCenterMenu;
import com.hollowcrown.hub.team.TeamNamingRules;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.Random;
import java.util.Set;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import com.hollowcrown.hungergames.gui.EnvoyAdminMenu;

public class HGCommand implements CommandExecutor, TabCompleter {

    private final GameManager game;
    //private final TeamManager teams;
    private final SpawnManager spawns;
    private final ChatInput chatInput;

    public HGCommand(GameManager game, SpawnManager spawns, ChatInput chatInput) {
        this.game = game;
        //this.teams = teams;
        this.spawns = spawns;
        this.chatInput = chatInput;
    }

    private boolean requireRole(CommandSender sender, String action, ServerRoleManager.Role... allowed) {
        ServerRoleManager.Role current = HungerGamesPlugin.get().serverRole().role();
        for (ServerRoleManager.Role role : allowed) {
            if (role == current) return true;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < allowed.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(allowed[i].name().toLowerCase());
        }
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                "§cThis action is disabled on " + current.name().toLowerCase() + " role. Allowed: " + sb));
        return false;
    }

    /** Resolve template key for spawn-pad commands; player must stand in a template/lobby/match world. */
    private Optional<String> requireSpawnTemplateKey(Player p) {
        var tk = HungerGamesPlugin.get().templates().resolveTemplateKeyForWorld(p.getWorld().getName());
        if (tk.isEmpty()) {
            p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                    "\u00A7cStand in a template editor, lobby, or match world to edit spawn pads."));
        }
        return tk;
    }

    private void sendHelp(CommandSender sender, String label) {
        boolean op = sender.isOp();
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§6HCIronLegacy Commands"));

        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§bPlayer Commands"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " help§7 - show this help menu"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " party§7 - open Hollow Crown Hub party menu"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/hub§7 (or /lobby) — teleport to Hollow Crown hub + utility hotbar"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " teams§7 - in lobby: Hub party menu; otherwise OP teams/main GUI"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " leaderboard|lb§7 - show top kills"));

        if (!op) return;

        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§bAdmin - Menus/Worlds"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " gui§7 - open teams/main menu"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " admin§7 - open unified admin control center"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/hch template create <key>§7 - create editable map (Hub)"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/hch template worlds …§7 §8|§7 §e/hch gui maps§7 — templates, publish, snapshots (was /" + label + " game / gameedit)"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " joingame <worldname>§7 - OP direct world join (bypass cap)"));

        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§bAdmin - Hub world (HollowCrownHub)"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§7Use §e/hch sethub§r, §e/hub§r, §e/hch hubworld …§7 for spawn, radius, build mode, pads."));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§bAdmin - Lobby / Match"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " hostilemobspawn <true|false|t|f>§7 - hostile spawns in §elobby§7 worlds"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " passivemobspawn <true|false|t|f>§7 - passive spawns in §elobby§7 worlds"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " start [lobby_world|template]§7 - force lobby match countdown (like lobby full)"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " stop§7 - stop match + cancel lobby queues, send players to hub"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e/" + label + " reload§7 - reload config/rules/templates/icons"));

        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7bAdmin - Spawns/Teams/Rules"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " addspawn|clearspawn [closest|all]|listspawns"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " addspawnring <count> <radius> [here|spawn]"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " showpads [seconds]"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " randomteams <size> [forceop]"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " rules"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " crafting <on|off>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " dispenser <on|off>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " build <enable|disable|mode|add|remove|list>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " dig <enable|disable|mode|add|remove|list>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " set <teamsize|countdown|penaltyheight|titlecolor> <value>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " border set <start> <end> <delaySec> <durationSec>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " toggle <deathmsgs|cannon|defaultteams|launchonmove>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " giftdrop"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " envoy <start|stop|addloc|clearlocs|listlocs|wave|random|gui>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " addchests <start|stop|give>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " massaddchests <radius> <placeholderblock> <tier>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7e/" + label + " replenish <T1|T2|T3|T4|mode>"));
    }

    // colors for random team assignment
    private static final ChatColor[] ALLOWED_COLORS = {
            ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
            ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
            ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
            ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
            // (BLACK omitted for readability)
    };

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "envoy" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only.")); return true; }
                if (!p.isOp()) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOnly OPs can use envoy commands.")); return true; }
                var env = HungerGamesPlugin.get().envoys();
                var tkWorld = HungerGamesPlugin.get().templates().resolveTemplateKeyForWorld(p.getWorld().getName());
                if (args.length < 2) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " envoy <start|stop|addloc|clearlocs|listlocs|wave|random|gui>")); return true; }
                switch (args[1].toLowerCase()) {
                    case "start" -> {
                        if (tkWorld.isEmpty()) {
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cStand in a template or match world."));
                            return true;
                        }
                        env.start(tkWorld.get());
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aEnvoys started for this map."));
                    }
                    case "stop" -> { env.stop(); sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eEnvoys stopped.")); }
                    case "addloc" -> {
                        if (tkWorld.isEmpty()) {
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cStand in a template or match world."));
                            return true;
                        }
                        env.addLocation(p.getLocation(), tkWorld.get());
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aAdded envoy location."));
                    }
                    case "clearlocs" -> {
                        if (tkWorld.isEmpty()) {
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cStand in a template or match world."));
                            return true;
                        }
                        env.clearLocations(tkWorld.get());
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eCleared envoy locations for this map."));
                    }
                    case "listlocs" -> {
                        if (tkWorld.isEmpty()) {
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cStand in a template or match world."));
                            return true;
                        }
                        int n = env.getSpawnLocations(tkWorld.get()).size();
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§6Envoy locations (this map): " + n));
                    }
                    case "wave" -> {
                        if (tkWorld.isEmpty()) {
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cStand in a template or match world."));
                            return true;
                        }
                        int count = 3;
                        if (args.length >= 3) try { count = Math.max(1, Integer.parseInt(args[2])); } catch (Exception ignored) {}
                        env.spawnWave(tkWorld.get(), count);
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aSpawned envoy wave of " + count + "."));
                    }
                    case "random" -> {
                        if (tkWorld.isEmpty()) {
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cStand in a template or match world."));
                            return true;
                        }
                        int count = 1;
                        if (args.length >= 3) try { count = Math.max(1, Integer.parseInt(args[2])); } catch (Exception ignored) {}
                        env.spawnRandomForagerDrops(tkWorld.get(), count);
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aSpawned " + count + " random forager drop(s)."));
                    }
                    case "gui" -> {
                        if (tkWorld.isEmpty()) {
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cStand in a template or match world (not hub)."));
                            return true;
                        }
                        p.openInventory(new EnvoyAdminMenu(tkWorld.get()).getInventory());
                    }
                    default -> sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUnknown envoy subcommand."));
                }
                return true;
            }
            case "giftdrop" -> {
                if (!(sender instanceof Player)) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only.")); return true; }
                var gd = HungerGamesPlugin.get().giftDrops();
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                gd.spawnGiftDropsForPlayers(players);
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aGift drops spawned for " + players.size() + " players."));
                return true;
            }
            case "addchests" -> {
                if (!(sender instanceof Player p) || !p.isOp()) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOPs only.")); return true; }
                var add = HungerGamesPlugin.get().addMode();
                if (args.length < 2) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " addchests <start|stop|give>")); return true; }
                switch (args[1].toLowerCase()) {
                    case "start" -> add.setEnabled(p, true);
                    case "stop" -> add.setEnabled(p, false);
                    case "give" -> add.giveWand(p);
                    default -> sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUnknown subcommand."));
                }
                return true;
            }
            case "massaddchests", "addmanychests" -> { // It's easy to add aliases to commands if you want them. I've gone ahead and added "addmanychests" as an alias to this command, but you can remove it if you want.
                if (!(sender instanceof Player p) || !p.isOp()) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOPs only.")); return true; }
                if (args.length < 3) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " massaddchests <radius> <placeholderblock> <tier>")); return true; }
                //Extract radius as a number
                String clean = args[1].replaceAll("[^0-9.-]", "");
                clean = clean.replaceAll("(?<=.)-", "").replaceFirst("\\.", "D").replaceAll("\\.", "").replace("D", ".");
                if (clean.isEmpty()){
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cInvalid radius."));
                    return true;
                }
                int radius;
                try{
                    radius = (int)(Double.parseDouble(clean)); // Decimal places should be treated as so, but then rounded down and ignored.
                } catch (NumberFormatException e) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cInvalid radius."));
                    return true;
                }

                //Extract material
                Material material = Material.getMaterial(args[2]);
                if(material == null || !material.isBlock()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cInvalid placeholder block."));
                    return true;
                }

                String tier = args[3];
                if(!List.of("T1", "T2", "T3", "T4").contains(tier)){ //If you ever decide to make ReplenishManager's tierlist (as found in startScheduleInternal()) public, replace this so it references that instead.
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cInvalid tier."));
                    return true;
                }

                HungerGamesPlugin.get().getLogger().info("A mass place operation with a radius of " + radius + " is now running. Long tick times may result from chunks being loaded during the mass replacement process. Paper may report that the server has not responded for 10 seconds. Please ignore those messages until after the replacement has been completed.");

                World world = p.getLocation().getWorld();
                int minX = p.getLocation().getBlockX() - radius;
                int minY = Math.max(p.getLocation().getBlockY() - radius, -64);
                int minZ = p.getLocation().getBlockZ() - radius;
                int maxX = p.getLocation().getBlockX() + radius;
                int maxY = Math.min(p.getLocation().getBlockY() + radius, 319);
                int maxZ = p.getLocation().getBlockZ() + radius;
                int total = (maxY - minY) * ((2 * radius) + 1) * ((2 * radius) + 1);
                final int[] currentXYZAndProgress = {minX, minY, minZ, 0, 0}; //This is stupid, but it lets me increment it while using it in lambda expressions.
                final Runnable[] taskHolder = {null}; //Same trick used to let a lambda reference itself for recursive calls
                Runnable task = () -> {
                    boolean speedTest = currentXYZAndProgress[0] == minX && currentXYZAndProgress[1] == minY && currentXYZAndProgress[2] == minZ;
                    Instant instant = null;
                    if(speedTest)
                        instant = Instant.now();
                    int i = 0;
                    int iterationsPerTick = 262144; //2^18
                    //while (currentXYZAndProgress[0] <= maxX){
                    //    while (currentXYZAndProgress[1] <= maxY){
                    //        while (currentXYZAndProgress[2] <= maxZ){
                    while (i < iterationsPerTick &&
                            currentXYZAndProgress[0] <= maxX) {

                        int x = currentXYZAndProgress[0];
                        int y = currentXYZAndProgress[1];
                        int z = currentXYZAndProgress[2];

                        Block block = world.getBlockAt(x, y, z);

                        if (block.getType() == material) {
                            // Your existing block-processing code
                            //HungerGamesPlugin.get().getLogger().info("Found block at " + currentXYZAndProgress[0] + ", " + currentXYZAndProgress[1] + ", " + currentXYZAndProgress[2]);
                            if(block.getBlockData() instanceof Directional originalDirectional){
                                //HungerGamesPlugin.get().getLogger().info("Template is directional");
                                BlockFace face = originalDirectional.getFacing();
                                world.setType(currentXYZAndProgress[0], currentXYZAndProgress[1], currentXYZAndProgress[2], Material.CHEST);
                                BlockData data = world.getBlockData(currentXYZAndProgress[0], currentXYZAndProgress[1], currentXYZAndProgress[2]);
                                if(data instanceof Directional directional) {
                                    //HungerGamesPlugin.get().getLogger().info("Chest is directional");
                                    switch (face) {
                                        case NORTH -> directional.setFacing(BlockFace.NORTH);
                                        case EAST -> directional.setFacing(BlockFace.EAST);
                                        case SOUTH -> directional.setFacing(BlockFace.SOUTH);
                                        case WEST -> directional.setFacing(BlockFace.WEST);
                                        case UP -> directional.setFacing(BlockFace.NORTH);
                                        case DOWN -> directional.setFacing(BlockFace.NORTH);
                                        case NORTH_EAST -> directional.setFacing(BlockFace.NORTH);
                                        case NORTH_WEST -> directional.setFacing(BlockFace.NORTH);
                                        case SOUTH_EAST -> directional.setFacing(BlockFace.SOUTH);
                                        case SOUTH_WEST -> directional.setFacing(BlockFace.SOUTH);
                                        case WEST_NORTH_WEST -> directional.setFacing(BlockFace.WEST);
                                        case NORTH_NORTH_WEST -> directional.setFacing(BlockFace.NORTH);
                                        case NORTH_NORTH_EAST -> directional.setFacing(BlockFace.NORTH);
                                        case EAST_NORTH_EAST -> directional.setFacing(BlockFace.EAST);
                                        case EAST_SOUTH_EAST -> directional.setFacing(BlockFace.EAST);
                                        case SOUTH_SOUTH_EAST -> directional.setFacing(BlockFace.SOUTH);
                                        case SOUTH_SOUTH_WEST -> directional.setFacing(BlockFace.SOUTH);
                                        case WEST_SOUTH_WEST -> directional.setFacing(BlockFace.WEST);
                                        case SELF -> directional.setFacing(BlockFace.NORTH);
                                    }
                                    world.setBlockData(currentXYZAndProgress[0], currentXYZAndProgress[1], currentXYZAndProgress[2], directional);
                                }
                            }
                            else{
                                //HungerGamesPlugin.get().getLogger().info("Template is not directional: " + block.getBlockData());
                                world.setType(currentXYZAndProgress[0], currentXYZAndProgress[1], currentXYZAndProgress[2], Material.CHEST);
                            }
                        }

                        i++;

                        // Move to the next Z
                        currentXYZAndProgress[2]++;

                        // Finished this Z row
                        if (currentXYZAndProgress[2] > maxZ) {
                            currentXYZAndProgress[2] = minZ;
                            currentXYZAndProgress[1]++;

                            // Finished this Y column
                            if (currentXYZAndProgress[1] > maxY) {
                                currentXYZAndProgress[1] = minY;
                                currentXYZAndProgress[0]++;
                            }
                        }
                    }
                                //Block block = world.getBlockAt(currentXYZAndProgress[0], currentXYZAndProgress[1], currentXYZAndProgress[2]);
                                //if(block.getType() == material){
                    /*
                                }

                                i++;
                                currentXYZAndProgress[2]++;
                                if(i > iterationsPerTick)
                                    break;
                            }
                            currentXYZAndProgress[1]++;
                            currentXYZAndProgress[2] = minZ;
                            if(i > iterationsPerTick)
                                break;
                        }
                        currentXYZAndProgress[0]++;
                        currentXYZAndProgress[1] = minY;
                        if(i > iterationsPerTick) {
                            HungerGamesPlugin.get().getLogger().info("breaking at " + currentXYZAndProgress[0] + ", " + currentXYZAndProgress[1] + ", " + currentXYZAndProgress[2]);
                            break;
                        }
                    }*/
                    if(speedTest)
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aEstimated Time:" + DurationFormatUtils.formatDurationHMS(Duration.between(instant, Instant.now()).multipliedBy(total / iterationsPerTick).toMillis())));
                    /*for (int y = finalMinY; y <= finalMaxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            Block block = world.getBlockAt(currentXAndProgress[0], y, z);
                            if(block.getType() == material){
                                if(block.getBlockData() instanceof Directional directional){
                                    BlockFace face = directional.getFacing();
                                    block.setType(Material.CHEST);
                                    if(block.getBlockData() instanceof Directional directional1){
                                        directional1.setFacing(face);
                                    }
                                }
                                else{
                                    block.setType(Material.CHEST);
                                }
                                HungerGamesPlugin.get().replenish().add(block.getLocation(), tier);
                            }
                        }
                    }*/
                    if(currentXYZAndProgress[0] < maxX){
                        //currentXYZAndProgress[0]++;
                        currentXYZAndProgress[3]++;
                        float progress = (float) ((currentXYZAndProgress[3] / Math.ceil((double) total / iterationsPerTick)) * 100);
                        if((int)progress != currentXYZAndProgress[4]){
                            currentXYZAndProgress[4] = (int)progress;
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§a" + (((int)progress)) + "%"));
                        }
                        if(taskHolder[0] != null)
                            Bukkit.getScheduler().runTaskLater(HungerGamesPlugin.get(), taskHolder[0], 1);
                    }
                    else{
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aMass place complete!"));
                    }
                };
                taskHolder[0] = task;

                //While an async task would be helpful here, you can't access the world from one.
                //Instead, this takes the naive approach of pausing every tick after completing a small section of the task, then resuming.
                //This should prevent crashes from watchdog. They may still occur if the radius is too large. With 1024 radius, that's 786,816 block checks per tick. Be careful!
                //sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aStarting mass place... Estimated:" + (int)(((radius * 2f) + 1f) / 20f) + "s"));
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aStarting mass place..."));
                Bukkit.getScheduler().runTaskLater(HungerGamesPlugin.get(), taskHolder[0], 1);

                return true;
            }
            case "replenish" -> {
                if (args.length < 2) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " replenish <T1|T2|T3|T4|mode>")); return true; }
                if (args[1].equalsIgnoreCase("mode")) {
                    if (!(sender instanceof Player p) || !p.isOp()) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOPs only.")); return true; }
                    if (args.length == 2) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " replenish mode <start|stop|give>")); return true; }
                    var rm = HungerGamesPlugin.get().replenishMode();
                    switch (args[2].toLowerCase()) {
                        case "start" -> rm.setEnabled(p, true);
                        case "stop" -> rm.setEnabled(p, false);
                        case "give" -> rm.giveWand(p);
                        default -> sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUnknown subcommand."));
                    }
                    return true;
                }
                String tier = args[1].toUpperCase();
                if (sender instanceof Player pl) {
                    var tk = HungerGamesPlugin.get().templates().resolveTemplateKeyForWorld(pl.getWorld().getName());
                    if (tk.isEmpty()) {
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cStand in a template or match world to replenish."));
                        return true;
                    }
                    HungerGamesPlugin.get().replenish().replenishTier(tier, tk.get(), pl.getWorld());
                } else {
                    HungerGamesPlugin.get().replenish().replenishTier(tier);
                }
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aReplenish " + tier + " triggered."));
                return true;
            }
            case "gui" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only."));
                    return true;
                }
                if (!p.isOp()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOPs only."));
                    return true;
                }
                boolean openTeamsFirst = HungerGamesPlugin.get().getConfig().getBoolean("ui.openTeamsFirst", false);
                if (openTeamsFirst) p.openInventory(new TeamsMenu(p.getWorld(), p).getInventory());
                else p.openInventory(new TeamMainMenu(p).getInventory());
                return true;
            }
            case "teams" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only."));
                    return true;
                }
                if (HungerGamesPlugin.get().preMatchLobby().isInLobby(p)) {
                    HungerGamesPlugin.get().hubPlayerMenus().ifPresentOrElse(
                            m -> m.openPartyMenu(p),
                            () -> p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                                    "§cParty menu unavailable. HollowCrownHub must be installed.")));
                    return true;
                }
                if (!p.isOp()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOPs only."));
                    return true;
                }
                boolean openTeamsFirst = HungerGamesPlugin.get().getConfig().getBoolean("ui.openTeamsFirst", false);
                if (openTeamsFirst) p.openInventory(new TeamsMenu(p.getWorld(), p).getInventory());
                else p.openInventory(new TeamMainMenu(p).getInventory());
                return true;
            }
            case "admin" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only."));
                    return true;
                }
                if (!p.isOp()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOPs only."));
                    return true;
                }
                p.openInventory(new AdminControlCenterMenu().getInventory());
                return true;
            }
            case "party" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only."));
                    return true;
                }
                HungerGamesPlugin.get().hubPlayerMenus().ifPresentOrElse(
                        m -> m.openPartyMenu(p),
                        () -> p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                                "§cParty menu unavailable. HollowCrownHub must be installed.")));
                return true;
            }
            case "leaderboard", "lb" -> {
                var top = HungerGamesPlugin.get().leaderboard().top(10);
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§6Top Kills:"));
                if (top.isEmpty()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§7No kills recorded yet."));
                    return true;
                }
                int rank = 1;
                for (var entry : top) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                            "§e#" + rank++ + " §f" + entry.name() + " §7- §c" + entry.kills()));
                }
                return true;
            }
            case "hostilemobspawn", "hostilemobspawns" -> {
                if (!requireRole(sender, "hostilemobspawn", ServerRoleManager.Role.HUB, ServerRoleManager.Role.STANDALONE)) return true;
                if (args.length < 2) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " hostilemobspawn <true|false|t|f>"));
                    return true;
                }
                if (!(sender instanceof Player p) || !p.isOp()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOPs only."));
                    return true;
                }
                String rawH = args[1].toLowerCase();
                Boolean onH = switch (rawH) {
                    case "true", "t", "on", "yes" -> true;
                    case "false", "f", "off", "no" -> false;
                    default -> null;
                };
                if (onH == null) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUse true/false or t/f."));
                    return true;
                }
                HungerGamesPlugin.get().lobby().setAllowHostileMobSpawns(onH);
                HungerGamesPlugin.get().history().log(p.getName(), "set_hostile_mob_spawn", onH.toString());
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aLobby hostile mob spawns: " + onH));
                return true;
            }
            case "passivemobspawn", "passivemobspawns" -> {
                if (!requireRole(sender, "passivemobspawn", ServerRoleManager.Role.HUB, ServerRoleManager.Role.STANDALONE)) return true;
                if (args.length < 2) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " passivemobspawn <true|false|t|f>"));
                    return true;
                }
                if (!(sender instanceof Player p) || !p.isOp()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOPs only."));
                    return true;
                }
                String rawP = args[1].toLowerCase();
                Boolean onP = switch (rawP) {
                    case "true", "t", "on", "yes" -> true;
                    case "false", "f", "off", "no" -> false;
                    default -> null;
                };
                if (onP == null) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUse true/false or t/f."));
                    return true;
                }
                HungerGamesPlugin.get().lobby().setAllowPassiveMobSpawns(onP);
                HungerGamesPlugin.get().history().log(p.getName(), "set_passive_mob_spawn", onP.toString());
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aLobby passive mob spawns: " + onP));
                return true;
            }
            case "joingame" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only."));
                    return true;
                }
                if (!p.isOp()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOPs only."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " joingame <worldname>"));
                    return true;
                }
                World target = Bukkit.getWorld(args[1]);
                if (target == null) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cWorld not found: " + args[1]));
                    return true;
                }
                game.markJoinIntentBypass(p);
                p.teleport(target.getSpawnLocation());
                HungerGamesPlugin.get().history().log(p.getName(), "join_game_bypass", target.getName());
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aJoined game world (OP bypass): " + target.getName()));
                return true;
            }
            case "game", "gameedit" -> {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                        "§eMoved:§7 use §f/hch template worlds …§7 or §f/hch gui maps§7 (HollowCrownHub + HCIronLegacy)."));
                return true;
            }
            case "reload" -> {
                if (!sender.isOp()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOPs only."));
                    return true;
                }
                HungerGamesPlugin.get().reloadConfig();
                try { HungerGamesPlugin.get().files().reloadAll(); } catch (Throwable ignored) {}
                spawns.load();
                try { HungerGamesPlugin.rules().load(); } catch (Throwable ignored) {}
                try { HungerGamesPlugin.get().templates().reload(); } catch (Throwable ignored) {}
                var recovery = HungerGamesPlugin.get().templates().recoverStaleInstances();
                try { HungerGamesPlugin.get().icons().reload(); } catch (Throwable ignored) {}
                var iconReport = HungerGamesPlugin.get().icons().validateIcons();
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aGame files/cache reloaded."));
                HungerGamesPlugin.get().history().log(sender.getName(), "reload_all", "via_/il_reload");
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                        "§7Instances recovery: scanned=" + recovery.scanned()
                                + ", cleaned=" + recovery.cleaned()
                                + ", skipped=" + recovery.skipped()
                                + ", failed=" + recovery.failed()));
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                        "§7Icons: total=" + iconReport.total() + ", valid=" + iconReport.valid() + ", warnings=" + iconReport.warnings().size()));
                int maxWarnings = Math.min(5, iconReport.warnings().size());
                for (int i = 0; i < maxWarnings; i++) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§e- " + iconReport.warnings().get(i)));
                }
                if (iconReport.warnings().size() > maxWarnings) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                            "§7... and " + (iconReport.warnings().size() - maxWarnings) + " more warning(s)."));
                }
                return true;
            }
            case "start" -> {
                if (!sender.isOp()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7cOPs only."));
                    return true;
                }
                if (!requireRole(sender, "start", ServerRoleManager.Role.GAME, ServerRoleManager.Role.STANDALONE)) return true;
                String key = args.length >= 2 ? args[1].trim() : null;
                if ((key == null || key.isBlank()) && sender instanceof Player p) {
                    key = p.getWorld().getName();
                }
                if (key == null || key.isBlank()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                            "§cUsage: /" + label + " start <lobby_instance_world|template_key>§7 (or stand in the lobby instance)"));
                    return true;
                }
                boolean ok = HungerGamesPlugin.get().preMatchLobby().forceBeginTeleportCountdown(key);
                if (ok) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                            "§aLobby match countdown started (same as lobby full)."));
                } else {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                            "§cNo active lobby queue for §f" + key + "§c. Players must join lobby first."));
                }
                return true;
            }
            case "stop" -> {
                if (!requireRole(sender, "stop", ServerRoleManager.Role.GAME, ServerRoleManager.Role.STANDALONE)) return true;
                HungerGamesPlugin.get().preMatchLobby().abortAllQueuesAndKickToHub();
                game.stopGame(true);
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eStopped lobby queues and match (if any); players sent to hub."));
                return true;
            }
            case "addspawn" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only.")); return true; }
                var tk = requireSpawnTemplateKey(p);
                if (tk.isEmpty()) return true;
                Location l = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                spawns.add(tk.get(), l);
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                        "\u00A7aAdded spawn pad for \u00A7f" + tk.get() + "\u00A7a at "
                                + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ()));
                return true;
            }
            case "clearspawn" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only.")); return true; }
                var tk = requireSpawnTemplateKey(p);
                if (tk.isEmpty()) return true;
                String mode = args.length >= 2 ? args[1].trim().toLowerCase(java.util.Locale.ROOT) : "";
                if (mode.isEmpty() || mode.equals("closest") || mode.equals("near") || mode.equals("nearest")) {
                    mode = "closest";
                } else if (!mode.equals("all")) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                            "\u00A7cUsage: /" + label + " clearspawn [closest|all]"));
                    return true;
                }
                if (mode.equals("all")) {
                    int before = spawns.count(tk.get());
                    spawns.clear(tk.get());
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                            "\u00A7eCleared \u00A7f" + before + "\u00A7e spawn pad(s) for \u00A7f" + tk.get() + "\u00A7e."));
                } else {
                    var removed = spawns.removeNearest(tk.get(), p.getLocation());
                    if (removed == null) {
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7cNo spawn pads to remove."));
                    } else {
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                                "\u00A7eRemoved closest pad at " + removed.getBlockX() + ","
                                        + removed.getBlockY() + "," + removed.getBlockZ()));
                    }
                }
                return true;
            }
            case "listspawns" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only.")); return true; }
                var tk = requireSpawnTemplateKey(p);
                if (tk.isEmpty()) return true;
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                        "\u00A76Spawn pads for \u00A7f" + tk.get() + "\u00A76: " + spawns.count(tk.get())));
                return true;
            }
            case "addspawnring" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only.")); return true; }
                var tk = requireSpawnTemplateKey(p);
                if (tk.isEmpty()) return true;
                if (args.length < 3) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7cUsage: /" + label + " addspawnring <count> <radius> [here|spawn]")); return true; }
                try {
                    int count = Integer.parseInt(args[1]);
                    double radius = Double.parseDouble(args[2]);
                    boolean useSpawn = args.length >= 4 && args[3].equalsIgnoreCase("spawn");
                    var added = spawns.addRing(tk.get(), p.getLocation(), count, radius, useSpawn);
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                            "\u00A7aAdded " + added.size() + " pads for \u00A7f" + tk.get()
                                    + "\u00A7a in a ring (radius " + radius + ")."));
                } catch (Exception ex) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7cNumbers expected for count and radius.")); }
                return true;
            }
            case "showpads" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Players only.")); return true; }
                var tk = requireSpawnTemplateKey(p);
                if (tk.isEmpty()) return true;
                int seconds = 15; // default display time
                if (args.length >= 2) {
                    try { seconds = Math.max(1, Integer.parseInt(args[1])); } catch (Exception ignored) {}
                }
                List<Location> padLocs = spawns.getSpawnPads(tk.get(), p.getWorld());
                if (padLocs.isEmpty()) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                            "\u00A7cNo spawn pads set for \u00A7f" + tk.get() + "\u00A7c."));
                    return true;
                }

                final int period = 10; // ticks (0.5s)
                final int totalTicks = seconds * 20;

                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                        "\u00A76Showing spawn pads for \u00A7f" + tk.get() + "\u00A76 for " + seconds + "s (" + padLocs.size() + " pads)."));

                new BukkitRunnable() {
                    int elapsed = 0;
                    final Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 255, 85), 1.5f); // lime

                    @Override public void run() {
                        padLocs.forEach(loc -> {
                            var w = loc.getWorld();
                            if (w == null) return;

                            // Small ring marker
                            double y = loc.getY() + 0.2;
                            double r = 0.6;
                            for (int i = 0; i < 16; i++) {
                                double a = (Math.PI * 2 * i) / 16.0;
                                double x = loc.getX() + r * Math.cos(a);
                                double z = loc.getZ() + r * Math.sin(a);
                                w.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
                            }

                            // Center sparkle column
                            for (double yy = 0.2; yy <= 2.2; yy += 0.5) {
                                w.spawnParticle(Particle.END_ROD, loc.getX(), loc.getY() + yy, loc.getZ(), 1, 0, 0, 0, 0);
                            }
                        });

                        elapsed += period;
                        if (elapsed >= totalTicks) cancel();
                    }
                }.runTaskTimer(HungerGamesPlugin.get(), 0L, period);

                return true;
            }

            // ===== RULE SUMMARY =====
            case "rules" -> {
                for (String line : HungerGamesPlugin.rules().summaryLines()) sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(line));
                return true;
            }

            // ===== CRAFTING TOGGLE =====
            case "crafting" -> {
                if (args.length < 2) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " crafting <on|off>")); return true; }
                boolean on = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("enable");
                HungerGamesPlugin.rules().setCraftingEnabled(on);
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eCrafting: " + (on ? "§aENABLED" : "§cDISABLED")));
                return true;
            }
            //      DISPENSER TOGGLE
                case "dispenser" -> {
                    if (!sender.isOp()) {
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                                "§cOnly OPs can change dispenser interaction."
                        ));
                        return true;
                    }
                
                    if (args.length < 2 ||
                            (!args[1].equalsIgnoreCase("on") &&
                             !args[1].equalsIgnoreCase("off"))) {
                
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                                "§cUsage: /" + label + " dispenser <on|off>"
                        ));
                        return true;
                    }
                
                    boolean enabled = args[1].equalsIgnoreCase("on");
                
                    HungerGamesPlugin.get().buildRules()
                            .setDispenserInteractionEnabled(enabled);
                
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                            enabled
                                    ? "§aDispenser interaction enabled."
                                    : "§cDispenser interaction disabled."
                    ));
                
                    return true;
                }
                
            // ===== BUILD with categories =====
            case "build" -> {
                if (args.length < 2) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " build <enable|disable|mode|add|remove|list>"));
                    return true;
                }
                var rules = HungerGamesPlugin.rules();
                switch (args[1].toLowerCase()) {
                    case "enable" -> { rules.setBuildEnabled(true); sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eBuilding: §aENABLED")); }
                    case "disable" -> { rules.setBuildEnabled(false); sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eBuilding: §cDISABLED")); }
                    case "mode" -> {
                        if (args.length < 3) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " build mode <whitelist|blacklist>")); return true; }
                        if (!rules.setBuildMode(args[2])) sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cMode must be whitelist or blacklist."));
                        else sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eBuild mode set to §6" + args[2].toLowerCase()));
                    }
                    case "add" -> {
                        if (args.length < 3) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " build add <MATERIAL|category:<name>|cat:<name>>")); return true; }
                        List<Material> mats = parseMaterialOrCategory(args[2]);
                        if (mats.isEmpty()) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cNo matching materials.")); return true; }
                        int added = rules.addBuildAll(mats);
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aAdded " + added + " entries to BUILD list."));
                    }
                    case "remove" -> {
                        if (args.length < 3) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " build remove <MATERIAL|category:<name>|cat:<name>>")); return true; }
                        List<Material> mats = parseMaterialOrCategory(args[2]);
                        if (mats.isEmpty()) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cNo matching materials.")); return true; }
                        int removed = rules.delBuildAll(mats);
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eRemoved " + removed + " entries from BUILD list."));
                    }
                    case "list" -> sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§6Build " + rules.getBuildMode() + " list (" + rules.getBuildList().size() + "): "
                            + "§7" + rules.getBuildList().stream().map(Enum::name).sorted().collect(Collectors.joining(", "))));
                    default -> {}
                }
                return true;
            }

            // ===== DIG with categories =====
            case "dig" -> {
                if (args.length < 2) {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " dig <enable|disable|mode|add|remove|list>"));
                    return true;
                }
                var rules = HungerGamesPlugin.rules();
                switch (args[1].toLowerCase()) {
                    case "enable" -> { rules.setDigEnabled(true); sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eDigging: §aENABLED")); }
                    case "disable" -> { rules.setDigEnabled(false); sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eDigging: §cDISABLED")); }
                    case "mode" -> {
                        if (args.length < 3) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " dig mode <whitelist|blacklist>")); return true; }
                        if (!rules.setDigMode(args[2])) sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cMode must be whitelist or blacklist."));
                        else sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eDig mode set to §6" + args[2].toLowerCase()));
                    }
                    case "add" -> {
                        if (args.length < 3) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " dig add <MATERIAL|category:<name>|cat:<name>>")); return true; }
                        List<Material> mats = parseMaterialOrCategory(args[2]);
                        if (mats.isEmpty()) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cNo matching materials.")); return true; }
                        int added = rules.addDigAll(mats);
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aAdded " + added + " entries to DIG list."));
                    }
                    case "remove" -> {
                        if (args.length < 3) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " dig remove <MATERIAL|category:<name>|cat:<name>>")); return true; }
                        List<Material> mats = parseMaterialOrCategory(args[2]);
                        if (mats.isEmpty()) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cNo matching materials.")); return true; }
                        int removed = rules.delDigAll(mats);
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eRemoved " + removed + " entries from DIG list."));
                    }
                    case "list" -> sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§6Dig " + rules.getDigMode() + " list (" + rules.getDigList().size() + "): "
                            + "§7" + rules.getDigList().stream().map(Enum::name).sorted().collect(Collectors.joining(", "))));
                    default -> {}
                }
                return true;
            }

            // ===== RANDOM TEAMS: /hg randomteams <size> [forceop] =====
            case "randomteams" -> {
                if (args.length < 2) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " randomteams <size> [forceop]")); return true; }
                int size = parseInt(sender, args[1]);
                if (size == Integer.MIN_VALUE || size < 1) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cSize must be >= 1")); return true; }
                boolean includeOps = args.length >= 3 && args[2].equalsIgnoreCase("forceop");
                runRandomTeams(size, includeOps, sender, label);
                return true;
            }

            case "set" -> {
                if (args.length < 3) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " set <teamsize|countdown|penaltyheight|titlecolor> <value>")); return true; }
                String key = args[1].toLowerCase();
                switch (key) {
                    case "teamsize" -> {
                        int val = parseInt(sender, args[2]); if (val == Integer.MIN_VALUE) return true;
                        var pl = HungerGamesPlugin.get();
                        pl.getConfig().set("game.teamSize", val); pl.saveConfig();
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aTeam size = " + val)); return true;
                    }
                    case "countdown" -> {
                        int val = parseInt(sender, args[2]); if (val == Integer.MIN_VALUE) return true;
                        var pl = HungerGamesPlugin.get();
                        pl.getConfig().set("game.countdownSeconds", val); pl.saveConfig();
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aCountdown = " + val)); return true;
                    }
                    case "penaltyheight" -> {
                        int val = parseInt(sender, args[2]); if (val == Integer.MIN_VALUE) return true;
                        var pl = HungerGamesPlugin.get();
                        pl.getConfig().set("game.freezePenaltyHeight", val); pl.saveConfig();
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aPenalty height = " + val)); return true;
                    }
                    case "titlecolor" -> {
                        try {
                            @SuppressWarnings("deprecation") // Needed for config compatibility
                            var c = ChatColor.valueOf(args[2].toUpperCase());
                            var pl = HungerGamesPlugin.get();
                            pl.getConfig().set("game.countdownColor", c.name()); pl.saveConfig();
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aCountdown title color = " + c.name())); return true;
                        } catch (Exception ex) {
                            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUse a valid ChatColor, e.g. GOLD or RED.")); return true;
                        }
                    }
                    default -> { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUnknown key.")); return true; }
                }
            }
            case "border" -> {
                if (args.length == 6 && args[1].equalsIgnoreCase("set")) {
                    try {
                        double start = Double.parseDouble(args[2]);
                        double end = Double.parseDouble(args[3]);
                        int delay = Integer.parseInt(args[4]);
                        int duration = Integer.parseInt(args[5]);
                        var pl = HungerGamesPlugin.get();
                        pl.getConfig().set("worldborder.startSize", start);
                        pl.getConfig().set("worldborder.endSize", end);
                        pl.getConfig().set("worldborder.shrinkDelaySeconds", delay);
                        pl.getConfig().set("worldborder.shrinkDurationSeconds", duration);
                        pl.saveConfig();
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aBorder updated: start=" + start + " end=" + end + " delay=" + delay + "s duration=" + duration + "s"));
                    } catch (Exception ex) {
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cNumbers expected."));
                    }
                    return true;
                } else {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " border set <start> <end> <delaySec> <durationSec>"));
                    return true;
                }
            }
            case "toggle" -> {
                if (args.length < 2) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /" + label + " toggle <deathmsgs|cannon|defaultteams|launchonmove>")); return true; }
                var pl = HungerGamesPlugin.get();
                switch (args[1].toLowerCase()) {
                    case "deathmsgs" -> {
                        boolean cur = pl.getConfig().getBoolean("game.suppressDeathMessages", true);
                        pl.getConfig().set("game.suppressDeathMessages", !cur);
                        pl.saveConfig();
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aSuppress death messages: " + (!cur)));
                        return true;
                    }
                    case "cannon" -> {
                        boolean cur = pl.getConfig().getBoolean("game.playCannonOnDeath", true);
                        pl.getConfig().set("game.playCannonOnDeath", !cur);
                        pl.saveConfig();
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aPlay cannon on death: " + (!cur)));
                        return true;
                    }
                    case "defaultteams" -> {
                        boolean cur = pl.getConfig().getBoolean("ui.openTeamsFirst", false);
                        pl.getConfig().set("ui.openTeamsFirst", !cur);
                        pl.saveConfig();
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aTeams-first GUI: " + (!cur)));
                        return true;
                    }
                    case "launchonmove" -> {
                        boolean cur = pl.getConfig().getBoolean("game.launchOnMove", true);
                        pl.getConfig().set("game.launchOnMove", !cur);
                        pl.saveConfig();
                        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aLaunch on move: " + (!cur)));
                        return true;
                    }
                    default -> { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUnknown toggle.")); return true; }
                }
            }
        }

        return true;
    }

    private int parseInt(CommandSender sender, String s) {
        try { return Integer.parseInt(s); }
        catch (Exception ex) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cNumber expected.")); return Integer.MIN_VALUE; }
    }

    /** token may be "STONE", "category:logs", "cat:planks", or any substring like "category:slab" */
    private List<Material> parseMaterialOrCategory(String token) {
        if (token == null) return List.of();

        String t = token.trim();
        boolean isCat = t.regionMatches(true, 0, "category:", 0, 9) || t.regionMatches(true, 0, "cat:", 0, 4);
        if (isCat) {
            String key = t.contains(":") ? t.substring(t.indexOf(':') + 1) : "";
            if (key.isBlank()) return List.of();
            Set<Material> set = HungerGamesPlugin.rules().resolveCategory(key);
            return new ArrayList<>(set);
        }

        Material m = matchMaterial(t);
        return (m == null) ? List.of() : List.of(m);
    }

    // ===== fuzzy material matcher =====
    private Material matchMaterial(String token) {
        if (token == null) return null;
        token = token.trim().toUpperCase();
        try { return Material.valueOf(token); }
        catch (Exception ignored) {}
        for (Material m : Material.values()) {
            if (m.name().startsWith(token)) return m;
        }
        return null;
    }

    /** Core implementation for random team assignment. Wipes all old teams first. */
    @SuppressWarnings("deprecation") // Team colors still use ChatColor enum
    private void runRandomTeams(int size, boolean includeOps, CommandSender sender, String label) {
        // keep the configured team size in sync
        var pl = Bukkit.getPluginManager().getPlugin("HollowHunger");
        pl.getConfig().set("game.teamSize", size); pl.saveConfig();

        // Nuke old teams (uses TeamManager.clearAllTeams() if present)
        //wipeAllTeamsHard();

        // Build pool (exclude OPs unless forceop specified)
        List<Player> pool = Bukkit.getOnlinePlayers().stream()
                .filter(p -> includeOps || !p.isOp())
                .collect(Collectors.toList());

        if (pool.isEmpty()) { sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cNo eligible players to assign.")); return; }

        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getOrCreateWorldInstance(pool.getFirst().getWorld());
        //Actually nuke old teams
        teamManagerInstance.clearAllTeams();

        Collections.shuffle(pool, new Random());

        //Set<ChatColor> used = new HashSet<>();
        //Random rnd = new Random();
        int made = 0;
        //int teamIndex = 1;

        for (int i = 0; i < pool.size(); i += size) {
            List<Player> group = pool.subList(i, Math.min(i + size, pool.size()));

            String name = TeamNamingRules.randomTeamName(teamManagerInstance);
            Color color = teamManagerInstance.getRandomUnusedColor();
            TeamManager.Team t = new TeamManager.Team(name, color, teamManagerInstance); //The new teams automatically assign the first player as the team leader.
            group.forEach((player) -> t.members.add(player.getUniqueId()));
            made++;
        }

        //try { teams.save(); } catch (Throwable ignored) {}
        //game.applyTeamColors();
        teamManagerInstance.sendAllFakeTeamPackets();
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aRandomized " + pool.size() + " players into " + made + " teams of size " + size + (includeOps ? " (including OPs)." : " (excluding OPs).")));
    }

    /** Try to delete all existing teams cleanly. Uses TeamManager.clearAllTeams() if present, otherwise falls back. */
    /*private void wipeAllTeamsHard() {
        try {
            teams.clearAllTeams();
            return;
        } catch (Throwable ignored) {}

        try { teams.save(); } catch (Throwable ignored) {}
    }*/

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return partial(args[0], List.of(
                "help","gui","teams","party","leaderboard","lb","admin","hostilemobspawn","hostilemobspawns","passivemobspawn","passivemobspawns","joingame","start","stop","addspawn","clearspawn",
                "listspawns","addspawnring","showpads","randomteams",
                "set","border","toggle","reload","envoy","giftdrop","addchests","massaddchests","addmanychests","replenish",
                "rules","crafting","dispenser","build","dig"
        ));
        if (args.length == 2 && args[0].equalsIgnoreCase("addchests")) return partial(args[1], List.of("start","stop","give"));
        if (args.length == 2 && args[0].equalsIgnoreCase("replenish")) return partial(args[1], List.of("T1","T2","T3","T4","mode"));

        if (args.length == 2 && args[0].equalsIgnoreCase("randomteams"))
            return partial(args[1], List.of("2","3","4","5","6"));
        if (args.length == 3 && args[0].equalsIgnoreCase("randomteams"))
            return partial(args[2], List.of("forceop"));

        if (args.length == 2 && args[0].equalsIgnoreCase("crafting"))
            return partial(args[1], List.of("on","off"));
         if (args.length == 2 && args[0].equalsIgnoreCase("dispenser"))
            return partial(args[1], List.of("on","off"));

        if (args.length == 2 && (args[0].equalsIgnoreCase("build") || args[0].equalsIgnoreCase("dig")))
            return partial(args[1], List.of("enable","disable","mode","add","remove","list"));

        if (args.length == 3 && (args[0].equalsIgnoreCase("build") || args[0].equalsIgnoreCase("dig")) && args[1].equalsIgnoreCase("mode"))
            return partial(args[2], List.of("whitelist","blacklist"));

        if (args.length == 3 && (args[0].equalsIgnoreCase("build") || args[0].equalsIgnoreCase("dig")) &&
                (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            String t = args[2];

            // Category suggestions
            List<String> out = new ArrayList<>();
            out.addAll(HungerGamesPlugin.rules().knownCategories().stream()
                    .map(c -> "category:" + c)
                    .filter(s -> s.toLowerCase().startsWith(t.toLowerCase()))
                    .toList());
            out.addAll(HungerGamesPlugin.rules().knownCategories().stream()
                    .map(c -> "cat:" + c)
                    .filter(s -> s.toLowerCase().startsWith(t.toLowerCase()))
                    .toList());

            // Material name suggestions (up to 40)
            String upper = t.toUpperCase();
            int cap = 40;
            for (Material m : Material.values()) {
                String n = m.name();
                if (!m.isBlock()) continue;
                if (n.startsWith(upper)) { out.add(n); if (--cap <= 0) break; }
            }
            return out;
        }

        if (args.length == 4 && (args[0].equalsIgnoreCase("massaddchests") || args[0].equalsIgnoreCase("addmanychests"))) { //Add more aliases here if you need em
            return partial(args[3], List.of("T1", "T2", "T3", "T4")); //If you ever decide to make ReplenishManager's tierlist (as found in startScheduleInternal()) public, replace this so it references that instead.
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("massaddchests") || args[0].equalsIgnoreCase("addmanychests"))) { //Add more aliases here if you need em
            String t = args[2];
            // Copying what you did above with material suggestions
            List<String> out = new ArrayList<>();
            String upper = t.toUpperCase();
            int cap = 40;
            for (Material m : Material.values()) {
                String n = m.name();
                if (!m.isBlock()) continue;
                if (n.startsWith(upper)) { out.add(n); if (--cap <= 0) break; }
            }
            return out;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("massaddchests") || args[0].equalsIgnoreCase("addmanychests"))) { //Add more aliases here if you need em
            return partial(args[1], List.of("1024")); //Feel free to add as many suggested values here as you want.
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) return partial(args[1], List.of("teamsize","countdown","penaltyheight","titlecolor"));
        if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("titlecolor")) return partial(args[2], List.of("GOLD","RED","GREEN","BLUE","AQUA","YELLOW","LIGHT_PURPLE","DARK_RED","DARK_BLUE","WHITE","GRAY"));
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) return partial(args[1], List.of("deathmsgs","cannon","defaultteams","launchonmove"));
        if (args.length == 2 && (args[0].equalsIgnoreCase("hostilemobspawn") || args[0].equalsIgnoreCase("hostilemobspawns")
                || args[0].equalsIgnoreCase("passivemobspawn") || args[0].equalsIgnoreCase("passivemobspawns")))
            return partial(args[1], List.of("true","false","t","f"));
        if (args.length == 2 && args[0].equalsIgnoreCase("joingame")) return Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) return Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("border")) return partial(args[1], List.of("set"));
        if (args.length == 2 && args[0].equalsIgnoreCase("addspawnring")) return partial(args[1], List.of("<count>"));
        if (args.length == 2 && args[0].equalsIgnoreCase("clearspawn")) {
            return partial(args[1], List.of("closest", "all"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addspawnring")) return partial(args[2], List.of("<radius>"));
        if (args.length == 4 && args[0].equalsIgnoreCase("addspawnring")) return partial(args[3], List.of("here","spawn"));
        if (args.length == 2 && args[0].equalsIgnoreCase("showpads")) return partial(args[1], List.of("5","10","15","30","60"));
        if (args.length >= 2 && args[0].equalsIgnoreCase("envoy")) {
                if (args.length == 2) return partial(args[1], List.of("start","stop","addloc","clearlocs","listlocs","wave","gui"));
            return List.of();
        }
        return List.of();
    }

    private List<String> partial(String arg, List<String> options) {
        return options.stream().filter(s -> s.toLowerCase().startsWith(arg.toLowerCase())).collect(Collectors.toList());
    }
}
