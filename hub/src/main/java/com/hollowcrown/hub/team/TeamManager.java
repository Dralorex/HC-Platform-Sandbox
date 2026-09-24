package com.hollowcrown.hub.team;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.hydraulicicecube.fakeTeamAPI.teams.FakeTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

//to do removed because I think IronLegacyLobbyBridgeImpl handles that already
//TO/DO: change team verification to allow for non-full teams and auto filling

/**
 * General Info in no particular order:
 * <p>
 * Team names can include caps, but in order to prevent confusion, caps will be treated as identical to lowercase when renaming.
 * This means that renaming a team to "BEST TEAM EVER" will fail if a team exists named "Best Team Ever" or some other variant.
 * <p>
 * I've also tried to weave this version into the bones of the old version as best as I can. I have literally no idea if it works though.
 */
public class TeamManager {
    //TODO: hook these into a config
    //public static int playersPerTeam = 2;
    //public static int maxAllies = 2;
    //public static int maxRivals = 2;
    public static int inviteSeconds = 120;

    //Color
    //Current ranges allow up to 756 semi-distinguishable color choices.
    /*public static float hueTolerance = 20f;
    public static float saturationTolerance = 0.1f;
    public static float valueTolerance = 0.1f;
    public static float minimumSaturation = 0.4f;
    public static float minimumValue = 0.3f;
    public static float hueSearchRange = 20f;
    public static float hueSearchStep = 10f;
    public static float saturationSearchRange = 0.1f;
    public static float saturationSearchStep = 0.05f;
    public static float valueSearchRange = 0.1f;
    public static float valueSearchStep = 0.05f;*/

    //Alternate Color system based on a predefined list of colors
    public static Color[] colorSwatch;
    //red, orange, yellow, green, teal, blue, purple, magenta, rose
    public static float[] hues = new float[] {0f, 30f, 60f, 125f, 175f, 240f, 275f, 300f, 330f};
    public static float highValue = 1f;
    public static float midValue = 0.8f;
    public static float lowValue = 0.6f;
    public static float highSaturation = 0.9f;
    public static float lowSaturation = 0.4f;

    public static String[] colorNames = new String[]{
            "Bright Red", "Bright Orange", "Bright Yellow", "Bright Green", "Bright Teal", "Bright Blue", "Bright Purple", "Bright Magenta", "Bright Rose",
            "Red", "Orange", "Yellow", "Green", "Teal", "Blue", "Purple", "Magenta", "Rose",
            "Dark Red", "Dark Orange", "Dark Yellow", "Dark Green", "Dark Teal", "Dark Blue", "Dark Purple", "Dark Magenta", "Dark Rose",
            "Pastel Bright Red", "Pastel Bright Orange", "Pastel Bright Yellow", "Pastel Bright Green", "Pastel Bright Teal", "Pastel Bright Blue", "Pastel Bright Purple", "Pastel Bright Magenta", "Pastel Bright Rose",
            "Pastel Red", "Pastel Orange", "Pastel Yellow", "Pastel Green", "Pastel Teal", "Pastel Blue", "Pastel Purple", "Pastel Magenta", "Pastel Rose",
            "Pastel Dark Red", "Pastel Dark Orange", "Pastel Dark Yellow", "Pastel Dark Green", "Pastel Dark Teal", "Pastel Dark Blue", "Pastel Dark Purple", "Pastel Dark Magenta", "Pastel Dark Rose"
    };

    static {
        colorSwatch = new Color[54]; //54 slots in a large chest inventory
        for(int i = 0; i < 9; i++){
            float hue = hues[i];
            colorSwatch[i] = hsvToRgb(new Vector3f(hue, highSaturation, highValue));
            colorSwatch[i + 9] = hsvToRgb(new Vector3f(hue, highSaturation, midValue));
            colorSwatch[i + 18] = hsvToRgb(new Vector3f(hue, highSaturation, lowValue));
            colorSwatch[i + 27] = hsvToRgb(new Vector3f(hue, lowSaturation, highValue));
            colorSwatch[i + 36] = hsvToRgb(new Vector3f(hue, lowSaturation, midValue));
            colorSwatch[i + 45] = hsvToRgb(new Vector3f(hue, lowSaturation, lowValue));
        }
    }

    //public static HollowCrownHubPlugin plugin;
    

    //public HashMap<String, Team> teams;
    public static HashMap<Object, TeamManagerInstance> instances = new HashMap<>();

    public static ArrayList<AllianceInvite> allianceInvites = new ArrayList<>();
    public static ArrayList<TeamJoinInvite> teamJoinInvites = new ArrayList<>();

    public static class TeamManagerInstance {
        private final Object identifier;
        public HashMap<String, Team> teams;
        public ArrayList<Color> takenColors = new ArrayList<>();
        //removed because I actually don't have commands to change teams stuff, you can only do that through the gui
        //TO/DO: Implement the following booleans
        //public boolean allowPartialTeams = false;
        public boolean allowTeamCustomization = true; //Whether to allow changing team name, color, players, etc. Useful for disallowing changes mid-game.
        public boolean allowAlliances = true; //Whether to allow alliances. Useful for disbanding alliances then disallowing new ones.
        public boolean friendlyFire = false; //Allow teammates to attack each-other
        public boolean allyFriendlyFire = true; //Allow allies to attack each-other
        public int maxPlayersPerTeam = 2;
        public int maxAllies = 2;
        public int maxRivals = 2;

