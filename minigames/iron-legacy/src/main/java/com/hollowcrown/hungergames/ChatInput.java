package com.hollowcrown.hungergames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationAbandonedListener;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.ManuallyAbandonedConversationCanceller;
import org.bukkit.conversations.NullConversationPrefix;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Modal chat prompts via the Bukkit Conversation API. Closing chat with ESC abandons the
 * conversation and runs {@code onCancel} when provided.
 */
public class ChatInput implements ConversationAbandonedListener {
    private static final String SESSION_DONE = "hg_chat_prompt_done";

    private final HungerGamesPlugin plugin;
    private final Map<UUID, Runnable> cancelHandlers = new ConcurrentHashMap<>();
    private final Set<UUID> suppressAbandonCancel = ConcurrentHashMap.newKeySet();

    public ChatInput(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void prompt(Player player, String message, Consumer<String> onInput) {
        prompt(player, message, onInput, null);
    }

    public void prompt(Player player, String message, Consumer<String> onInput, Runnable onCancel) {
        Bukkit.getScheduler().runTask(plugin, () -> beginPrompt(player, message, onInput, onCancel));
    }

    private void beginPrompt(Player player, String message, Consumer<String> onInput, Runnable onCancel) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) return;

        if (player.isConversing()) {
            suppressAbandonCancel.add(id);
            try {
                abandonCurrentConversation(player);
            } finally {
                suppressAbandonCancel.remove(id);
            }
        }

        if (onCancel != null) cancelHandlers.put(id, onCancel);
        else cancelHandlers.remove(id);

        String promptLegacy = LegacyComponentSerializer.legacySection().serialize(
                Component.text(message, NamedTextColor.YELLOW));

        ConversationFactory factory = new ConversationFactory(plugin)
                .withModality(true)
                .withLocalEcho(false)
                .withPrefix(new NullConversationPrefix())
                .withTimeout(900)
                .thatExcludesNonPlayersWithMessage("")
                .addConversationAbandonedListener(this)
                .withFirstPrompt(new StringPrompt() {
                    @Override
                    public String getPromptText(ConversationContext context) {
                        return promptLegacy;
                    }

                    @Override
                    public Prompt acceptInput(ConversationContext context, String input) {
                        UUID pid = ((Player) context.getForWhom()).getUniqueId();
                        context.setSessionData(SESSION_DONE, Boolean.TRUE);
                        cancelHandlers.remove(pid);
                        String msg = input == null ? "" : input.trim();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (((Player) context.getForWhom()).isOnline()) {
                                onInput.accept(msg);
                            }
                        });
                        return Prompt.END_OF_CONVERSATION;
                    }
                });

        Conversation conv = factory.buildConversation(player);
        conv.begin();
    }

    /**
     * Paper API 1.21.x no longer exposes {@code ConversationAbandonReason}; abandon via the
     * current conversation instance when available.
     */
    private void abandonCurrentConversation(Player player) {
        try {
            var getConversation = player.getClass().getMethod("getConversation");
            Object active = getConversation.invoke(player);
            if (!(active instanceof Conversation conv)) return;
            try {
                // Preferred: abandonConversation(Conversation)
                player.getClass().getMethod("abandonConversation", Conversation.class).invoke(player, conv);
                return;
            } catch (NoSuchMethodException ignored) {
                // Fallback for APIs expecting an explicit abandoned event object.
                ConversationAbandonedEvent ev =
                        new ConversationAbandonedEvent(conv, new ManuallyAbandonedConversationCanceller());
                player.getClass().getMethod("abandonConversation", ConversationAbandonedEvent.class).invoke(player, ev);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void conversationAbandoned(ConversationAbandonedEvent event) {
        if (!(event.getContext().getPlugin() instanceof HungerGamesPlugin)) return;
        if (!(event.getContext().getForWhom() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        if (suppressAbandonCancel.contains(id)) return;
        if (Boolean.TRUE.equals(event.getContext().getSessionData(SESSION_DONE))) return;

        Runnable r = cancelHandlers.remove(id);
        if (r != null) {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }
}
