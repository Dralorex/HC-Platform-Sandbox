package com.hollowcrown.hub.lobby;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public final class HubLobbyInstanceRecord {

    private final String id;
    private final String templateKey;
    private final String worldName;
    private final UUID worldUuid;
    private final int lobbyIndex;
    private final long createdAt;
    private final long prioritySequence;
    private LobbyInstanceState state;

    public HubLobbyInstanceRecord(
            String id,
            String templateKey,
            String worldName,
            UUID worldUuid,
            int lobbyIndex,
            long createdAt,
            long prioritySequence,
            LobbyInstanceState state
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.templateKey = Objects.requireNonNull(templateKey, "templateKey");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.worldUuid = Objects.requireNonNull(worldUuid, "worldUuid");
        this.lobbyIndex = lobbyIndex;
        this.createdAt = createdAt;
        this.prioritySequence = prioritySequence;
        this.state = Objects.requireNonNull(state, "state");
    }

    public String id() {
        return id;
    }

    public String templateKey() {
        return templateKey;
    }

    public String worldName() {
        return worldName;
    }

    public UUID worldUuid() {
        return worldUuid;
    }

    public int lobbyIndex() {
        return lobbyIndex;
    }

    public long createdAt() {
        return createdAt;
    }

    public long prioritySequence() {
        return prioritySequence;
    }

    public LobbyInstanceState state() {
        return state;
    }

    public void setState(LobbyInstanceState state) {
        this.state = Objects.requireNonNull(state);
    }

    public World loadedWorld() {
        return Bukkit.getWorld(worldUuid());
    }
}
