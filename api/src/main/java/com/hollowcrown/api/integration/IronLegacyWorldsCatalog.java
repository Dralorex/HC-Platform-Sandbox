package com.hollowcrown.api.integration;

import java.util.List;
import java.util.Optional;

/**
 * Hub-owned reader/writer for Iron Legacy {@code worlds.yml} metadata.
 */
public interface IronLegacyWorldsCatalog {

    record TemplateInfo(
            String key,
            String worldName,
            String displayName,
            String status,
            boolean enabled,
            int maxPlayers,
            int teamSizeMax,
            boolean teamFriendlyFire,
            boolean allyFriendlyFire,
            String iconKey,
            int version
    ) {}

    record LobbyZoneCorners(double x1, double y1, double z1, double x2, double y2, double z2) {}

    record LobbySpawn(String worldName, double x, double y, double z, float yaw, float pitch) {}

    List<TemplateInfo> listTemplateInfos();

    Optional<TemplateInfo> templateByKeyOrWorld(String keyOrWorld);

    Optional<String> resolveTemplateKeyForWorld(String worldName);

    int minimumPercentage(String templateKey);

    Optional<LobbyZoneCorners> lobbyZoneCorners(String templateKey);

    Optional<LobbySpawn> lobbySpawn(String templateKey);

    String resolveServerTarget(String templateKey);

    boolean setDisplayName(String templateKey, String displayName);

    boolean setEnabled(String templateKey, boolean enabled);

    boolean setMaxPlayers(String templateKey, int maxPlayers);

    boolean setTeamSizeMax(String templateKey, int teamSizeMax);

    boolean setTeamFriendlyFire(String templateKey, boolean teamFriendlyFire);

    boolean setAllyFriendlyFire(String templateKey, boolean allyFriendlyFire);

    boolean setIconKey(String templateKey, String iconKey);

    boolean setMinimumPercentage(String templateKey, int percentage);

    boolean setPublishGameRule(String templateKey, String ruleName, boolean value);

    /** Hub-side publish toggle for worlds.yml state (PUBLISHED + enabled=true). */
    boolean publishTemplate(String templateKey);
}

