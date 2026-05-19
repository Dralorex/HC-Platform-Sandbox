package com.hollowcrown.hungergames.integration;

import com.hollowcrown.api.template.TemplateEntry;
import com.hollowcrown.api.world.GameWorldScope;
import com.hollowcrown.api.world.GameWorldScopeResolver;
import com.hollowcrown.hungergames.HungerGamesPlugin;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tags environmental-hazard traps with Iron Legacy / Hub template scope based on world naming.
 */
public final class IlGameWorldScopeResolver implements GameWorldScopeResolver {

    private static final Pattern HUB_LOBBY = Pattern.compile("(?i)^(.+)-L\\d+$");

    private final HungerGamesPlugin plugin;

    public IlGameWorldScopeResolver(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<GameWorldScope> resolve(UUID worldUuid, String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return Optional.empty();
        }
        var hubLobby = HUB_LOBBY.matcher(worldName);
        if (hubLobby.matches()) {
            String prefix = hubLobby.group(1);
            Optional<TemplateEntry> row = plugin.hollowTemplateRegistry().flatMap(r -> r.findByTemplateKey(prefix));
            if (row.isPresent() && "iron-legacy".equalsIgnoreCase(row.get().mechanicId())) {
                return Optional.of(new GameWorldScope(
                        row.get().templateKey().toLowerCase(Locale.ROOT),
                        GameWorldScope.SCOPE_LOBBY_INSTANCE,
                        worldName));
            }
        }
        Optional<String> tk = plugin.templates().resolveTemplateKeyForWorld(worldName);
        if (tk.isEmpty()) {
            return Optional.empty();
        }
        String kind = plugin.templates().isManagedInstanceWorld(worldName)
                ? GameWorldScope.SCOPE_MATCH_INSTANCE
                : GameWorldScope.SCOPE_TEMPLATE_EDITOR;
        return Optional.of(new GameWorldScope(tk.get().toLowerCase(Locale.ROOT), kind, worldName));
    }
}
