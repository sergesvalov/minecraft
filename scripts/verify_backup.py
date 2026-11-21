#!/usr/bin/env python3
"""
Automated consistency verification for Minecraft restic backups (itzg/mc-backup).

Validates:
1. Restic repository structure & optional data subset integrity (`restic check`).
2. Latest snapshot freshness, file count, and size anomaly detection.
3. RCON save-off / save-all / save-on sequence in mc-backup container logs.
4. Zero-disk streaming validation of Minecraft & plugin data via `restic dump --archive tar`:
   - world/level.dat (gzip + NBT compound)
   - All *.mca Anvil region/entity/poi chunks (headers, sector bounds, zlib/gzip NBT)
   - SQLite databases (`PRAGMA integrity_check`) and H2 `.mv.db` dual headers
   - Critical JSON & plugin state files (`ops.json`, `buildings.txt`, `taverns.txt`, etc.)
"""

import argparse
import gzip
import json
import os
import re
import sqlite3
import struct
import subprocess
import sys
import tarfile
import tempfile
import time
import zlib
from datetime import datetime, timezone

SQLITE_MAGIC = b"SQLite format 3\x00"
H2_MAGIC = b"H:2,"
MAX_DB_STREAM_BYTES = 50 * 1024 * 1024
CRITICAL_JSON_PATHS = frozenset({
    "data/ops.json",
    "data/banned-players.json",
    "data/banned-ips.json",
    "data/plugins/axgraves/data.json",
    "data/plugins/luckperms/contexts.json",
})
PLUGIN_TEXT_PATHS = frozenset({
    "data/plugins/greatbuildings/buildings.txt",
    "data/plugins/halloumitrader/taverns.txt",
})


def parse_iso_timestamp(ts: str) -> datetime:
    """Parses an RFC3339/ISO timestamp (including restic 9-digit nanoseconds) into UTC-aware datetime."""
    cleaned = ts.strip()
    if cleaned.endswith("Z"):
        cleaned = cleaned[:-1] + "+00:00"
    cleaned = re.sub(r"\.(\d{6})\d+", r".\1", cleaned)
    dt = datetime.fromisoformat(cleaned)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def evaluate_snapshots(snapshots: list, now_ts: float, max_age_hours: float,
                       max_drop_percent: float) -> tuple:
    """Validates snapshot list for presence, freshness, non-emptiness, and sudden shrinkage."""
    errors = []
    warnings = []
    meta = {
        "snapshot_id": "",
        "snapshot_time": "",
        "snapshot_epoch": 0.0,
        "snapshot_age_hours": -1.0,
        "snapshot_bytes": 0,
        "snapshot_files": 0,
    }
    if not snapshots:
        return meta, ["No restic snapshots found in repository"], warnings

    ordered = sorted(snapshots, key=lambda s: parse_iso_timestamp(s["time"]).timestamp())
    latest = ordered[-1]
    dt = parse_iso_timestamp(latest["time"])
    epoch = dt.timestamp()
    age_hours = max(0.0, (now_ts - epoch) / 3600.0)
    summary = latest.get("summary") or {}
    cur_bytes = int(summary.get("total_bytes_processed", 0))
    cur_files = int(summary.get("total_files_processed", 0))

    meta.update({
        "snapshot_id": latest.get("short_id") or latest.get("id", "")[:8],
        "snapshot_time": latest["time"],
        "snapshot_epoch": round(epoch, 3),
        "snapshot_age_hours": round(age_hours, 2),
        "snapshot_bytes": cur_bytes,
        "snapshot_files": cur_files,
    })

    if age_hours > max_age_hours:
        errors.append(
            f"Latest snapshot {meta['snapshot_id']} is stale: {age_hours:.1f}h old (limit {max_age_hours:.1f}h)"
        )
    if cur_files <= 0 or cur_bytes <= 0:
        errors.append(f"Latest snapshot {meta['snapshot_id']} looks empty (files={cur_files}, bytes={cur_bytes})")

    if len(ordered) >= 2:
        prev_summary = ordered[-2].get("summary") or {}
        prev_bytes = int(prev_summary.get("total_bytes_processed", 0))
        if prev_bytes > 0:
            drop_pct = (prev_bytes - cur_bytes) * 100.0 / prev_bytes
            if drop_pct > max_drop_percent:
                errors.append(
                    f"Snapshot size dropped sharply by {drop_pct:.1f}% "
                    f"({prev_bytes} -> {cur_bytes} bytes, limit {max_drop_percent:.1f}%)"
                )

    return meta, errors, warnings


