package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Paginated OPEN lobby list for one template (after right-click on template).
 */
public final class LobbyJoinInstanceMenuHolder implements InventoryHolder {

    public static final int PAGE_SIZE = 36;
    public static final int INV_SIZE = 54;

    private final HollowCrownHubPlugin plugin;
    private final String templateKey;
    private final int page;
    private final int totalPages;
    private final Inventory inventory;

    public LobbyJoinInstanceMenuHolder(
            HollowCrownHubPlugin plugin,
            String templateKey,
            int page,
            int totalPages
    ) {
        this.plugin = plugin;
        this.templateKey = templateKey;
        this.page = page;
        this.totalPages = Math.max(1, totalPages);
        this.inventory = Bukkit.createInventory(
                this,
                INV_SIZE,
                Component.text("Lobbies: " + templateKey + " — " + (page + 1) + "/" + this.totalPages)
        );
    }

    public HollowCrownHubPlugin plugin() {
        return plugin;
    }

    public String templateKey() {
        return templateKey;
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
