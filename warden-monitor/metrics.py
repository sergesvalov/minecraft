"""Prometheus metric definitions for Warden Monitor."""

from prometheus_client import Counter, Gauge

MC_ONLINE = Gauge("minecraft_server_online", "Is the Minecraft server online (1) or down (0)")
MC_PLAYERS = Gauge("minecraft_player_count", "Number of players currently online")
MC_PING = Gauge("minecraft_server_ping_ms", "Server ping latency in milliseconds")
MC_TPS = Gauge("minecraft_server_tps", "Server TPS from last 1 minute")

BACKUP_VERIFY_OK = Gauge(
    "minecraft_backup_verify_ok",
    "Last backup consistency check result (1=ok, 0=failed, -1=unknown)",
)
BACKUP_VERIFY_TS = Gauge("minecraft_backup_last_verify_timestamp", "Unix timestamp of last backup verification")
BACKUP_AGE_SECONDS = Gauge("minecraft_backup_snapshot_age_seconds", "Age of latest backup snapshot in seconds")
BACKUP_SIZE_BYTES = Gauge("minecraft_backup_snapshot_size_bytes", "Size of latest backup snapshot in bytes")
BACKUP_CORRUPT_CHUNKS = Gauge(
    "minecraft_backup_corrupt_chunks",
    "Corrupt chunks found in latest backup verification",
)
BACKUP_VERIFY_ERRORS = Gauge("minecraft_backup_verify_errors", "Error count from latest backup verification")
BACKUP_VERIFY_OK.set(-1)

TNT_PLACED = Counter("warden_tnt_placed_total", "Total TNT blocks placed", ["player", "world", "x", "y", "z", "time"])
TNT_EXPLODED = Counter("warden_tnt_exploded_total", "Total TNT explosions", ["source", "world", "x", "y", "z", "time"])

PLAYER_JOINED = Counter("warden_player_joins_total", "Total player joins", ["player", "time"])
PLAYER_QUIT = Counter("warden_player_quits_total", "Total player quits", ["player", "time"])
DANGEROUS_MOB_SPAWN = Counter(
    "warden_dangerous_mob_spawn_total",
    "Total dangerous mobs spawned",
    ["mob", "player", "world", "x", "y", "z", "time"],
)

LAVA_PLACED = Counter("warden_lava_placed_total", "Total lava blocks placed", ["player", "world", "x", "y", "z", "time"])
CRYSTAL_PLACED = Counter(
    "warden_crystal_placed_total",
    "Total end crystals placed",
    ["player", "world", "x", "y", "z", "time"],
)
SUSPICIOUS_COMMAND = Counter(
    "warden_suspicious_command_total",
    "Total suspicious commands executed",
    ["player", "command", "time"],
)
PLAYER_KILLS = Counter("warden_player_kills_total", "Total PVP kills", ["killer", "victim", "time"])
FAILED_LOGINS = Counter("warden_failed_logins_total", "Total failed login attempts", ["player", "time"])

VALUABLE_BROKEN = Counter(
    "warden_valuable_block_broken_total",
    "Total valuable blocks broken",
    ["player", "block", "world", "x", "y", "z", "time"],
)
GRIEF_ALERTS = Counter("warden_grief_alerts_total", "Total grief alerts triggered", ["player", "blocks_broken", "time"])

