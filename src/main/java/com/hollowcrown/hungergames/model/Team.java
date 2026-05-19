package com.hollowcrown.hungergames.model;

import org.bukkit.ChatColor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Team {
    private final String name;
    private final ChatColor color;
    private final UUID owner;
    /** Optional 0xRRGGBB for tab list / display; scoreboard still uses nearest {@link #color}. */
    private final Integer displayNameRgb;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Team(String name, ChatColor color, UUID owner) {
        this(name, color, owner, null);
    }

    public Team(String name, ChatColor color, UUID owner, Integer displayNameRgb) {
        this.name = name;
        this.color = color;
        this.owner = owner;
        this.displayNameRgb = displayNameRgb;
        this.members.add(owner);
    }

    public String getName() { return name; }
    public ChatColor getColor() { return color; }
    /** @return 24-bit RGB or null to derive color from {@link #getColor()} only */
    public Integer getDisplayNameRgb() { return displayNameRgb; }
    public UUID getOwner() { return owner; }
    public Set<UUID> getMembers() { return members; }
}