def parse_rcon_consistency(log_text: str, snapshot_short_id: str) -> tuple:
    """Checks mc-backup logs around `snapshot <id> saved` for save-off, save-all, and save-on."""
    lines = log_text.splitlines()
    marker = f"snapshot {snapshot_short_id} saved"
    match_idx = None
    for i in range(len(lines) - 1, -1, -1):
        if marker in lines[i]:
            match_idx = i
            break
    if match_idx is None:
        return "not_in_logs", [], [f"Snapshot {snapshot_short_id} not found in current mc-backup container logs"]

    start = max(0, match_idx - 40)
    for j in range(match_idx - 1, start - 1, -1):
        if "sleeping " in lines[j] or ("snapshot " in lines[j] and " saved" in lines[j]):
            start = j + 1
            break
    end = min(len(lines), match_idx + 6)

    pre = lines[start:match_idx]
    post = lines[match_idx + 1:end]
    errors = []

    has_save_off = any("Command executed successfully rcon-cli save-off" in line for line in pre)
    has_save_all = any("Command executed successfully rcon-cli save-all" in line for line in pre)
    has_save_on = any("Command executed successfully rcon-cli save-on" in line for line in post)

    if not has_save_off:
        errors.append(f"RCON save-off missing before snapshot {snapshot_short_id}")
    if not has_save_all:
        errors.append(f"RCON save-all missing before snapshot {snapshot_short_id}")
    if not has_save_on:
        errors.append(f"RCON save-on missing after snapshot {snapshot_short_id}")

    for line in pre + post:
        low = line.lower()
        if ("rcon" in low and ("fail" in low or "error" in low or "refused" in low or "timeout" in low)) or "error " in low:
            errors.append(f"Backup RCON error in logs: {line.strip()}")
            break

    return ("ok", [], []) if not errors else ("error", errors, [])


def validate_level_dat(raw: bytes, path: str) -> list:
    """Validates gzipped NBT compound structure of Minecraft level.dat."""
    if not raw:
        return [f"{path}: empty file"]
    try:
        data = gzip.decompress(raw)
    except Exception as exc:
        return [f"{path}: gzip decompression failed ({exc})"]
    if not data or data[0] != 0x0A:
        return [f"{path}: root NBT tag is not TAG_Compound"]
    if b"Data" not in data or b"LevelName" not in data:
        return [f"{path}: missing expected Data/LevelName NBT fields"]
    return []


def validate_mca(raw: bytes, path: str) -> tuple:
    """Validates Anvil .mca location headers, sector bounds, and chunk NBT payloads."""
    if len(raw) == 0:
        return 0, 0, []
    if len(raw) < 8192:
        return 0, 1, [f"{path}: truncated region header ({len(raw)} bytes < 8192)"]

    chunks_checked = 0
    corrupt_chunks = 0
    sample_errors = []

    def add_err(msg: str) -> None:
        if len(sample_errors) < 3:
            sample_errors.append(msg)

    for idx in range(1024):
        loc = struct.unpack_from(">I", raw, idx * 4)[0]
        if loc == 0:
            continue
        chunks_checked += 1
        offset = loc >> 8
        sectors = loc & 0xFF
        if offset < 2 or sectors == 0:
            corrupt_chunks += 1
            add_err(f"{path}: invalid location entry idx={idx} (offset={offset}, sectors={sectors})")
            continue
        pos = offset * 4096
        end = (offset + sectors) * 4096
        if pos + 5 > len(raw):
            corrupt_chunks += 1
            add_err(f"{path}: chunk idx={idx} offset out of file bounds (pos={pos}, size={len(raw)})")
            continue
        chunk_len = struct.unpack_from(">I", raw, pos)[0]
        # Paper/Moonrise may omit trailing 4 KiB zero-padding on the final chunk in a region file
        if chunk_len < 1 or pos + 4 + chunk_len > end or pos + 4 + chunk_len > len(raw):
            corrupt_chunks += 1
            add_err(
                f"{path}: chunk idx={idx} invalid/truncated length {chunk_len} "
                f"(data_end={pos + 4 + chunk_len}, sector_end={end}, size={len(raw)})"
            )
            continue

        comp = raw[pos + 4]
        payload = raw[pos + 5:pos + 4 + chunk_len]
        try:
            if comp == 1:
                nbt = gzip.decompress(payload)
            elif comp == 2:
                nbt = zlib.decompress(payload)
            elif comp == 3:
                nbt = payload
            elif comp == 4:
                if len(payload) < 1:
                    corrupt_chunks += 1
                    add_err(f"{path}: chunk idx={idx} empty LZ4 payload")
                continue
            elif comp >= 128:
                continue  # External .mcc chunk file
            else:
                corrupt_chunks += 1
                add_err(f"{path}: chunk idx={idx} unknown compression {comp}")
                continue
        except Exception as exc:
            corrupt_chunks += 1
            add_err(f"{path}: chunk idx={idx} decompression failed ({exc})")
            continue

        if not nbt or nbt[0] != 0x0A:
            corrupt_chunks += 1
            add_err(f"{path}: chunk idx={idx} root NBT tag is not TAG_Compound")

    return chunks_checked, corrupt_chunks, sample_errors


