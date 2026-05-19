package com.hollowcrown.hungergames.integration;

import com.hollowcrown.api.mechanic.MechanicContext;
import org.bukkit.World;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks lobby instance worlds created by Hollow Crown Hub ({@code MechanicContext}) so Iron Legacy
 * can resolve template keys without duplicating Hub's world copy / naming logic.
 */
public final class HubManagedLobbyRegistry {

    private final ConcurrentHashMap<UUID, String> worldUidToTemplateKey = new ConcurrentHashMap<>();

    public void register(MechanicContext ctx) {
        if (ctx == null) {
            return;
        }
        worldUidToTemplateKey.put(ctx.lobbyWorldUuid(), ctx.templateKey().value());
    }

    public void unregister(MechanicContext ctx) {
        if (ctx == null) {
            return;
        }
        worldUidToTemplateKey.remove(ctx.lobbyWorldUuid());
    }

    public Optional<String> templateKeyForWorld(World world) {
        if (world == null) {
            return Optional.empty();
        }
        String tk = worldUidToTemplateKey.get(world.getUID());
        return tk == null || tk.isBlank() ? Optional.empty() : Optional.of(tk);
    }

    public void clear() {
        worldUidToTemplateKey.clear();
    }
}
