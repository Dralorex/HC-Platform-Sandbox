package com.hollowcrown.hub.template;

import com.hollowcrown.api.template.TemplateEntry;
import com.hollowcrown.api.template.TemplateRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Adapts mutable {@link HubTemplateRegistry} to API {@link TemplateRegistry}.
 */
public final class HubTemplateApiRegistry implements TemplateRegistry {

    private final HubTemplateRegistry delegate;

    public HubTemplateApiRegistry(HubTemplateRegistry delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<TemplateEntry> list() {
        List<TemplateEntry> out = new ArrayList<>();
        for (HubTemplateEntry row : delegate.list()) {
            try {
                out.add(toApi(row));
            } catch (RuntimeException ex) {
                // Never let a bad templates.yml row take down minigame enable / Hub services.
                java.util.logging.Logger.getLogger(HubTemplateApiRegistry.class.getName()).log(
                        Level.WARNING,
                        "Skipping invalid Hub template row key=" + row.templateKey() + ": " + ex.getMessage());
            }
        }
        return List.copyOf(out);
    }

    private static TemplateEntry toApi(HubTemplateEntry row) {
        return new TemplateEntry(
                row.templateKey(),
                row.mechanicId() == null ? "" : row.mechanicId(),
                row.displayName(),
                row.sourceWorldName(),
                row.queueMaxUnitSize(),
                row.queueSoloOnly(),
                row.queueSoloAutoMerge(),
                row.joinQueueFirst()
        );
    }
}
