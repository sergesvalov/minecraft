#!/usr/bin/env python3
"""Unit tests for pure backup validation functions in verify_backup.py."""

import gzip
import json
import os
import sqlite3
import struct
import sys
import tempfile
import unittest
import zlib
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import verify_backup as vb


def build_mca_bytes(chunks: dict) -> bytes:
    """Builds a synthetic Anvil .mca region file from `{index: (comp_type, compressed_bytes)}`."""
    header = bytearray(8192)
    body = bytearray()
    next_sector = 2

    for idx, (comp, payload) in sorted(chunks.items()):
        total_len = 1 + len(payload)
        raw_entry = struct.pack(">IB", total_len, comp) + payload
        padded_len = ((len(raw_entry) + 4095) // 4096) * 4096
        sectors = padded_len // 4096
        struct.pack_into(">I", header, idx * 4, (next_sector << 8) | sectors)
        body.extend(raw_entry)
        body.extend(b"\x00" * (padded_len - len(raw_entry)))
        next_sector += sectors

    return bytes(header) + bytes(body)


class VerifyBackupTests(unittest.TestCase):

    def test_validate_level_dat(self):
        good_nbt = b"\x0A\x00\x00" + b"\x0A\x00\x04Data" + b"\x08\x00\x09LevelName\x00\x05world\x00\x00"
        good_gz = gzip.compress(good_nbt)
        self.assertEqual([], vb.validate_level_dat(good_gz, "world/level.dat"))

        self.assertTrue(vb.validate_level_dat(b"", "world/level.dat"))
        self.assertTrue(vb.validate_level_dat(b"not-gzip", "world/level.dat"))
        self.assertTrue(vb.validate_level_dat(gzip.compress(b"\x08\x00\x04Data"), "world/level.dat"))
        self.assertTrue(vb.validate_level_dat(gzip.compress(b"\x0A\x00\x00"), "world/level.dat"))

    def test_validate_mca(self):
        self.assertEqual((0, 0, []), vb.validate_mca(b"", "r.0.0.mca"))
        chunks_trunc, bad_trunc, errs_trunc = vb.validate_mca(b"\x00" * 100, "r.0.0.mca")
        self.assertEqual((chunks_trunc, bad_trunc), (0, 1))
        self.assertTrue(errs_trunc)

        nbt = b"\x0A\x00\x00\x03\x00\x04Data\x00\x00\x00\x01\x00"
        good_mca = build_mca_bytes({
            0: (2, zlib.compress(nbt)),
            5: (1, gzip.compress(nbt)),
        })
        self.assertEqual((2, 0, []), vb.validate_mca(good_mca, "r.0.0.mca"))

        # Paper/Moonrise unpadded final sector must pass when full compressed payload is present
        gz_payload = gzip.compress(nbt)
        actual_end = 3 * 4096 + 5 + len(gz_payload)
        unpadded_mca = good_mca[:actual_end]
        self.assertLess(len(unpadded_mca), len(good_mca))
        self.assertEqual((2, 0, []), vb.validate_mca(unpadded_mca, "r.0.0.mca"))
        # Removing 1 real byte of the final chunk must fail
        self.assertEqual(1, vb.validate_mca(unpadded_mca[:-1], "r.0.0.mca")[1])

        corrupt_zlib = build_mca_bytes({0: (2, b"corrupt-zlib-data")})
        checked, bad, errs = vb.validate_mca(corrupt_zlib, "r.0.0.mca")
        self.assertEqual((checked, bad), (1, 1))
        self.assertTrue(errs)

        wrong_root = build_mca_bytes({3: (2, zlib.compress(b"\x01\x00\x00"))})
        checked, bad, errs = vb.validate_mca(wrong_root, "r.0.0.mca")
        self.assertEqual((checked, bad), (1, 1))
        self.assertTrue(errs)

        # Out-of-bounds sector location
        bad_header = bytearray(8192)
        struct.pack_into(">I", bad_header, 0, (10 << 8) | 1)
        checked, bad, errs = vb.validate_mca(bytes(bad_header), "r.0.0.mca")
        self.assertEqual((checked, bad), (1, 1))
        self.assertTrue(errs)

    def test_validate_sqlite_bytes(self):
        fd, path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        try:
            conn = sqlite3.connect(path)
            conn.execute("CREATE TABLE users(id INTEGER PRIMARY KEY, name TEXT);")
            conn.execute("INSERT INTO users(name) VALUES ('papa');")
            conn.commit()
            conn.close()
            with open(path, "rb") as f:
                raw = f.read()
        finally:
            os.unlink(path)

        self.assertEqual([], vb.validate_sqlite_bytes(raw, "authme.db"))

        corrupt = bytearray(raw)
        corrupt[100:] = b"\xff" * (len(corrupt) - 100)
        self.assertTrue(vb.validate_sqlite_bytes(bytes(corrupt), "authme.db"))
        self.assertTrue(vb.validate_sqlite_bytes(b"bad", "authme.db"))

    def test_validate_h2_bytes(self):
        good = bytearray(8192)
        good[0:16] = b"H:2,block:6,test"
        good[4096:4112] = b"H:2,block:6,test"
        self.assertEqual([], vb.validate_h2_bytes(bytes(good), "lp.mv.db"))

        bad = bytearray(good)
        bad[4096:4100] = b"XXXX"
        self.assertTrue(vb.validate_h2_bytes(bytes(bad), "lp.mv.db"))
        self.assertTrue(vb.validate_h2_bytes(b"H:2,short", "lp.mv.db"))

    def test_parse_rcon_consistency(self):
        ok_log = """
2026-11-21T16:04:10+0200 INFO Command executed successfully rcon-cli save-off
2026-11-21T16:04:10+0200 INFO Command executed successfully rcon-cli save-all flush
2026-11-21T16:04:11+0200 INFO Backing up content in /data
2026-11-21T16:04:47+0200 INFO snapshot 33aa4014 saved
2026-11-21T16:04:47+0200 INFO Command executed successfully rcon-cli save-on
"""
        status, errs, warns = vb.parse_rcon_consistency(ok_log, "33aa4014")
        self.assertEqual(status, "ok")
        self.assertEqual(errs, [])
        self.assertEqual(warns, [])

        missing_off = ok_log.replace("rcon-cli save-off", "rcon-cli list")
        status, errs, _ = vb.parse_rcon_consistency(missing_off, "33aa4014")
        self.assertEqual(status, "error")
        self.assertTrue(errs)

        status, errs, warns = vb.parse_rcon_consistency("unrelated logs\n", "33aa4014")
        self.assertEqual(status, "not_in_logs")
        self.assertEqual(errs, [])
        self.assertTrue(warns)

    def test_evaluate_snapshots(self):
        now = datetime(2026, 11, 21, 18, 0, 0, tzinfo=timezone.utc).timestamp()
        snaps = [
            {
                "short_id": "ed32e983",
                "time": "2026-11-20T16:04:07.428729231+02:00",
                "summary": {"total_files_processed": 1627, "total_bytes_processed": 1000000000},
            },
            {
                "short_id": "33aa4014",
                "time": "2026-11-21T16:04:12.122928338+02:00",
                "summary": {"total_files_processed": 1627, "total_bytes_processed": 980000000},
            },
        ]
        meta, errs, _ = vb.evaluate_snapshots(snaps, now, 26.0, 25.0)
        self.assertEqual(errs, [])
        self.assertEqual(meta["snapshot_id"], "33aa4014")

        # Stale snapshot (> 26h)
        stale_now = now + 30 * 3600
        _, stale_errs, _ = vb.evaluate_snapshots(snaps, stale_now, 26.0, 25.0)
        self.assertTrue(stale_errs)

        # Sharp byte drop (> 25%)
        snaps_drop = [
            snaps[0],
            {
                "short_id": "bad00001",
                "time": "2026-11-21T16:04:12.122928338+02:00",
                "summary": {"total_files_processed": 1627, "total_bytes_processed": 500000000},
            },
        ]
        _, drop_errs, _ = vb.evaluate_snapshots(snaps_drop, now, 26.0, 25.0)
        self.assertTrue(drop_errs)

    def test_validate_json_and_utf8(self):
        self.assertEqual([], vb.validate_json_bytes(json.dumps([{"name": "papa"}]).encode(), "ops.json"))
        self.assertTrue(vb.validate_json_bytes(b"{broken", "ops.json"))
        self.assertEqual([], vb.validate_utf8_text("svetitskhoveli,world,1,2,3\n".encode(), "buildings.txt"))
        self.assertTrue(vb.validate_utf8_text(b"bad\x00line", "buildings.txt"))


if __name__ == "__main__":
    unittest.main()

