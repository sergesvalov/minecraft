"""Runtime configuration loaded from environment variables with safe defaults."""

import os


def _float_env(name: str, default: float) -> float:
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        return float(raw)
    except ValueError:
        return default


def _int_env(name: str, default: int) -> int:
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        return default


LOG_FILE = os.environ.get("WARDEN_LOG_FILE", "/app/logs/events.jsonl")
TG_CONFIG_FILE = os.environ.get("WARDEN_TG_CONFIG_FILE", "/app/logs/tg_config.json")
BACKUP_REPORT_FILE = os.environ.get("BACKUP_REPORT_FILE", "/app/logs/backup_verify.json")
BACKUP_STALE_HOURS = _float_env("BACKUP_STALE_HOURS", 28.0)

MC_HOST = os.environ.get("MC_HOST", "mc")
MC_PORT = _int_env("MC_PORT", 25565)
RCON_HOST = os.environ.get("RCON_HOST", MC_HOST)
RCON_PORT = _int_env("RCON_PORT", 25575)
RCON_PASSWORD = os.environ.get("RCON_PASSWORD", "admin")

PROMETHEUS_PORT = _int_env("PROMETHEUS_PORT", 8000)
WEBHOOK_PORT = _int_env("WEBHOOK_PORT", 8001)

