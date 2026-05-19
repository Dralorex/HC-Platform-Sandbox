package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Applies configured resource pack policy from resourcepack.yml. */
public class ResourcePackManager implements Listener {
    private final HungerGamesPlugin plugin;

    public ResourcePackManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void applyIfEnabled(Player p) {
        var cfg = plugin.files().resourcepack();
        boolean enabled = cfg.getBoolean("resourcepack.enabled", false);
        if (!enabled) return;

        String url = cfg.getString("resourcepack.url", "");
        if (url == null || url.isBlank()) return;
        String prompt = cfg.getString("resourcepack.prompt", "This server uses a resource pack.");
        boolean enforce = cfg.getBoolean("resourcepack.enforce", false);
        String sha1 = cfg.getString("resourcepack.sha1", "");

        byte[] hash = decodeSha1(sha1);
        if (hash != null) {
            p.setResourcePack(url, hash, prompt, enforce);
        } else {
            p.setResourcePack(url, null, prompt, enforce);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        applyIfEnabled(e.getPlayer());
    }

    @EventHandler
    public void onResourceStatus(PlayerResourcePackStatusEvent e) {
        var cfg = plugin.files().resourcepack();
        if (!cfg.getBoolean("resourcepack.enabled", false)) return;
        if (!cfg.getBoolean("resourcepack.enforce", false)) return;

        PlayerResourcePackStatusEvent.Status s = e.getStatus();
        if (s == PlayerResourcePackStatusEvent.Status.DECLINED
                || s == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || s == PlayerResourcePackStatusEvent.Status.INVALID_URL
                || s == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD) {
            e.getPlayer().kickPlayer("A required resource pack was not accepted.");
        }
    }

    private byte[] decodeSha1(String raw) {
        if (raw == null) return null;
        String hex = raw.trim().toLowerCase();
        if (hex.isBlank()) return null;
        if (!hex.matches("[0-9a-f]{40}")) return null;
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}

