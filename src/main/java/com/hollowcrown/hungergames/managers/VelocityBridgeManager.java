package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/** Minimal Velocity/Bungee plugin-message bridge hooks. */
public class VelocityBridgeManager {
    private final HungerGamesPlugin plugin;

    public VelocityBridgeManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean connectPlayer(Player player, String targetServer) {
        if (player == null || targetServer == null || targetServer.isBlank()) return false;
        if (!plugin.serverRole().velocityEnabled()) return false;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(targetServer);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Velocity connect failed for " + player.getName() + " -> " + targetServer + ": " + ex.getMessage());
            return false;
        }
    }
}