        public TeamManagerInstance(Object identifier) {
            this.identifier = identifier;
            this.teams = new HashMap<>();
        }

        public Object getIdentifier() {
            return identifier;
        }

        public Team findPlayerTeam(Player player){
            UUID uuid = player.getUniqueId();
            for(Team team : teams.values())
                if(team.members.contains(uuid))
                    return team;
            return null;
        }

        public Team findPlayerTeam(UUID uuid){
            for(Team team : teams.values())
                if(team.members.contains(uuid))
                    return team;
            return null;
        }

        public void clearAllTeams(){
            Server server = Bukkit.getServer();
            for(Team team : teams.values()) {
                List<Player> players = new ArrayList<>();
                for (UUID member : team.members){
                    Player player = server.getPlayer(member);
                    if(player != null) {
                        player.sendMessage(Component.text("Your Team has been disbanded.", NamedTextColor.GRAY));
                        players.add(player);
                    }
                }
                FakeTeam fakeTeam = new FakeTeam(team.name)
                        .players(players);
                for(Player player : Bukkit.getOnlinePlayers()){
                    fakeTeam.sendRemovalTo(player);
                }
            }
            teams.clear();
        }

        public Color getRandomUnusedColor(){
            return Arrays.stream(TeamManager.colorSwatch)
                    .filter((e) -> !takenColors.contains(e))
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(ArrayList::new),
                            subList -> {
                                Collections.shuffle(subList);
                                return subList.stream().findFirst().orElse(Color.WHITE);
                            }
                    ));
        }

        public boolean sendAllFakeTeamPackets(){
            return TeamManager.sendAllFakeTeamPackets(this);
        }

        public boolean disbandAllAlliances(boolean allowFutureAlliances){
            return TeamManager.disbandAllAlliances(this, allowFutureAlliances);
        }

        public boolean verifyTeamIntegrity(){
            return TeamManager.verifyTeamIntegrity(this);
        }
    }

    public static class TeamJoinInvite{
        public long timestamp;
        public Team offeringTeam;
        public UUID receivingPlayer;

        public TeamJoinInvite(Team offeringTeam, UUID receivingPlayer){
            this.offeringTeam = offeringTeam;
            this.receivingPlayer = receivingPlayer;
            this.timestamp = Instant.now().getEpochSecond();
        }
    }

    public static class AllianceInvite{
        public long timestamp;
        public Team offeringTeam;
        public Team receivingTeam;

        public AllianceInvite(Team offeringTeam, Team receivingTeam){
            this.offeringTeam = offeringTeam;
            this.receivingTeam = receivingTeam;
            this.timestamp = Instant.now().getEpochSecond();
        }
    }

    public static class Team {
        public long timestamp;
        private String name;
        public Color color;
        TeamManagerInstance teamManagerInstance;
        public ArrayList<UUID> members = new ArrayList<>();
        public ArrayList<Team> allies = new ArrayList<>();
        public ArrayList<Team> rivals = new ArrayList<>();

        public Team(String name, TeamManagerInstance teamManagerInstance){
            this.name = name;
            this.teamManagerInstance = teamManagerInstance;
            color = teamManagerInstance.getRandomUnusedColor();
            teamManagerInstance.takenColors.add(color);
            teamManagerInstance.teams.put(name.toLowerCase(Locale.ROOT), this);
            Instant now = Instant.now();
            timestamp = now.getNano() + (now.getEpochSecond() * 1000000000);
        }

        public Team(String name, Color color, TeamManagerInstance teamManagerInstance){
            this.name = name;
            this.color = color;
            this.teamManagerInstance = teamManagerInstance;
            teamManagerInstance.takenColors.add(color);
            teamManagerInstance.teams.put(name.toLowerCase(Locale.ROOT), this);
            Instant now = Instant.now();
            timestamp = now.getNano() + (now.getEpochSecond() * 1000000000);
        }

        /**
         * Set the members of a team.
         * @param members The members to set. This collection must have exactly {@link TeamManagerInstance#maxPlayersPerTeam} players.
         * @return True if the members were set, or False if it would make the team invalid.
         */
        public boolean setMembers(Collection<Player> members){
            if(members == null || members.size() != teamManagerInstance.maxPlayersPerTeam)
                return false;
            this.members.clear();
            members.forEach((player -> this.members.add(player.getUniqueId())));
            return true;
        }

        public UUID getLeader(){
            return members == null || members.isEmpty() ? null : members.getFirst();
        }

        /**
         * Rename this team.
         * @param newName The new name of the team.
         * @return true if the team was renamed successfully, or false if the new name was already taken.
         */
        public boolean rename(String newName){
            if(!teamManagerInstance.allowTeamCustomization)
                return false;
            if(!teamManagerInstance.teams.containsValue(this)) //In the strange case that a team doesn't exist in its manger, nothing should happen.
                return false;
            if(teamManagerInstance.teams.containsKey(newName.toLowerCase()))
                return false;
            teamManagerInstance.teams.remove(name.toLowerCase());
            teamManagerInstance.teams.put(newName.toLowerCase(), this);
            name = newName;
            return true;
        }

        public String getName(){
            return name;
        }

        public boolean setColor(Color color){
            if(!teamManagerInstance.allowTeamCustomization)
                return false;
            if(!teamManagerInstance.teams.containsValue(this)) //In the strange case that a team doesn't exist in its manger, nothing should happen.
                return false;
            teamManagerInstance.takenColors.remove(this.color);
            teamManagerInstance.takenColors.add(color);
            this.color = color;
            return true;
        }

        public TeamManagerInstance getTeamManagerInstance(){
            return teamManagerInstance;
        }

        /*public boolean setColor(String colorCode){
            Color color;
            if(!colorCode.startsWith("0x") && !colorCode.startsWith("#"))
                colorCode = "#" + colorCode;
            try {
                java.awt.Color tempColor = java.awt.Color.decode(colorCode);
                color = Color.fromRGB(tempColor.getRed(), tempColor.getGreen(), tempColor.getBlue());
            }
            catch (Exception exception){
                return false;
            }

            //At this point, we have a valid color.
            Vector3f hsv = rgbToHsv(color);
            Vector3f[] otherHSV = new Vector3f[teamManagerInstance.teams.size() - 1];
            int i = 0;
            for(Team team : teamManagerInstance.teams.values()){
                if(team == this)
                    continue;
                otherHSV[i] = rgbToHsv(team.color);
                i++;
            }
            if(colorSpaceSampleValid(hsv, otherHSV)){
                //Perfect! This spot is empty and not too close to any other team's color choice.
                teamManagerInstance.takenColors.remove(this.color);
                teamManagerInstance.takenColors.add(color);
                this.color = color;
                return true;
            }
            else{
                //Look around a little to see if there's a nearby color that would work
                ArrayList<Vector3f> options = new ArrayList<>();
                for(float hueDifference = -hueSearchRange; hueDifference <= hueSearchRange + 0.001f; hueDifference += hueSearchStep)
                    for(float saturationDifference = -saturationSearchRange; saturationDifference <= saturationSearchRange + 0.001f; saturationDifference += saturationSearchStep)
                        for(float valueDifference = -valueSearchRange; valueDifference <= valueSearchRange + 0.001f; valueDifference += valueSearchStep)
                            if(colorSpaceSampleValid(new Vector3f(hsv.x + hueDifference, hsv.y + saturationDifference, hsv.z + valueDifference), otherHSV))
                                options.add(new Vector3f(hsv.x + hueDifference, hsv.y + saturationDifference, hsv.z + valueDifference));
                if(options.isEmpty())
                    return false; //Found nothing :(
                Vector3f winner = options.stream().min((e1, e2) -> Float.compare(e1.distanceSquared(hsv), e2.distanceSquared(hsv))).get();
                Color winningColor = hsvToRgb(winner);
                teamManagerInstance.takenColors.remove(this.color);
                teamManagerInstance.takenColors.add(winningColor);
                this.color = winningColor;
                return true;
            }
        }*/

        /**
         * Allies this team to another.
         * @param other The team to form an alliance with.
         * @return True if the teams now have an alliance, False if they don't. This method returns true if the teams are already allied.
         */
        public boolean addAlly(Team other){
            if(allies.size() >= teamManagerInstance.maxAllies || other.allies.size() >= teamManagerInstance.maxAllies)
                return false;
            if(allies.contains(other) || other.allies.contains(this))
                return true;
            rivals.remove(other);
            other.rivals.remove(this);
            allies.add(other);
            other.allies.add(this);
            //Remove any invites
            allianceInvites.removeIf((e) -> (e.offeringTeam == other && e.receivingTeam == this) || (e.offeringTeam == this && e.receivingTeam == other));
            for(UUID uuid : members) {
                Player player = Bukkit.getServer().getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(Component.text()
                            .content("")
                            .append(Component.text(other.name, TextColor.color(other.color.asRGB())))
                            .append(Component.text(" has joined your alliance."))
                    );
                }
            }
            for(UUID uuid : other.members) {
                Player player = Bukkit.getServer().getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(Component.text()
                            .content("")
                            .append(Component.text(name, TextColor.color(color.asRGB())))
                            .append(Component.text(" has joined in alliance with you."))
                    );
                }
            }
            TeamManager.sendFakeTeamPackets(this);
            TeamManager.sendFakeTeamPackets(other);
            return true;
        }

        public void removeAlly(Team other){
            if(allies.contains(other)){
                allies.remove(other);
                other.allies.remove(this);
                for(UUID uuid : members) {
                    Player player = Bukkit.getServer().getPlayer(uuid);
                    if (player != null) {
                        player.sendMessage(Component.text()
                                .content("You've broken your alliance with Team ")
                                .append(Component.text(other.name, TextColor.color(other.color.asRGB())))
                        );
                    }
                }
                for(UUID uuid : other.members) {
                    Player player = Bukkit.getServer().getPlayer(uuid);
                    if (player != null) {
                        player.sendMessage(Component.text()
                                .content("")
                                .append(Component.text(name, TextColor.color(color.asRGB())))
                                .append(Component.text(" has broken their alliance with you."))
                        );
                    }
                }
                TeamManager.sendFakeTeamPackets(this);
                TeamManager.sendFakeTeamPackets(other);
            }
        }

        public void removeAllAllies(){
            for(Team other : allies){
                removeAlly(other);
            }
        }

        public void addRival(Team rival){
            if(!rivals.contains(rival)){
                rivals.add(rival);
                removeAlly(rival);
                for(UUID uuid : rival.members) {
                    Player player = Bukkit.getServer().getPlayer(uuid);
                    if (player != null) {
                        if (!rival.rivals.contains(this) && uuid == rival.getLeader())
                            player.sendMessage(Component.text()
                                    .content("")
                                    .append(Component.text(name, TextColor.color(color.asRGB())))
                                    .append(Component.text(" has deemed you an enemy. "))
                                    .append(Component.text("[Return the favor?]", TextColor.color(85, 255, 85)).clickEvent(ClickEvent.runCommand("/rival add " + name.toLowerCase())).hoverEvent(Component.text("Declare Team " ).append(Component.text(name, TextColor.color(color.asRGB()))).append(Component.text(" a rival!")).asHoverEvent())) //Green!
                            );
                        else
                            player.sendMessage(Component.text()
                                    .content("")
                                    .append(Component.text(name, TextColor.color(color.asRGB())))
                                    .append(Component.text(" has deemed you an enemy. "))
                            );
                    }
                }
                TeamManager.sendFakeTeamPackets(this);
                TeamManager.sendFakeTeamPackets(rival);
            }
        }

        public void removeRival(Team rival) {
            if (rivals.contains(rival)) {
                rivals.remove(rival);
                for (UUID uuid : rival.members) {
                    Player player = Bukkit.getServer().getPlayer(uuid);
                    if (player != null) {
                        if (!rival.rivals.contains(this) && uuid == rival.getLeader())
                            player.sendMessage(Component.text()
                                    .content("")
                                    .append(Component.text(name, TextColor.color(color.asRGB())))
                                    .append(Component.text(" has decided you aren't a threat. "))
                                    .append(Component.text("[Return the favor?]", TextColor.color(85, 255, 85)).clickEvent(ClickEvent.runCommand("/rival remove " + name.toLowerCase())).hoverEvent(Component.text("Dismiss Team ").append(Component.text(name, TextColor.color(color.asRGB()))).asHoverEvent())) //Green!
                            );
                        else
                            player.sendMessage(Component.text()
                                    .content("")
                                    .append(Component.text(name, TextColor.color(color.asRGB())))
                                    .append(Component.text(" has decided you aren't a threat. "))
                            );
                    }
                }
                TeamManager.sendFakeTeamPackets(this);
                TeamManager.sendFakeTeamPackets(rival);
            }
        }

        public void sendFakeTeamPackets(){
            TeamManager.sendFakeTeamPackets(this);
        }
    }

    /**
     * Create a new team manager instance and add it to {@link #instances}. This is useful for creating lobbies or games. Because almost anything can be used as an identifier, It does not need to be world-specific.
     * @param referenceObject The reference object that identifies the manager instance. This could be a world, plugin reference, or some other persistent object you keep a reference to. A {@link TeamManagerInstance} is not a valid reference and will throw {@link IllegalArgumentException} if passed in.
     * @return The newly created manager instance.
     */
    public static TeamManagerInstance newInstance(Object referenceObject){
        if(referenceObject instanceof TeamManagerInstance)
            throw new IllegalArgumentException("A TeamManagerInstance cannot be used as an identifier!");
        TeamManagerInstance instance = new TeamManagerInstance(referenceObject);
        instances.put(referenceObject, instance);
        return instance;
    }

    /**
     * Remove a team manager instance from {@link #instances}.
     * @param referenceObject The reference object that identifies the manager instance. This could be a world, plugin reference, or some other persistent object you keep a reference to. A {@link TeamManagerInstance} is not a valid reference and will throw {@link IllegalArgumentException} if passed in.
     * @return The removed instance, or null if it cannot be found.
     */
    public static TeamManagerInstance destroyInstance(Object referenceObject){
        if(referenceObject instanceof TeamManagerInstance)
            throw new IllegalArgumentException("A TeamManagerInstance cannot be used as an identifier!");
        return instances.remove(referenceObject);
    }

    /**
     * Get a team manager instance from its identifier.
     * @param referenceObject The reference object that identifies the manager instance. This could be a world, plugin reference, or some other persistent object you keep a reference to. A {@link TeamManagerInstance} is not a valid reference and will throw {@link IllegalArgumentException} if passed in.
     * @return The manager instance, or null if it cannot be found.
     */
    public static TeamManagerInstance getInstance(Object referenceObject){
        if(referenceObject instanceof TeamManagerInstance)
            throw new IllegalArgumentException("A TeamManagerInstance cannot be used as an identifier!");
        return instances.getOrDefault(referenceObject, null);
    }

    public static @NotNull TeamManagerInstance getOrCreateWorldInstance(World world){
        if(instances.containsKey(world))
            return instances.get(world);
        else
            return newInstance(world);
    }

    /*/**
     * Add a team to a manager instance.
     * @param reference A manager instance or the reference object that identifies the manager instance. This could be a world, plugin reference, or some other persistent object you keep a reference to. A {@link TeamManagerInstance} is not a valid reference and will be directly used as the instance.
     * @param name The name of the team. Teams can be renamed later.
     * @return The newly created team, or null if the manager instance could not be found or the name is already taken.
     */
    /*public static Team addTeam(Object reference, String name){
        TeamManagerInstance instance = reference instanceof TeamManagerInstance teamManagerInstance ? teamManagerInstance : instances.get(reference);
        if(instance == null || instance.teams.containsKey(name.toLowerCase(Locale.ROOT)))
            return null;
        Team team = new Team(name, instance);
        instance.teams.put(name.toLowerCase(Locale.ROOT), team);
        return team;
    }*/

    /**
     * Searches through all active instances and finds the first team with this player. Use this method minimally, as it is costly.
     * @param player The player to look for.
     * @return A team containing the player, or null if it cannot be found.
     */
    public static Team findPlayerTeam(Player player){
        UUID uuid = player.getUniqueId();
        for(TeamManagerInstance instance : instances.values())
            for(Team team : instance.teams.values())
                if (team.members.contains(uuid))
                    return team;
        return null;
    }

    public static Team findPlayerTeam(UUID uuid){
        for(TeamManagerInstance instance : instances.values())
            for(Team team : instance.teams.values())
                if (team.members.contains(uuid))
                    return team;
        return null;
    }

    /**
     * Run logic for an alliance add command.
     * @param player The player running the command.
     * @param desiredTeam The team name of the team the player wants to ally with.
     * @return True if the command ran successfully, false if it failed in some way.
     */
    public static boolean allianceAddCommand(Player player, String desiredTeam){
        Team team = findPlayerTeam(player);
        TeamManagerInstance teamManagerInstance = getInstance(player.getWorld());
        //if(teamManagerInstance != null)
        //    HollowCrownHubPlugin.getPlugin(HollowCrownHubPlugin.class).getLogger().info("passed arg: " + desiredTeam + ", instance: " + teamManagerInstance + ", ref: " + teamManagerInstance.getIdentifier() + ", teams: " + teamManagerInstance.teams);
        //else
        //    HollowCrownHubPlugin.getPlugin(HollowCrownHubPlugin.class).getLogger().info("passed arg: " + desiredTeam + ", instance is null, all instances: " + TeamManager.instances);
        if(team == null) {
            player.sendMessage(Component.text("You are not on a team!", TextColor.color(255, 85, 85)));
            return false;
        }
        Team other = team.teamManagerInstance.teams.getOrDefault(desiredTeam.toLowerCase(), null);
        if(other == null) {
            player.sendMessage(Component.text(desiredTeam + " could not be found!", TextColor.color(255, 85, 85)));
            return false;
        }
        if(checkAllianceInvite(other, team)){
            //This team is accepting an invite.
            //addAlly() sends messages to all players on both teams if it's successful, so no need to do that here.
            boolean success = other.addAlly(team); //Because this team (team) is accepting an alliance invite from another team (other), the other team (other) "owns" the alliance. In actuality, all this means is that the chat notification recognizes that the other team took the first step.
            if(!success){
                if(team.allies.contains(other))
                    player.sendMessage(Component.text("You already have an alliance with Team " + other.name + '!', TextColor.color(255, 85, 85)));
                else if(team.allies.size() >= teamManagerInstance.maxAllies)
                    player.sendMessage(Component.text("You cannot have more than " + teamManagerInstance.maxAllies + (teamManagerInstance.maxAllies == 1 ? " alliance!" : " alliances!"), TextColor.color(255, 85, 85)));
                else if(other.allies.size() >= teamManagerInstance.maxAllies)
                    player.sendMessage(Component.text(other.name + " has already reached the maximum of " + teamManagerInstance.maxAllies + (teamManagerInstance.maxAllies == 1 ? " alliance!" : " alliances!"), TextColor.color(255, 85, 85)));
            }
            return success;
        }
        else {
            //This team is sending an invite.
            allianceInvites.add(new AllianceInvite(team, other));
            player.sendMessage(Component.text()
                    .content("You have offered an alliance to ")
                    .append(Component.text(other.name, TextColor.color(other.color.asRGB())))
                    .append(Component.text("."))
            );
            Player otherLeader = Bukkit.getServer().getPlayer(other.getLeader());
            if (otherLeader != null) {
                otherLeader.sendMessage(Component.text()
                        .content("")
                        .append(Component.text(team.name, TextColor.color(team.color.asRGB())))
                        .append(Component.text(" has offered an alliance. "))
                        .append(Component.text("[Accept?]", TextColor.color(85, 255, 85)).clickEvent(ClickEvent.runCommand("/ally add " + team.name.toLowerCase(Locale.ROOT))).hoverEvent(Component.text("Form alliance with ").append(Component.text(team.name, TextColor.color(team.color.asRGB()))).asHoverEvent())) //Green!
                );
            }
            return true;
        }
    }

    public static boolean allianceRemoveCommand(Player player, String desiredTeam){
        Team team = findPlayerTeam(player);
        if(team == null) {
            player.sendMessage(Component.text("You are not on a team!", TextColor.color(255, 85, 85)));
            return false;
        }
        Team other = team.teamManagerInstance.teams.getOrDefault(desiredTeam.toLowerCase(), null);
        if(other == null) {
            player.sendMessage(Component.text(desiredTeam + " could not be found!", TextColor.color(255, 85, 85)));
            return false;
        }
        if(!team.allies.contains(other)) {
            player.sendMessage(Component.text("You are not allied with " + desiredTeam + "!", TextColor.color(255, 85, 85)));
            return false;
        }
        team.removeAlly(other);
        return true;
    }

    public static boolean rivalAddCommand(Player player, String desiredTeam){
        Team team = findPlayerTeam(player);
        if(team == null) {
            player.sendMessage(Component.text("You are not on a team!", TextColor.color(255, 85, 85)));
            return false;
        }
        Team other = team.teamManagerInstance.teams.getOrDefault(desiredTeam.toLowerCase(), null);
        if(other == null) {
            player.sendMessage(Component.text(desiredTeam + " could not be found!", TextColor.color(255, 85, 85)));
            return false;
        }
        if(team.rivals.contains(other)) {
            player.sendMessage(Component.text("You have already deemed " + desiredTeam + " a rival!", TextColor.color(255, 85, 85)));
            return false;
        }
        team.addRival(other);
        return true;
    }

    public static boolean rivalRemoveCommand(Player player, String desiredTeam){
        Team team = findPlayerTeam(player);
        if(team == null) {
            player.sendMessage(Component.text("You are not on a team!", TextColor.color(255, 85, 85)));
            return false;
        }
        Team other = team.teamManagerInstance.teams.getOrDefault(desiredTeam.toLowerCase(), null);
        if(other == null) {
            player.sendMessage(Component.text(desiredTeam + " could not be found!", TextColor.color(255, 85, 85)));
            return false;
        }
        if(!team.rivals.contains(other)) {
            player.sendMessage(Component.text(desiredTeam + " is not a rival!", TextColor.color(255, 85, 85)));
            return false;
        }
        team.removeRival(other);
        return true;
    }

    /**
     * Check if a valid alliance invite exists that matches offering and receiving.
     * @param offering The team that offered the invite.
     * @param receiving The team that received the invite.
     * @return true if a valid invite exists, false otherwise.
     */
    public static boolean checkAllianceInvite(Team offering, Team receiving){
        //Remove any invites older than a minute (or whatever inviteSeconds is)
        long timestamp = Instant.now().getEpochSecond();
        allianceInvites.removeIf((e) -> e.timestamp + inviteSeconds < timestamp);
        //See if any remaining invites match the request
        for(AllianceInvite invite : allianceInvites){
            if(invite.offeringTeam == offering && invite.receivingTeam == receiving)
                return true;
        }
        return false;
    }

    public static ArrayList<TeamJoinInvite> getTeamJoinInvites(UUID receiving){
        //return teamJoinInvites.stream().filter((e) -> e.receivingPlayer == receiving).toArray();
        ArrayList<TeamJoinInvite> temp = new ArrayList<>();
        for(TeamJoinInvite teamJoinInvite : teamJoinInvites){
            if(teamJoinInvite.receivingPlayer == receiving)
                temp.add(teamJoinInvite);
        }
        return temp;
    }

    public static boolean checkTeamJoinInvite(Team offering, UUID receiving){
        //Remove any invites older than a minute (or whatever inviteSeconds is)
        long timestamp = Instant.now().getEpochSecond();
        teamJoinInvites.removeIf((e) -> e.timestamp + inviteSeconds < timestamp);
        //See if any remaining invites match the request
        for(TeamJoinInvite invite : teamJoinInvites){
            if(invite.offeringTeam == offering && invite.receivingPlayer == receiving)
                return true;
        }
        return false;
    }

    /**
     * Verifies the integrity of the teams managed by a specific instance.
     * @param reference A manager instance or the reference object that identifies the manager instance. This could be a world, plugin reference, or some other persistent object you keep a reference to. A {@link TeamManagerInstance} is not a valid reference and will be directly used as the instance.
     * @return true if the verification ran, false if the instance could not be found.
     * @see TeamManagerInstance
     */
    public static boolean verifyTeamIntegrity(Object reference){
        //Far too many loops for its own good
        //Remove duplicate UUIDs in teams, preferring newer teams. (Nanosecond timestamp)
        TeamManagerInstance instance = reference instanceof TeamManagerInstance teamManagerInstance ? teamManagerInstance : instances.get(reference);
        if(instance == null)
            return false;
        for(Team team : instance.teams.values()){
            for(UUID member : team.members){
                Team best = team;
                for(Team other : instance.teams.values()){
                    if(other == best)
                        continue;
                    if(other.members.contains(member)){
                        if(other.timestamp <= best.timestamp)
                            other.members.remove(member);
                        else{
                            best.members.remove(member);
                            best = other;
                        }
                    }
                }
            }
        }
        //Remove empty teams
        instance.teams.entrySet().removeIf(e -> e.getValue().members.isEmpty());

        return true;
    }

    //Removed because
    //TO/DO: Implement autofill
    /*public static boolean runAutoFill(Object reference){
        TeamManagerInstance instance = reference instanceof TeamManagerInstance teamManagerInstance ? teamManagerInstance : instances.get(reference);
        if(instance == null)
            return false;
        //instance can be used at this point
        return true;
    }*/

    /**
     * Disband all alliances.
     * @param reference A manager instance or the reference object that identifies the manager instance. This could be a world, plugin reference, or some other persistent object you keep a reference to. A {@link TeamManagerInstance} is not a valid reference and will be directly used as the instance.
     * @param disallowFutureAlliances disallow the creation of new alliances. This is useful for disbanding alliances near the end of a tournament when you don't want any more alliances.
     * @return true if the manager instance or reference is valid, false if it isn't.
     */
    public static boolean disbandAllAlliances(Object reference, boolean disallowFutureAlliances){
        TeamManagerInstance instance = reference instanceof TeamManagerInstance teamManagerInstance ? teamManagerInstance : instances.get(reference);
        if(instance == null)
            return false;
        for(Team team : instance.teams.values()){
            team.allies.clear();
        }
        for(Player player : Bukkit.getServer().getOnlinePlayers())
            player.sendMessage(Component.text("All alliances have been disbanded!", TextColor.color(255, 85, 85)));
        if(disallowFutureAlliances)
            instance.allowAlliances = false;
        return true;
    }

    /**
     * Send out fake team packets for a specific team.
     * @param team The team to send out.
     * @return true if the team and manager are valid, false if they aren't.
     * @see TeamManagerInstance
     */
    public static boolean sendFakeTeamPackets(Team team){
        Server server = Bukkit.getServer();
        ArrayList<Player> players = new ArrayList<>(team.members.size());
        for(UUID uuid : team.members){
            Player player = server.getPlayer(uuid);
            if(player != null)
                players.add(player);
        }
        //I finally fixed it!
        //Bukkit.getLogger().info("teamManagerInstance: " + team.teamManagerInstance + ", ref: " + team.teamManagerInstance.getIdentifier() + ", loader: " + TeamManager.class.getClassLoader() + " hash: " + System.identityHashCode(TeamManager.instances) + ", all instances: " + instances);
        //Keep track of which players have already been sent a customized team packet
        ArrayList<Player> inTeams = new ArrayList<>(team.getTeamManagerInstance().maxPlayersPerTeam * team.teamManagerInstance.teams.size());
        for(Team otherTeam : team.teamManagerInstance.teams.values()){
            FakeTeam fake = new FakeTeam(team.name)
                    .color(otherTeam == team ? FakeTeam.PlayerNameColor.GREEN : (otherTeam.allies.contains(team) ? FakeTeam.PlayerNameColor.AQUA : (otherTeam.rivals.contains(team) ? FakeTeam.PlayerNameColor.GOLD : FakeTeam.PlayerNameColor.RED)))
                    .stringPrefixColored("[" + team.name + "] ", team.color)
                    .players(players);
            for(UUID uuid : otherTeam.members){
                Player player = server.getPlayer(uuid);
                if(player != null){
                    fake.sendTo(player);
                    inTeams.add(player);
                }
            }
        }
        //Send any non-teamed player (spectators) a packet that declares this team to them without enemy/ally coloration.
        FakeTeam fake = new FakeTeam(team.name)
                .color(FakeTeam.PlayerNameColor.WHITE)
                .stringPrefixColored("[" + team.name + "] ", team.color)
                .players(players);
        for(Player player : server.getOnlinePlayers()){
            if(!inTeams.contains(player))
                fake.sendTo(player);
        }
        return true;
    }

    /**
     * Collects fake team packets for every team in the world and sends them to the specified player.
     * @param player The player to send the data to.
     * @return true if it succeeded, false if it failed.
     */
    public static boolean sendFakeTeamPacketsTo(Player player){
        TeamManagerInstance teamManagerInstance = getInstance(player.getWorld());
        Team team = null;
        if(teamManagerInstance == null) {
            team = TeamManager.findPlayerTeam(player);
            if(team == null)
                return false;
            else{
                teamManagerInstance = team.teamManagerInstance;
            }
        }
        else{
            team = teamManagerInstance.findPlayerTeam(player);
        }

        Server server = Bukkit.getServer();
        if(team == null) {
            for (Team other : teamManagerInstance.teams.values()) {
                ArrayList<Player> teamMembers = new ArrayList<>(other.members.size());
                for(UUID uuid : other.members){
                    Player teamMember = server.getPlayer(uuid);
                    if(teamMember != null)
                        teamMembers.add(teamMember);
                }
                new FakeTeam(other.name)
                        .color(FakeTeam.PlayerNameColor.WHITE)
                        .stringPrefixColored("[" + other.name + "] ", other.color)
                        .players(teamMembers)
                        .sendTo(player);
            }
        }
        else{
            for (Team other : teamManagerInstance.teams.values()) {
                ArrayList<Player> teamMembers = new ArrayList<>(other.members.size());
                for(UUID uuid : other.members){
                    Player teamMember = server.getPlayer(uuid);
                    if(teamMember != null)
                        teamMembers.add(teamMember);
                }
                new FakeTeam(other.name)
                        .color(team == other ? FakeTeam.PlayerNameColor.GREEN : (team.allies.contains(other) ? FakeTeam.PlayerNameColor.AQUA : (team.rivals.contains(other) ? FakeTeam.PlayerNameColor.GOLD : FakeTeam.PlayerNameColor.RED)))
                        .stringPrefixColored("[" + other.name + "] ", other.color)
                        .players(teamMembers)
                        .sendTo(player);
            }
        }
        return true;
    }

    public static boolean sendFakeTeamPacketsTo(Team team) {
        TeamManagerInstance teamManagerInstance = team.teamManagerInstance;
        Server server = Bukkit.getServer();
        for (Team other : teamManagerInstance.teams.values()) {
            ArrayList<Player> teamMembers = new ArrayList<>(other.members.size());
            for (UUID uuid : other.members) {
                Player teamMember = server.getPlayer(uuid);
                if (teamMember != null)
                    teamMembers.add(teamMember);
            }
            FakeTeam fakeTeam = new FakeTeam(other.name)
                    .color(team == other ? FakeTeam.PlayerNameColor.GREEN : (team.allies.contains(other) ? FakeTeam.PlayerNameColor.AQUA : (team.rivals.contains(other) ? FakeTeam.PlayerNameColor.GOLD : FakeTeam.PlayerNameColor.RED)))
                    .stringPrefixColored("[" + other.name + "] ", other.color)
                    .players(teamMembers);
            for(UUID member : team.members){
                Player player = server.getPlayer(member);
                if(player != null)
                    fakeTeam.sendTo(player);
            }

        }
        return true;
    }

    /**
     * Sends out fake team packets for all teams in this manager. Please use the less expensive {@link #sendFakeTeamPackets(Team)} for dynamic modifications such as declaring allies or rivals.
     * @param reference A manager instance or the reference object that identifies the manager instance. This could be a world, plugin reference, or some other persistent object you keep a reference to. A {@link TeamManagerInstance} is not a valid reference and will be directly used as the instance.
     * @return true if the instance could be found, false if it could not
     * @see TeamManagerInstance
     */
    public static boolean sendAllFakeTeamPackets(Object reference){
        TeamManagerInstance instance = reference instanceof TeamManagerInstance teamManagerInstance ? teamManagerInstance : instances.get(reference);
        if(instance == null)
            return false;
        for(Team team : instance.teams.values())
            sendFakeTeamPackets(team);
        return true;
    }

    public static void nukeTeamsForWorld(World world){
        FakeTeam nuke = new FakeTeam("tempTeamToTerrorizeTemporaryTeamTitles")
                .players(world.getPlayers());
        Bukkit.getLogger().info("Nuking teams with team containing " + world.getPlayers());
        for(Player player : Bukkit.getOnlinePlayers()){
            nuke.sendTo(player);
            Bukkit.getLogger().info("Sending nuke to " + player);
        }
        Bukkit.getScheduler().runTaskLater(HollowCrownHubPlugin.get(), () -> {
            for(Player player : Bukkit.getOnlinePlayers()){
                nuke.sendRemovalTo(player);
                Bukkit.getLogger().info("Sending nuke removal to " + player);
            }
        }, 5);
    }

    static Vector3f rgbToHsv(Color color) {
        float rf = color.getRed() / 255f;
        float gf = color.getGreen() / 255f;
        float bf = color.getBlue() / 255f;

        float v = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = v - min;

        float h;

        if (delta == 0) {
            h = 0;
        } else if (v == rf) {
            h = 60f * (((gf - bf) / delta) % 6);
        } else if (v == gf) {
            h = 60f * (((bf - rf) / delta) + 2);
        } else {
            h = 60f * (((rf - gf) / delta) + 4);
        }

        if (h < 0)
            h += 360f;

        float s = v == 0 ? 0 : delta / v;

        return new Vector3f(h, s, v);
    }

    static Color hsvToRgb(Vector3f hsv) {
        float c = hsv.z * hsv.y;
        float x = c * (1 - Math.abs((hsv.x / 60f) % 2 - 1));
        float m = hsv.z - c;

        float r, g, b;

        if (hsv.x < 60) {
            r = c; g = x; b = 0;
        } else if (hsv.x < 120) {
            r = x; g = c; b = 0;
        } else if (hsv.x < 180) {
            r = 0; g = c; b = x;
        } else if (hsv.x < 240) {
            r = 0; g = x; b = c;
        } else if (hsv.x < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        return Color.fromRGB(Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    /*public static boolean colorSpaceSampleValid(Vector3f hsv, Vector3f[] otherHSV){
        for(Vector3f other : otherHSV){
            if(hsv.y < minimumSaturation || hsv.z < minimumValue
                    || ((hsv.x < other.x + hueTolerance || hsv.x > other.x - hueTolerance)
                    && (hsv.y < other.y + saturationTolerance || hsv.y > other.y - saturationTolerance)
                    && (hsv.z < other.z + valueTolerance || hsv.z > other.z - valueTolerance)))
                return false;
        }
        return true;
    }*/
}
