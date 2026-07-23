package com.hollowcrown.hub.template;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players who were blocked on publish for missing mechanic and may type {@code yes} for vanilla.
 */
public final class HubTemplatePublishPending {

    private static final long TTL_MS = 120_000L;

    private final Map<UUID, Entry> pending = new ConcurrentHashMap<>();

    public void register(UUID playerId, String canonicalIlKey) {
        if (playerId == null || canonicalIlKey == null || canonicalIlKey.isBlank()) {
            return;
        }
        pending.put(playerId, new Entry(canonicalIlKey.trim(), System.currentTimeMillis() + TTL_MS));
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            pending.remove(playerId);
        }
    }

    /**
     * @return canonical Iron Legacy worlds.yml key when message is {@code yes} and a prompt is active
     */
    public Optional<String> consumeYes(UUID playerId, String chatMessage) {
        if (playerId == null || chatMessage == null || !chatMessage.trim().equalsIgnoreCase("yes")) {
            return Optional.empty();
        }
        Entry e = pending.remove(playerId);
        if (e == null || System.currentTimeMillis() > e.expiresAtMs()) {
            return Optional.empty();
        }
        return Optional.of(e.canonicalIlKey());
    }

    private record Entry(String canonicalIlKey, long expiresAtMs) {
    }
}
