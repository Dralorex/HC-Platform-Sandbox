package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

/**
 * Executes border shrink phases during a running match.
 * Replacement 4 old border shrink
 */
public class BorderShrinkManager {
    private final HungerGamesPlugin plugin;
    private final World matchWorld;
    private final List<Player> audience;
    private final GameManager gameManager;
    private final BorderShrinkSchedule schedule;

    //track phases have been scheduled
    private final Set<Integer> scheduledPhaseIndices = new HashSet<>();

    public BorderShrinkManager(HungerGamesPlugin plugin, World matchWorld, List<Player> audience,
                               GameManager gameManager, BorderShrinkSchedule schedule) {
        this.plugin = plugin;
        this.matchWorld = matchWorld;
        this.audience = audience != null ? new ArrayList<>(audience) : new ArrayList<>();
        this.gameManager = gameManager;
        this.schedule = schedule != null ? schedule : new BorderShrinkSchedule();
    }

    /**
     * Schedule all border shrink phases relative to match start
     */
    public void scheduleAllPhases() {
        for (int i = 0; i < schedule.size(); i++) {
            BorderShrinkPhase phase = schedule.getPhase(i);
            if (phase == null) continue;

            final int index = i;
            long delayTicks = phase.delaySeconds() * 20L;

            new BukkitRunnable() {
                @Override public void run() {
                    if (gameManager.getState() != GameManager.State.RUNNING) return;
                    executePhase(index, phase);
                }
            }.runTaskLater(plugin, delayTicks);
        }
    }

    private void executePhase(int index, BorderShrinkPhase phase) {
        if (matchWorld == null) return;
        if (scheduledPhaseIndices.contains(index)) return;
        scheduledPhaseIndices.add(index);

        WorldBorder wb = matchWorld.getWorldBorder();
        wb.setSize(phase.radiusEnd() * 2, phase.shrinkDurationSeconds());

        // notificatin 4 players
        for (Player p : audience) {
            if (p == null || !p.isOnline()) continue;
            p.sendMessage(Component.text(
                    String.format("[Border] Shrinking to %.0f blocks over %d seconds",
                            phase.radiusEnd() * 2, phase.shrinkDurationSeconds()),
                    NamedTextColor.YELLOW
            ));
        }
    }

    public void reset() {
        scheduledPhaseIndices.clear();
    }
}
