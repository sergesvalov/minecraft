"""Unit tests for modularized Warden Monitor components."""

import json
import os
import tempfile
import unittest

import metrics
from backup_watcher import BackupWatcher
from dashboard import render_dashboard, render_tg_page
from events import process_event


class MonitorTests(unittest.TestCase):

    def test_process_event_updates_counters_and_notifies(self):
        sent = []
        tnt_before = metrics.TNT_PLACED.labels(
            player="alex", world="world", x="1", y="64", z="2", time="t1"
        )._value.get()
        process_event(
            json.dumps({"event": "place_tnt", "player": "alex", "world": "world", "x": 1, "y": 64, "z": 2, "timestamp": "t1"}),
            notify=sent.append,
        )
        tnt_after = metrics.TNT_PLACED.labels(
            player="alex", world="world", x="1", y="64", z="2", time="t1"
        )._value.get()
        self.assertEqual(tnt_after - tnt_before, 1.0)
        self.assertEqual(len(sent), 1)
        self.assertIn("alex", sent[0])

        process_event(json.dumps({"event": "player_join", "player": "papa", "timestamp": "t2"}), notify=sent.append)
        process_event(
            json.dumps({"event": "grief_alert", "player": "griefer", "blocks_broken": 50, "x": 5, "y": 70, "z": 6, "timestamp": "t3"}),
            notify=sent.append,
        )
        process_event(
            json.dumps({"event": "suspicious_command", "player": "bob", "command": "/op bob", "timestamp": "t4"}),
            notify=sent.append,
        )
        process_event("{invalid-json", notify=sent.append)
        self.assertEqual(len(sent), 4)

    def test_backup_watcher_alerts_and_recovery(self):
        sent = []
        watcher = BackupWatcher(stale_hours=28.0)

        # Fail report triggers alert once per epoch
        fail = {"ok": False, "epoch": 100.0, "snapshot_epoch": 90.0, "snapshot_bytes": 1000, "corrupt_chunks": 2, "errors": ["bad chunk"], "snapshot_id": "aaa111"}
        watcher.step(fail, now_ts=200.0, notify=sent.append)
        watcher.step(fail, now_ts=260.0, notify=sent.append)
        self.assertEqual(len(sent), 1)
        self.assertIn("ОШИБКА БЭКАПА", sent[0])
        self.assertEqual(metrics.BACKUP_VERIFY_OK._value.get(), 0.0)
        self.assertEqual(metrics.BACKUP_CORRUPT_CHUNKS._value.get(), 2.0)

        # Recovery report triggers recovery notice
        ok_rep = {"ok": True, "epoch": 300.0, "snapshot_epoch": 290.0, "snapshot_bytes": 2000, "corrupt_chunks": 0, "errors": [], "snapshot_id": "bbb222"}
        watcher.step(ok_rep, now_ts=350.0, notify=sent.append)
        self.assertEqual(len(sent), 2)
        self.assertIn("восстановлена", sent[1])
        self.assertEqual(metrics.BACKUP_VERIFY_OK._value.get(), 1.0)

        # Stale snapshot triggers alert once, then resets on newer snapshot
        watcher.step(ok_rep, now_ts=290.0 + 29 * 3600, notify=sent.append)
        watcher.step(ok_rep, now_ts=290.0 + 30 * 3600, notify=sent.append)
        self.assertEqual(len(sent), 3)
        self.assertIn("устарел", sent[2])

        fresh = {"ok": True, "epoch": 200000.0, "snapshot_epoch": 199000.0, "snapshot_bytes": 2000, "corrupt_chunks": 0, "errors": [], "snapshot_id": "ccc333"}
        watcher.step(fresh, now_ts=199500.0, notify=sent.append)
        self.assertFalse(watcher.stale_alerted)

    def test_dashboard_escapes_html_and_renders_backup_cards(self):
        with tempfile.TemporaryDirectory() as tmp:
            log_path = os.path.join(tmp, "events.jsonl")
            rep_path = os.path.join(tmp, "backup_verify.json")

            with open(log_path, "w", encoding="utf-8") as f:
                f.write(json.dumps({
                    "timestamp": "2026-11-21T12:00:00Z",
                    "event": "suspicious_command",
                    "player": "<script>alert(1)</script>",
                    "command": "/op <img onerror=x>",
                }) + "\n")

            # Missing report -> —
            html_none = render_dashboard(log_path, rep_path, now_ts=1000.0)
            self.assertNotIn("<script>alert(1)</script>", html_none)
            self.assertIn("&lt;script&gt;alert(1)&lt;/script&gt;", html_none)
            self.assertIn("&lt;img onerror=x&gt;", html_none)
            self.assertIn(">—<", html_none)

            # OK fresh -> ✅ OK
            with open(rep_path, "w", encoding="utf-8") as f:
                json.dump({"ok": True, "snapshot_epoch": 1000.0}, f)
            self.assertIn("✅ OK (2ч)", render_dashboard(log_path, rep_path, now_ts=1000.0 + 7200))

            # Stale -> ⚠️
            self.assertIn("⚠️ 30ч", render_dashboard(log_path, rep_path, now_ts=1000.0 + 30 * 3600))

            # Error -> ❌ Ошибка
            with open(rep_path, "w", encoding="utf-8") as f:
                json.dump({"ok": False, "errors": ["e1", "e2"]}, f)
            self.assertIn("❌ Ошибка (2)", render_dashboard(log_path, rep_path, now_ts=1000.0))

            tg_html = render_tg_page('tok" onclick="x', '<chat>')
            self.assertIn("tok&quot; onclick=&quot;x", tg_html)
            self.assertIn("&lt;chat&gt;", tg_html)


if __name__ == "__main__":
    unittest.main()

