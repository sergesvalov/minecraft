package com.server.wardenlog.tracking;

import java.util.Locale;
import java.util.Set;

/**
 * Pure normalization and classification of admin / WorldEdit commands for WardenLog alerts.
 */
public final class CommandFilter {

    private static final Set<String> WATCHED = Set.of(
            "/op", "/deop", "/ban", "/kick", "/stop", "//set", "//replace"
    );

    private CommandFilter() {
    }

    /** Lowercases the command and strips any plugin namespace prefix (e.g. {@code /minecraft:op} -> {@code /op}). */
    public static String normalize(String rawCmd) {
        if (rawCmd == null) {
            return "";
        }
        String cmd = rawCmd.trim().toLowerCase(Locale.ROOT);
        if (!cmd.startsWith("/")) {
            return cmd;
        }
        int spaceIdx = cmd.indexOf(' ');
        String baseCmd = spaceIdx != -1 ? cmd.substring(1, spaceIdx) : cmd.substring(1);
        String args = spaceIdx != -1 ? cmd.substring(spaceIdx) : "";
        int colonIdx = baseCmd.indexOf(':');
        if (colonIdx != -1) {
            baseCmd = baseCmd.substring(colonIdx + 1);
        }
        return "/" + baseCmd + args;
    }

    /** Returns true if {@code rawCmd} invokes a watched command. */
    public static boolean isSuspicious(String rawCmd) {
        String normalized = normalize(rawCmd);
        if (normalized.isEmpty()) {
            return false;
        }
        int spaceIdx = normalized.indexOf(' ');
        String base = spaceIdx != -1 ? normalized.substring(0, spaceIdx) : normalized;
        return WATCHED.contains(base);
    }
}

