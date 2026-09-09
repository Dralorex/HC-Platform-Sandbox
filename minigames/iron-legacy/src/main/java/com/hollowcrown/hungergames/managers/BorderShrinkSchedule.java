package com.hollowcrown.hungergames.managers;

import java.util.*;

/**
 * Manages all border shrink phases
 * Can load/save from ConfigurationSection
 */
public class BorderShrinkSchedule {
    private final List<BorderShrinkPhase> phases;

    public BorderShrinkSchedule() {
        this.phases = new ArrayList<>();
    }

    public BorderShrinkSchedule(List<BorderShrinkPhase> phases) {
        this.phases = new ArrayList<>(phases);
        this.phases.sort(Comparator.comparingLong(BorderShrinkPhase::delaySeconds));
    }

    public List<BorderShrinkPhase> phases() {
        return new ArrayList<>(phases);
    }

    public void addPhase(BorderShrinkPhase phase) {
        phases.add(phase);
        phases.sort(Comparator.comparingLong(BorderShrinkPhase::delaySeconds));
    }

    public void removePhase(int index) {
        if (index >= 0 && index < phases.size()) {
            phases.remove(index);
        }
    }

    public BorderShrinkPhase getPhase(int index) {
        return phases.size() > index ? phases.get(index) : null;
    }

    public int size() {
        return phases.size();
    }

    public boolean isEmpty() {
        return phases.isEmpty();
    }
}
