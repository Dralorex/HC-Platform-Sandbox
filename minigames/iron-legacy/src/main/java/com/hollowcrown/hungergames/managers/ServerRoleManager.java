package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;

import java.util.Locale;

/** Role-aware runtime behavior for standalone/hub/game/edit nodes. */
public class ServerRoleManager {
    public enum Role { STANDALONE, HUB, GAME, EDIT }

    private final HungerGamesPlugin plugin;
    private Role role = Role.STANDALONE;
    private boolean velocityEnabled = false;
    private String serverId = "local";

    public ServerRoleManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String raw = plugin.files().gameflow().getString("server.role", "standalone");
        role = parseRole(raw);
        velocityEnabled = plugin.files().gameflow().getBoolean("velocity.enabled", false);
        serverId = plugin.files().gameflow().getString("velocity.serverId", "local");
    }

    public Role role() { return role; }
    public boolean velocityEnabled() { return velocityEnabled; }
    public String serverId() { return serverId; }

    public boolean isHubLike() {
        return role == Role.HUB || role == Role.STANDALONE;
    }

    public boolean canRunMatch() {
        return role == Role.GAME || role == Role.STANDALONE;
    }

    public boolean canEditTemplates() {
        return role == Role.EDIT || role == Role.STANDALONE;
    }

    private Role parseRole(String raw) {
        if (raw == null) return Role.STANDALONE;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "hub" -> Role.HUB;
            case "game" -> Role.GAME;
            case "edit" -> Role.EDIT;
            default -> Role.STANDALONE;
        };
    }
}

