package com.server.wardenlog.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Validated runtime thresholds and log retention settings loaded from {@code config.yml}.
 */
public record WardenConfig(int entityLimit, int griefBlocks, int lagMachinePistons, int logRetentionDays) {

    public static WardenConfig from(FileConfiguration cfg) {
        return new WardenConfig(
                Math.max(1, cfg.getInt("limits.entities_per_chunk", 150)),
                Math.max(1, cfg.getInt("limits.grief_blocks", 45)),
                Math.max(1, cfg.getInt("limits.lag_machine_pistons", 500)),
                Math.max(1, cfg.getInt("log_retention_days", 14))
        );
    }
}