def validate_sqlite_bytes(raw: bytes, path: str) -> list:
    """Runs read-only `PRAGMA integrity_check` on SQLite database bytes."""
    if len(raw) < 100 or raw[:16] != SQLITE_MAGIC:
        return [f"{path}: invalid SQLite header"]
    fd, tmp_path = tempfile.mkstemp(prefix="verify_sqlite_", suffix=".db")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(raw)
        conn = sqlite3.connect(f"file:{tmp_path}?mode=ro", uri=True)
        try:
            rows = conn.execute("PRAGMA integrity_check;").fetchall()
        finally:
            conn.close()
        if rows != [("ok",)]:
            return [f"{path}: integrity_check failed ({rows[:3]})"]
        return []
    except Exception as exc:
        return [f"{path}: sqlite error ({exc})"]
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


def validate_h2_bytes(raw: bytes, path: str) -> list:
    """Validates dual H2 MVStore file headers at byte 0 and byte 4096."""
    if len(raw) < 8192:
        return [f"{path}: H2 database too small ({len(raw)} bytes)"]
    if raw[:4] != H2_MAGIC or raw[4096:4100] != H2_MAGIC:
        return [f"{path}: invalid H2 dual header signature"]
    return []


def validate_json_bytes(raw: bytes, path: str) -> list:
    """Validates UTF-8 JSON syntax."""
    try:
        json.loads(raw.decode("utf-8"))
        return []
    except Exception as exc:
        return [f"{path}: invalid JSON ({exc})"]


def validate_utf8_text(raw: bytes, path: str) -> list:
    """Validates UTF-8 decoding and checks for NUL byte corruption."""
    if b"\x00" in raw:
        return [f"{path}: unexpected NUL bytes"]
    try:
        raw.decode("utf-8")
        return []
    except UnicodeDecodeError as exc:
        return [f"{path}: invalid UTF-8 ({exc})"]


def run_cmd(cmd: list, timeout: int = 300) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=timeout)


def run_restic_check(container: str, subset: str) -> tuple:
    cmd = ["docker", "exec", container, "restic", "check"]
    if subset == "100%":
        cmd.append("--read-data")
    elif subset and subset.lower() != "none":
        cmd.append(f"--read-data-subset={subset}")
    res = run_cmd(cmd, timeout=900)
    if res.returncode != 0:
        msg = (res.stderr or res.stdout).strip().splitlines()[-3:]
        return False, " | ".join(msg) or f"restic check exited {res.returncode}"
    return True, "ok"


