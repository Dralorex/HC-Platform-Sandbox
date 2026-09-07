package com.hollowcrown.hub.command;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps user CLI tokens to Iron Legacy {@code worlds.yml} row keys.
 * Accepts either the YAML section key or the server's world folder name ({@code worldName}).
 */
public final class HubIlWorldInput {

    private HubIlWorldInput() {
    }

    /** Canonical key string for {@link IronLegacyTemplateAdmin} calls (matches worlds.yml casing). */
    public static Optional<String> resolveCanonicalKey(IronLegacyTemplateAdmin admin, String raw) {
        if (admin == null || raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String t = stripQuotes(raw.trim());
        if (t.isBlank()) {
            return Optional.empty();
        }
        String leaf = leafName(t);
        for (var row : admin.listTemplates()) {
            if (row.key().equalsIgnoreCase(t) || row.key().equalsIgnoreCase(leaf)) {
                return Optional.of(row.key());
            }
            String wn = row.worldName();
            if (wn != null && !wn.isBlank()) {
                String wnNorm = wn.replace('\\', '/');
                if (wnNorm.equalsIgnoreCase(t) || leafName(wnNorm).equalsIgnoreCase(t)
                        || leafName(wnNorm).equalsIgnoreCase(leaf)) {
                    return Optional.of(row.key());
                }
            }
        }
        return Optional.empty();
    }

    public static boolean matchesAnyTemplate(IronLegacyTemplateAdmin admin, String raw) {
        return resolveCanonicalKey(admin, raw).isPresent();
    }

    /** Prefer showing the on-disk folder name in messages. */
    public static String folderLabel(IronLegacyTemplateAdmin admin, String canonicalKey) {
        if (admin == null || canonicalKey == null) {
            return canonicalKey == null ? "" : canonicalKey;
        }
        for (var row : admin.listTemplates()) {
            if (row.key().equalsIgnoreCase(canonicalKey)) {
                String wn = row.worldName();
                return (wn != null && !wn.isBlank()) ? leafName(wn.replace('\\', '/')) : row.key();
            }
        }
        return canonicalKey;
    }

    static String stripQuotes(String s) {
        if (s == null || s.length() < 2) {
            return s == null ? "" : s;
        }
        char a = s.charAt(0);
        char b = s.charAt(s.length() - 1);
        if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static String leafName(String path) {
        String n = path.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        return slash >= 0 ? n.substring(slash + 1) : n;
    }

    /** Tab suggestions: worlds.yml keys only (not folder paths). */
    public static void addCompletionTokens(IronLegacyTemplateAdmin admin, String prefixLc, LinkedHashSet<String> out) {
        if (admin == null || prefixLc == null) {
            return;
        }
        for (var row : admin.listTemplates()) {
            if (row.key().toLowerCase(Locale.ROOT).startsWith(prefixLc)) {
                out.add(row.key());
            }
        }
    }
}
