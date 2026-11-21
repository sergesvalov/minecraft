"""HTML views and XSS-safe rendering for Telegram settings and live dashboard."""

import collections
import html
import json
import os
import time

import backup_watcher
import config
import metrics

HTML_PAGE = '''
<!DOCTYPE html>
<html>
<head>
    <title>Warden Monitor - Telegram Settings</title>
    <meta charset="utf-8">
    <style>
        body {{ font-family: Arial, sans-serif; background: #121212; color: #fff; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }}
        .card {{ background: #1e1e1e; padding: 30px; border-radius: 10px; box-shadow: 0 4px 15px rgba(0,0,0,0.5); width: 400px; }}
        h2 {{ margin-top: 0; color: #0088cc; }}
        label {{ display: block; margin: 15px 0 5px; font-weight: bold; }}
        input[type="text"] {{ width: 100%; padding: 10px; border: 1px solid #333; border-radius: 5px; background: #2a2a2a; color: #fff; box-sizing: border-box; }}
        button {{ margin-top: 20px; width: 100%; padding: 12px; border: none; border-radius: 5px; background: #0088cc; color: white; font-size: 16px; cursor: pointer; transition: 0.3s; }}
        button:hover {{ background: #006699; }}
        .success {{ color: #4caf50; margin-top: 15px; font-weight: bold; display: none; }}
    </style>
</head>
<body>
    <div class="card">
        <h2>Настройки Telegram Bot</h2>
        <p>Введите данные бота для получения уведомлений (TNT, входы игроков, статус сервера).</p>
        <form method="POST" action="/tg">
            <label>Bot Token</label>
            <input type="text" name="bot_token" placeholder="Например: 123456789:ABCdefGHI..." value="{bot_token}" required>
            
            <label>Chat ID</label>
            <input type="text" name="chat_id" placeholder="Например: -10012345678" value="{chat_id}" required>
            
            <button type="submit">Сохранить и протестировать</button>
            {message}
        </form>
    </div>
</body>
</html>'''

DASHBOARD_HTML = '''<!DOCTYPE html>
<html>
<head>
    <title>Warden Monitor - Dashboard</title>
    <meta charset="utf-8">
    <meta http-equiv="refresh" content="30">
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: 'Segoe UI', Arial, sans-serif; background: #0d1117; color: #c9d1d9; padding: 20px; }}
        h1 {{ color: #58a6ff; margin-bottom: 5px; font-size: 24px; }}
        .sub {{ color: #8b949e; margin-bottom: 20px; font-size: 13px; }}
        .cards {{ display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }}
        .card {{ background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 12px 18px; min-width: 130px; }}
        .card .l {{ color: #8b949e; font-size: 11px; text-transform: uppercase; }}
        .card .v {{ font-size: 22px; font-weight: bold; color: #58a6ff; margin-top: 4px; }}
        table {{ width: 100%; border-collapse: collapse; background: #161b22; border-radius: 8px; overflow: hidden; }}
        th {{ background: #21262d; color: #8b949e; padding: 8px 12px; text-align: left; font-size: 11px; text-transform: uppercase; }}
        td {{ padding: 6px 12px; border-top: 1px solid #21262d; font-size: 13px; }}
        tr:hover {{ background: #1c2128; }}
        .e-tnt {{ color: #f85149; }}
        .e-join {{ color: #3fb950; }}
        .e-quit {{ color: #8b949e; }}
        .e-grief {{ color: #f85149; font-weight: bold; }}
        .e-val {{ color: #d2a8ff; }}
        .e-mob {{ color: #f0883e; }}
        .e-cmd {{ color: #ffa657; }}
        .e-kill {{ color: #ff7b72; }}
        .e-lava {{ color: #f0883e; }}
        .e-crys {{ color: #bc8cff; }}
        .e-fail {{ color: #f85149; }}
        .e-ent {{ color: #ffa657; }}
        .e-lag {{ color: #f85149; font-weight: bold; }}
        .tps-good {{ color: #3fb950; }}
        .tps-warn {{ color: #d29922; }}
        .tps-bad {{ color: #f85149; }}
        .empty {{ text-align: center; padding: 40px; color: #8b949e; }}
    </style>
</head>
<body>
    <h1>📊 Warden Monitor</h1>
    <p class="sub">Автообновление каждые 30 сек · Последние 100 событий</p>
    <div class="cards">
        <div class="card"><div class="l">Статус</div><div class="v">{status}</div></div>
        <div class="card"><div class="l">Игроки</div><div class="v">{players}</div></div>
        <div class="card"><div class="l">TPS</div><div class="v {tps_class}">{tps}</div></div>
        <div class="card"><div class="l">Пинг</div><div class="v">{ping} ms</div></div>
        <div class="card"><div class="l">Бэкап</div><div class="v {backup_class}">{backup}</div></div>
    </div>
    <table>
        <thead><tr><th>Время</th><th>Событие</th><th>Игрок</th><th>Детали</th></tr></thead>
        <tbody>{rows}</tbody>
    </table>
</body>
</html>'''

