"""Backup verification JSON watcher, Prometheus updater, and Telegram alerter."""

import json
import logging
import os
import time

import config
import metrics
from telegram import send_telegram_message


def read_backup_report(path: str = None):
    report_path = path or config.BACKUP_REPORT_FILE
    if not os.path.exists(report_path):
        return None
    try:
        with open(report_path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        logging.warning(f"Failed to read {report_path}: {e}")
        return None


class BackupWatcher:
    """Tracks backup verification state transitions across polling cycles."""

    def __init__(self, stale_hours: float = None):
        self.stale_hours = config.BACKUP_STALE_HOURS if stale_hours is None else stale_hours
        self.last_seen_epoch = None
        self.last_ok = None
        self.stale_alerted = False

    def step(self, report, now_ts: float = None, notify=send_telegram_message) -> None:
        if report is None:
            return
        now = time.time() if now_ts is None else now_ts
        ok = bool(report.get("ok", False))
        verify_epoch = float(report.get("epoch") or 0)
        snap_epoch = float(report.get("snapshot_epoch") or 0)
        snap_bytes = int(report.get("snapshot_bytes") or 0)
        corrupt_chunks = int(report.get("corrupt_chunks") or 0)
        errors = report.get("errors") or []
        snap_id = report.get("snapshot_id") or "?"

        metrics.BACKUP_VERIFY_OK.set(1 if ok else 0)
        metrics.BACKUP_VERIFY_TS.set(verify_epoch)
        metrics.BACKUP_SIZE_BYTES.set(snap_bytes)
        metrics.BACKUP_CORRUPT_CHUNKS.set(corrupt_chunks)
        metrics.BACKUP_VERIFY_ERRORS.set(len(errors))

        age_sec = max(0.0, now - snap_epoch) if snap_epoch > 0 else -1.0
        if age_sec >= 0:
            metrics.BACKUP_AGE_SECONDS.set(age_sec)

        if verify_epoch != self.last_seen_epoch:
            if not ok:
                preview = "; ".join(str(e) for e in errors[:3]) or "неизвестная ошибка"
                logging.error(f"❌ Backup verification FAILED (snapshot={snap_id}): {preview}")
                notify(f"❌ ОШИБКА БЭКАПА (снапшот {snap_id}): {preview}")
            elif self.last_ok is False:
                logging.info(f"✅ Backup verification recovered (snapshot={snap_id})")
                notify(f"✅ Проверка бэкапа восстановлена: снапшот {snap_id} полностью консистентен.")
            self.last_seen_epoch = verify_epoch
            self.last_ok = ok
            if ok and 0 <= age_sec <= self.stale_hours * 3600:
                self.stale_alerted = False

        if age_sec > self.stale_hours * 3600 and not self.stale_alerted:
            age_h = age_sec / 3600.0
            logging.warning(f"⚠️ Latest backup snapshot {snap_id} is {age_h:.1f}h old")
            notify(f"⚠️ ВНИМАНИЕ: Последний проверенный бэкап ({snap_id}) устарел — {age_h:.1f} ч назад!")
            self.stale_alerted = True


def backup_monitor() -> None:
    logging.info("Started backup verification monitor")
    watcher = BackupWatcher()
    while True:
        watcher.step(read_backup_report())
        time.sleep(60)

