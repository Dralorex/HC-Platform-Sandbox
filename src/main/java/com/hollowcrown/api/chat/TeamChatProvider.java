package com.hollowcrown.api.chat;

import java.util.Optional;
import java.util.UUID;

/**
 * Registered by Iron Legacy when teams are in use; chat plugins can resolve team prefix + color.
 */
public interface TeamChatProvider {

    Optional<TeamChatProfile> profile(UUID playerId);
}