EVENT_DISPLAY = {
    "place_tnt": ("🧨", "e-tnt", "TNT установлен"),
    "explode_tnt": ("💥", "e-tnt", "TNT взорван"),
    "player_join": ("✅", "e-join", "Вход"),
    "player_quit": ("🚪", "e-quit", "Выход"),
    "break_valuable": ("💎", "e-val", "Ценный блок"),
    "grief_alert": ("🚨", "e-grief", "ГРИФ"),
    "dangerous_mob_spawn": ("🧟", "e-mob", "Опасный моб"),
    "place_lava": ("🌋", "e-lava", "Лава"),
    "place_crystal": ("🔮", "e-crys", "Кристалл"),
    "explode_crystal": ("💥", "e-crys", "Взрыв кристалла"),
    "suspicious_command": ("⚠️", "e-cmd", "Команда"),
    "player_kill": ("⚔️", "e-kill", "PvP"),
    "failed_login": ("❌", "e-fail", "Неверный пароль"),
    "high_entity_count": ("🐄", "e-ent", "Много мобов"),
    "lag_machine": ("🤖", "e-lag", "Лаг-машина"),
}


def render_tg_page(bot_token: str, chat_id: str, message: str = "") -> str:
    return HTML_PAGE.format(
        bot_token=html.escape(bot_token or "", quote=True),
        chat_id=html.escape(chat_id or "", quote=True),
        message=message,
    )


def _load_recent_events(log_file: str) -> list:
    events = []
    log_dir = os.path.dirname(log_file)
    try:
        archives = sorted(
            (n for n in os.listdir(log_dir) if n.startswith("events-") and n.endswith(".jsonl")),
            reverse=True,
        )
    except OSError:
        archives = []
    sources = [log_file] + [os.path.join(log_dir, n) for n in archives[:1]]
    for path in sources:
        if len(events) >= 100 or not os.path.exists(path):
            continue
        try:
            with open(path, "r", encoding="utf-8") as f:
                lines = collections.deque(f, maxlen=100 - len(events))
            for line in reversed(list(lines)):
                stripped = line.strip()
                if stripped:
                    try:
                        events.append(json.loads(stripped))
                    except json.JSONDecodeError:
                        pass
        except Exception:
            pass
    return events


def _backup_card(report, now_ts: float) -> tuple:
    if report is None:
        return "—", ""
    if not report.get("ok", False):
        return f"❌ Ошибка ({len(report.get('errors') or [])})", "tps-bad"
    snap_epoch = float(report.get("snapshot_epoch") or 0)
    age_h = (
        max(0.0, (now_ts - snap_epoch) / 3600.0)
        if snap_epoch > 0
        else float(report.get("snapshot_age_hours") or 0)
    )
    if age_h > config.BACKUP_STALE_HOURS:
        return f"⚠️ {age_h:.0f}ч", "tps-warn"
    return f"✅ OK ({age_h:.0f}ч)", "tps-good"


def render_dashboard(log_file: str = None, backup_report_file: str = None, now_ts: float = None) -> str:
    events = _load_recent_events(log_file or config.LOG_FILE)
    rows = []
    if not events:
        rows.append('<tr><td colspan="4" class="empty">Нет событий</td></tr>')
    else:
        for ev in events:
            etype = str(ev.get("event", "unknown"))
            emoji, css, label = EVENT_DISPLAY.get(etype, ("❓", "", etype))
            ts = str(ev.get("timestamp", ""))
            if "T" in ts:
                time_part = ts.split("T", 1)[1]
                ts = time_part.split(".")[0] if "." in time_part else time_part.replace("Z", "")
            player = str(ev.get("player", ev.get("killer", "—")))
            parts = []
            if "block" in ev:
                parts.append(str(ev["block"]).replace("_", " ").title())
            if "command" in ev:
                parts.append(str(ev["command"]))
            if "mob" in ev:
                parts.append(str(ev["mob"]))
            if etype == "player_kill" and "victim" in ev:
                parts.append(f'→ {ev["victim"]}')
            if etype == "explode_tnt" and "source" in ev:
                parts.append(f'от {ev["source"]}')
            if "blocks_broken" in ev:
                parts.append(f'{ev["blocks_broken"]} бл/10с')
            if "count" in ev:
                parts.append(f'{ev["count"]} шт')
            if "x" in ev:
                parts.append(f'{ev["x"]},{ev["y"]},{ev["z"]}' if "y" in ev else f'{ev["x"]},{ev["z"]}')
            if "world" in ev:
                parts.append(str(ev["world"]))
            detail = " · ".join(parts) if parts else "—"
            rows.append(
                f'<tr><td>{html.escape(ts)}</td>'
                f'<td class="{html.escape(css, quote=True)}">{emoji} {html.escape(label)}</td>'
                f'<td>{html.escape(player)}</td>'
                f'<td>{html.escape(detail)}</td></tr>'
            )

    try:
        s = metrics.MC_ONLINE._value.get()
    except Exception:
        s = 0
    try:
        p = int(metrics.MC_PLAYERS._value.get())
    except Exception:
        p = 0
    try:
        t = metrics.MC_TPS._value.get()
    except Exception:
        t = 0
    try:
        pg = int(metrics.MC_PING._value.get())
    except Exception:
        pg = 0

    now = time.time() if now_ts is None else now_ts
    report = backup_watcher.read_backup_report(backup_report_file or config.BACKUP_REPORT_FILE)
    backup_text, backup_cls = _backup_card(report, now)

    return DASHBOARD_HTML.format(
        status="🟢 Online" if s == 1 else "🔴 Offline",
        players=p,
        tps=f"{t:.1f}" if t > 0 else "—",
        tps_class="tps-good" if t >= 18 else ("tps-warn" if t >= 15 else "tps-bad"),
        ping=pg,
        backup=backup_text,
        backup_class=backup_cls,
        rows="\n".join(rows),
    )

