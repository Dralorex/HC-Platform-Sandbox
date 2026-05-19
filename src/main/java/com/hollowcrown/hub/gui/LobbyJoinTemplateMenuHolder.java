package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Player join flow — pick a game template (one icon per template).
 */
public final class LobbyJoinTemplateMenuHolder implements InventoryHolder {

    public static final int PAGE_SIZE = 36;
    public static final int INV_SIZE = 54;

    private final HollowCrownHubPlugin plugin;
    private final int page;
    private final int totalPages;
    private final Inventory inventory;

    public LobbyJoinTemplateMenuHolder(HollowCrownHubPlugin plugin, int page, int totalPages) {
        this.plugin = plugin;
        this.page = page;
        this.totalPages = Math.max(1, totalPages);
        this.inventory = Bukkit.createInventory(
                this,
                INV_SIZE,
                Component.text("Join a game — " + (page + 1) + "/" + this.totalPages)
        );
    }

    public HollowCrownHubPlugin plugin() {
        return plugin;
    }

    public int page() {
        return page;
    }

    public int totalPages() {
        return totalPages;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