def stream_and_validate_content(container: str, snapshot_id: str) -> dict:
    """Streams `/data` tar from restic into memory and validates world/plugin files without disk extraction."""
    stats = {
        "level_dat_ok": False,
        "mca_files_checked": 0,
        "chunks_checked": 0,
        "corrupt_mca_files": 0,
        "corrupt_chunks": 0,
        "sqlite_dbs_checked": 0,
        "sqlite_dbs_failed": 0,
        "h2_dbs_checked": 0,
        "h2_dbs_failed": 0,
        "json_files_checked": 0,
        "json_files_failed": 0,
        "errors": [],
        "warnings": [],
    }
    saw_level_dat = False
    saw_ops_json = False

    cmd = ["docker", "exec", container, "restic", "dump", "--archive", "tar", snapshot_id, "/data"]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    assert proc.stdout is not None and proc.stderr is not None

    try:
        with tarfile.open(fileobj=proc.stdout, mode="r|") as tf:
            for member in tf:
                if not member.isfile():
                    continue
                rel = member.name.lstrip("./")
                low = rel.lower()

                is_level = rel in ("data/world/level.dat", "data/world/level.dat_old")
                is_mca = low.startswith("data/world/") and low.endswith(".mca")
                is_mv_db = low.endswith(".mv.db") and member.size <= MAX_DB_STREAM_BYTES
                is_db = low.endswith((".db", ".sqlite", ".sqlite3")) and not is_mv_db and member.size <= MAX_DB_STREAM_BYTES
                is_json = low in CRITICAL_JSON_PATHS
                is_text = low in PLUGIN_TEXT_PATHS

                if not (is_level or is_mca or is_mv_db or is_db or is_json or is_text):
                    continue

                fobj = tf.extractfile(member)
                if fobj is None:
                    continue
                raw = fobj.read()

                if is_level:
                    errs = validate_level_dat(raw, rel)
                    if rel == "data/world/level.dat":
                        saw_level_dat = True
                        stats["level_dat_ok"] = len(errs) == 0
                    stats["errors"].extend(errs)
                elif is_mca:
                    stats["mca_files_checked"] += 1
                    chunks, bad_chunks, errs = validate_mca(raw, rel)
                    stats["chunks_checked"] += chunks
                    stats["corrupt_chunks"] += bad_chunks
                    if errs:
                        stats["corrupt_mca_files"] += 1
                        stats["errors"].extend(errs)
                elif is_mv_db:
                    stats["h2_dbs_checked"] += 1
                    errs = validate_h2_bytes(raw, rel)
                    if errs:
                        stats["h2_dbs_failed"] += 1
                        stats["errors"].extend(errs)
                elif is_db:
                    if low.endswith((".sqlite", ".sqlite3")) or raw[:16] == SQLITE_MAGIC:
                        stats["sqlite_dbs_checked"] += 1
                        errs = validate_sqlite_bytes(raw, rel)
                        if errs:
                            stats["sqlite_dbs_failed"] += 1
                            stats["errors"].extend(errs)
                elif is_json:
                    if rel == "data/ops.json":
                        saw_ops_json = True
                    stats["json_files_checked"] += 1
                    errs = validate_json_bytes(raw, rel)
                    if errs:
                        stats["json_files_failed"] += 1
                        stats["errors"].extend(errs)
                elif is_text:
                    stats["errors"].extend(validate_utf8_text(raw, rel))
    finally:
        proc.stdout.close()
        stderr_out = proc.stderr.read().decode("utf-8", errors="replace").strip()
        proc.stderr.close()
        rc = proc.wait()

    if rc != 0:
        stats["errors"].append(f"restic dump exited {rc}: {stderr_out[:300]}")
    if not saw_level_dat:
        stats["errors"].append("Missing data/world/level.dat in snapshot")
    if not saw_ops_json:
        stats["errors"].append("Missing data/ops.json in snapshot")
    if stats["mca_files_checked"] == 0:
        stats["errors"].append("No .mca region files found under data/world/ in snapshot")

    return stats


