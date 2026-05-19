package com.hollowcrown.api.integration;

import java.util.List;

/** Result of Iron Legacy {@code worlds.yml} sync from Hub {@code TemplateRegistry}. */
public record IronLegacyHubImportResult(
        int newRowsWritten,
        int skippedAlreadyPresent,
        int skippedNoHubSource,
        int metadataSyncWritten,
        boolean dryRun,
        List<String> messages
) {
    public IronLegacyHubImportResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
