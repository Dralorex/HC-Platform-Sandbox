package com.hollowcrown.hub.gui;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class LobbyJoinMechanicMenuHolder implements InventoryHolder {

    public static final int PAGE_SIZE = 28;

    private final HollowCrownHubPlugin plugin;
    private final int page;
    private final int totalPages;
    private final Inventory inv;

    public LobbyJoinMechanicMenuHolder(HollowCrownHubPlugin plugin, int page, int totalPages) {
        this.plugin = plugin;
        this.page = page;
        this.totalPages = totalPages;
        this.inv = Bukkit.createInventory(this, 54,
                Component.text("Game Lobby · Mechanics", NamedTextColor.GREEN));
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
        return inv;
    }
}