def write_report(path: str, report: dict) -> None:
    if not path:
        return
    parent = os.path.dirname(os.path.abspath(path))
    if parent:
        os.makedirs(parent, exist_ok=True)
    tmp_path = path + ".tmp"
    with open(tmp_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    os.replace(tmp_path, path)


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify Minecraft restic backup consistency.")
    parser.add_argument("--container", default="mc-backup", help="Backup container name (default: mc-backup)")
    parser.add_argument("--subset", default="10%", help="restic check --read-data-subset value, '100%%', or 'none'")
    parser.add_argument("--max-age-hours", type=float, default=26.0, help="Maximum allowed age of latest snapshot")
    parser.add_argument("--max-drop-percent", type=float, default=25.0, help="Maximum allowed byte drop between snapshots")
    parser.add_argument("--report", default="", help="Optional atomic JSON report output path")
    parser.add_argument("--skip-rcon-check", action="store_true", help="Skip RCON save-off/save-all log check")
    parser.add_argument("--skip-content-check", action="store_true", help="Skip streaming tar validation")
    args = parser.parse_args()

    started = time.time()
    errors = []
    warnings = []
    report = {
        "ok": False,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "epoch": round(started, 3),
        "duration_sec": 0.0,
        "snapshot_id": "",
        "snapshot_time": "",
        "snapshot_epoch": 0.0,
        "snapshot_age_hours": -1.0,
        "snapshot_bytes": 0,
        "snapshot_files": 0,
        "restic_check_ok": False,
        "read_data_subset": args.subset,
        "rcon_consistent": "skipped",
        "level_dat_ok": False,
        "mca_files_checked": 0,
        "chunks_checked": 0,
        "corrupt_mca_files": 0,
        "corrupt_chunks": 0,
        "sqlite_dbs_checked": 0,
        "sqlite_dbs_failed": 0,
        "h2_dbs_checked": 0,
        "h2_dbs_failed": 0,
        "json_files_checked": 0,
        "json_files_failed": 0,
        "errors": errors,
        "warnings": warnings,
    }

    try:
        # 1. Inspect snapshots
        snap_res = run_cmd(["docker", "exec", args.container, "restic", "snapshots", "--json"], timeout=120)
        if snap_res.returncode != 0:
            raise RuntimeError(f"Failed to list restic snapshots: {snap_res.stderr.strip()}")
        snapshots = json.loads(snap_res.stdout or "[]")
        meta, snap_errs, snap_warns = evaluate_snapshots(
            snapshots, started, args.max_age_hours, args.max_drop_percent
        )
        report.update(meta)
        errors.extend(snap_errs)
        warnings.extend(snap_warns)

        # 2. Check repository integrity
        restic_ok, restic_msg = run_restic_check(args.container, args.subset)
        report["restic_check_ok"] = restic_ok
        if not restic_ok:
            errors.append(f"restic check failed: {restic_msg}")

        # 3. Check RCON save-off/save-all/save-on logs
        if not args.skip_rcon_check and report["snapshot_id"]:
            log_res = run_cmd(["docker", "logs", "--tail", "300", args.container], timeout=30)
            combined_logs = (log_res.stdout or "") + "\n" + (log_res.stderr or "")
            status, rcon_errs, rcon_warns = parse_rcon_consistency(combined_logs, report["snapshot_id"])
            report["rcon_consistent"] = status
            errors.extend(rcon_errs)
            warnings.extend(rcon_warns)

        # 4. Zero-disk streaming content validation
        if not args.skip_content_check and report["snapshot_id"]:
            content_stats = stream_and_validate_content(args.container, report["snapshot_id"])
            c_errs = content_stats.pop("errors")
            c_warns = content_stats.pop("warnings")
            report.update(content_stats)
            errors.extend(c_errs)
            warnings.extend(c_warns)

        report["ok"] = len(errors) == 0
    except Exception as exc:
        errors.append(f"Verification aborted: {exc}")
        report["ok"] = False

    report["duration_sec"] = round(time.time() - started, 2)
    write_report(args.report, report)

    status_label = "OK" if report["ok"] else "FAILED"
    print(
        f"[verify-backup] {status_label} | snapshot={report['snapshot_id'] or '-'} "
        f"age={report['snapshot_age_hours']}h restic={report['restic_check_ok']} "
        f"rcon={report['rcon_consistent']} level_dat={report['level_dat_ok']} "
        f"mca={report['mca_files_checked']} chunks={report['chunks_checked']} "
        f"corrupt_chunks={report['corrupt_chunks']} sqlite={report['sqlite_dbs_checked']} "
        f"h2={report['h2_dbs_checked']} json={report['json_files_checked']} "
        f"errors={len(errors)} warnings={len(warnings)} ({report['duration_sec']}s)"
    )
    for w in warnings:
        print(f"  WARN: {w}", file=sys.stderr)
    for e in errors:
        print(f"  ERROR: {e}", file=sys.stderr)

    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())

