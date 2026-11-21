package com.server.wardenlog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sliding-window rate tracker with per-subject cooldowns for grief block-breaking and piston lag machines.
 */
public final class RateTracker {

    private static final long GRIEF_WINDOW_MS = 10_000L;
    private static final long GRIEF_COOLDOWN_MS = 60_000L;
    private static final long PISTON_COOLDOWN_MS = 300_000L;

    private final Map<String, List<Long>> breakTracker = new HashMap<>();
    private final Map<String, Long> griefCooldowns = new HashMap<>();
    private final Map<Long, Integer> chunkPistonCounts = new HashMap<>();
    private final Map<Long, Long> chunkPistonCooldowns = new HashMap<>();

    /**
     * Records a block break for {@code player}; returns the burst count if it exceeds {@code limit}
     * outside cooldown, or {@code 0} otherwise.
     */
    public int recordBreak(String player, long nowMs, int limit) {
        List<Long> breaks = breakTracker.computeIfAbsent(player, k -> new ArrayList<>());
        breaks.add(nowMs);
        breaks.removeIf(t -> nowMs - t > GRIEF_WINDOW_MS);
        if (breaks.size() <= limit) {
            return 0;
        }
        Long cooldown = griefCooldowns.get(player);
        if (cooldown != null && nowMs <= cooldown) {
            return 0;
        }
        griefCooldowns.put(player, nowMs + GRIEF_COOLDOWN_MS);
        return breaks.size();
    }

    /**
     * Records a piston extension in {@code chunkKey}; returns true if the burst exceeds {@code limit}
     * outside the chunk cooldown.
     */
    public boolean recordPiston(long chunkKey, long nowMs, int limit) {
        if (chunkPistonCooldowns.getOrDefault(chunkKey, 0L) > nowMs) {
            return false;
        }
        int count = chunkPistonCounts.getOrDefault(chunkKey, 0) + 1;
        chunkPistonCounts.put(chunkKey, count);
        if (count > limit) {
            chunkPistonCooldowns.put(chunkKey, nowMs + PISTON_COOLDOWN_MS);
            return true;
        }
        return false;
    }

    public void clearPistons() {
        chunkPistonCounts.clear();
    }

    public void prune(long nowMs) {
        breakTracker.values().forEach(list -> list.removeIf(t -> nowMs - t > GRIEF_WINDOW_MS));
        breakTracker.values().removeIf(List::isEmpty);
        chunkPistonCooldowns.entrySet().removeIf(entry -> nowMs > entry.getValue());
        griefCooldowns.entrySet().removeIf(entry -> nowMs > entry.getValue());
    }
}

