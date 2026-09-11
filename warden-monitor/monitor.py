import collections
import os
import re
import time
import json
import logging
import threading
from datetime import datetime
from prometheus_client import start_http_server, Gauge, Counter
from mcstatus import JavaServer
from http.server import BaseHTTPRequestHandler, HTTPServer
import urllib.parse
import urllib.request
from mcrcon import MCRcon

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s | [WARDEN] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)

LOG_FILE = '/app/logs/events.jsonl'
TG_CONFIG_FILE = '/app/logs/tg_config.json'
MC_HOST = 'mc'
MC_PORT = 25565
PROMETHEUS_PORT = 8000
WEBHOOK_PORT = 8001
RCON_PASSWORD = 'admin'
RCON_PORT = 25575

# Prometheus Metrics
MC_ONLINE = Gauge('minecraft_server_online', 'Is the Minecraft server online (1) or down (0)')
MC_PLAYERS = Gauge('minecraft_player_count', 'Number of players currently online')
MC_PING = Gauge('minecraft_server_ping_ms', 'Server ping latency in milliseconds')

TNT_PLACED = Counter('warden_tnt_placed_total', 'Total TNT blocks placed', ['player', 'world', 'x', 'y', 'z', 'time'])
TNT_EXPLODED = Counter('warden_tnt_exploded_total', 'Total TNT explosions', ['source', 'world', 'x', 'y', 'z', 'time'])

PLAYER_JOINED = Counter('warden_player_joins_total', 'Total player joins', ['player', 'time'])
PLAYER_QUIT = Counter('warden_player_quits_total', 'Total player quits', ['player', 'time'])
DANGEROUS_MOB_SPAWN = Counter('warden_dangerous_mob_spawn_total', 'Total dangerous mobs spawned', ['mob', 'player', 'world', 'x', 'y', 'z', 'time'])

LAVA_PLACED = Counter('warden_lava_placed_total', 'Total lava blocks placed', ['player', 'world', 'x', 'y', 'z', 'time'])
CRYSTAL_PLACED = Counter('warden_crystal_placed_total', 'Total end crystals placed', ['player', 'world', 'x', 'y', 'z', 'time'])
SUSPICIOUS_COMMAND = Counter('warden_suspicious_command_total', 'Total suspicious commands executed', ['player', 'command', 'time'])
PLAYER_KILLS = Counter('warden_player_kills_total', 'Total PVP kills', ['killer', 'victim', 'time'])
FAILED_LOGINS = Counter('warden_failed_logins_total', 'Total failed login attempts', ['player', 'time'])

VALUABLE_BROKEN = Counter('warden_valuable_block_broken_total', 'Total valuable blocks broken', ['player', 'block', 'world', 'x', 'y', 'z', 'time'])
GRIEF_ALERTS = Counter('warden_grief_alerts_total', 'Total grief alerts triggered', ['player', 'blocks_broken', 'time'])

MC_TPS = Gauge('minecraft_server_tps', 'Server TPS from last 1 minute')

server = JavaServer.lookup(f"{MC_HOST}:{MC_PORT}")

def get_tg_config():
    if os.path.exists(TG_CONFIG_FILE):
        try:
            with open(TG_CONFIG_FILE, 'r') as f:
                return json.load(f)
        except:
            pass
    return {"bot_token": "", "chat_id": ""}

def save_tg_config(token, chat_id):
    with open(TG_CONFIG_FILE, 'w') as f:
        json.dump({"bot_token": token, "chat_id": chat_id}, f)

def send_telegram_message_sync(msg):
    config = get_tg_config()
    token = config.get("bot_token")
    chat_id = config.get("chat_id")
    if not token or not chat_id:
        return
    
    url = f"https://api.telegram.org/bot{token}/sendMessage"
    data = json.dumps({"chat_id": chat_id, "text": msg}).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
    
    try:
        urllib.request.urlopen(req, timeout=5)
    except Exception as e:
        logging.error(f"Failed to send Telegram message: {e}")

def send_telegram_message(msg):
    threading.Thread(target=send_telegram_message_sync, args=(msg,), daemon=True).start()

def liveness_monitor():
    """Background thread to continuously check if the Minecraft server is alive."""
    logging.info(f"Started liveness monitor for {MC_HOST}:{MC_PORT}")
    was_online = True
    while True:
        try:
            status = server.status()
            MC_ONLINE.set(1)
            MC_PLAYERS.set(status.players.online)
            MC_PING.set(status.latency)
            
            if not was_online:
                logging.info("✅ Server is back ONLINE!")
                send_telegram_message("✅ Сервер Minecraft запущен и доступен!")
                was_online = True
                
        except Exception as e:
            MC_ONLINE.set(0)
            MC_PLAYERS.set(0)
            MC_PING.set(0)
            if was_online:
                logging.error(f"❌ SERVER IS DOWN! Failed to ping {MC_HOST}:{MC_PORT}. Error: {e}")
                send_telegram_message(f"❌ СЕРВЕР НЕДОСТУПЕН! Произошла ошибка: {e}")
                was_online = False
                
        time.sleep(15) # Check every 15 seconds

def tps_monitor():
    """Background thread to poll server TPS via RCON."""
    logging.info("Started TPS monitor")
    time.sleep(30)  # Wait for server to start
    while True:
        try:
            with MCRcon(MC_HOST, RCON_PASSWORD, port=RCON_PORT) as mcr:
                response = mcr.command("tps")
                clean = re.sub(r'\xa7.', '', response)  # Strip Minecraft color codes
                numbers = re.findall(r'\d+\.\d+', clean)
                if numbers:
                    MC_TPS.set(float(numbers[0]))
        except Exception:
            pass  # Server might be down, liveness_monitor handles that
        time.sleep(30)

def tail(file_path):
    logged_wait = False
    while not os.path.exists(file_path):
        if not logged_wait:
            logging.info(f"Waiting for log file {file_path}...")
            logged_wait = True
        time.sleep(5)
    
    with open(file_path, 'r', encoding='utf-8') as f:
        f.seek(0, os.SEEK_END)
        logging.info(f"Connected to log file. Listening for TNT events...")
        
        while True:
            line = f.readline()
            if not line:
                time.sleep(0.5)
                f.seek(0, os.SEEK_CUR) # Сбрасываем флаг конца файла (EOF), чтобы Python увидел новые строки
                continue
            
            yield line.strip()

def process_event(event_line):
    try:
        data = json.loads(event_line)
        event_type = data.get('event')
        
        if event_type == 'place_tnt':
            player = data.get('player', 'Unknown')
            world = data.get('world', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            timestamp = data.get('timestamp', 'Unknown')
            TNT_PLACED.labels(player=player, world=world, x=str(x), y=str(y), z=str(z), time=timestamp).inc()
            logging.warning(f"🧨 TNT PLACED by {player} at {x},{y},{z}")
            send_telegram_message(f"🧨 ВНИМАНИЕ: Игрок {player} установил динамит (TNT) на координатах {x}, {y}, {z} в мире {world}!")
            
        elif event_type == 'explode_tnt':
            source = data.get('source', 'Unknown')
            world = data.get('world', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            timestamp = data.get('timestamp', 'Unknown')
            TNT_EXPLODED.labels(source=source, world=world, x=str(x), y=str(y), z=str(z), time=timestamp).inc()
            logging.error(f"💥 TNT EXPLODED (Source: {source}) at {x},{y},{z}")
            send_telegram_message(f"💥 ВЗРЫВ! Динамит взорвался на координатах {x}, {y}, {z} в мире {world} (Источник: {source})!")
            
        elif event_type == 'player_join':
            player = data.get('player', 'Unknown')
            timestamp = data.get('timestamp', 'Unknown')
            PLAYER_JOINED.labels(player=player, time=timestamp).inc()
            logging.info(f"✅ Player {player} joined")
            send_telegram_message(f"👋 Игрок {player} зашел на сервер")
            
        elif event_type == 'player_quit':
            player = data.get('player', 'Unknown')
            timestamp = data.get('timestamp', 'Unknown')
            PLAYER_QUIT.labels(player=player, time=timestamp).inc()
            logging.info(f"👋 Player {player} quit")
            send_telegram_message(f"🚪 Игрок {player} покинул сервер")
            
        elif event_type == 'dangerous_mob_spawn':
            mob = data.get('mob', 'Unknown')
            player = data.get('player', 'Unknown')
            world = data.get('world', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            timestamp = data.get('timestamp', 'Unknown')
            DANGEROUS_MOB_SPAWN.labels(mob=mob, player=player, world=world, x=str(x), y=str(y), z=str(z), time=timestamp).inc()
            logging.warning(f"🧟 DANGEROUS MOB SPAWNED: {mob} near {player} at {x},{y},{z}")
            send_telegram_message(f"⚠️ ВНИМАНИЕ: Опасный моб {mob} заспавнен (или появился рядом с {player}) на координатах {x}, {y}, {z} в мире {world}!")
            
        elif event_type == 'place_lava':
            player = data.get('player', 'Unknown')
            world = data.get('world', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            timestamp = data.get('timestamp', 'Unknown')
            LAVA_PLACED.labels(player=player, world=world, x=str(x), y=str(y), z=str(z), time=timestamp).inc()
            logging.warning(f"🌋 LAVA PLACED by {player} at {x},{y},{z}")
            send_telegram_message(f"🌋 Игрок {player} разлил лаву на координатах {x}, {y}, {z} в мире {world}!")
            
        elif event_type == 'place_crystal':
            player = data.get('player', 'Unknown')
            world = data.get('world', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            timestamp = data.get('timestamp', 'Unknown')
            CRYSTAL_PLACED.labels(player=player, world=world, x=str(x), y=str(y), z=str(z), time=timestamp).inc()
            logging.warning(f"🔮 CRYSTAL PLACED by {player} at {x},{y},{z}")
            send_telegram_message(f"🔮 Игрок {player} установил Кристалл Энда на координатах {x}, {y}, {z} в мире {world}!")
            
        elif event_type == 'explode_crystal':
            world = data.get('world', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            logging.warning(f"💥 CRYSTAL EXPLODED at {x},{y},{z}")
            send_telegram_message(f"💥 Взорвался Кристалл Энда на координатах {x}, {y}, {z} в мире {world}!")

        elif event_type == 'suspicious_command':
            player = data.get('player', 'Unknown')
            command = data.get('command', 'Unknown')
            timestamp = data.get('timestamp', 'Unknown')
            SUSPICIOUS_COMMAND.labels(player=player, command=command, time=timestamp).inc()
            logging.warning(f"⚠️ SUSPICIOUS COMMAND by {player}: {command}")
            send_telegram_message(f"⚠️ Подозрительная команда от {player}: `{command}`")
            
        elif event_type == 'player_kill':
            killer = data.get('killer', 'Unknown')
            victim = data.get('victim', 'Unknown')
            timestamp = data.get('timestamp', 'Unknown')
            PLAYER_KILLS.labels(killer=killer, victim=victim, time=timestamp).inc()
            logging.info(f"⚔️ {killer} killed {victim}")
            send_telegram_message(f"⚔️ Игрок {killer} убил {victim}!")
            
        elif event_type == 'failed_login':
            player = data.get('player', 'Unknown')
            timestamp = data.get('timestamp', 'Unknown')
            FAILED_LOGINS.labels(player=player, time=timestamp).inc()
            logging.warning(f"❌ Failed login attempt for {player}")
            send_telegram_message(f"❌ Неудачная попытка входа под ником {player} (Неверный пароль)!")
            
        elif event_type == 'high_entity_count':
            count = data.get('count', 0)
            world = data.get('world', 'Unknown')
            x, z = data.get('x'), data.get('z')
            logging.warning(f"🐄 HIGH ENTITY COUNT ({count}) in {world} at {x},{z}")
            send_telegram_message(f"🐄 Аномальное скопление мобов ({count} шт.) в чанке на координатах {x}, {z} в мире {world}!")
            
        elif event_type == 'lag_machine':
            world = data.get('world', 'Unknown')
            x, z = data.get('x'), data.get('z')
            logging.warning(f"🤖 LAG MACHINE DETECTED in {world} at {x},{z}")
            send_telegram_message(f"🤖 ОБНАРУЖЕНА ЛАГ-МАШИНА (Слишком много поршней) в чанке {x}, {z} в мире {world}!")

        elif event_type == 'break_valuable':
            player = data.get('player', 'Unknown')
            block = data.get('block', 'Unknown')
            world = data.get('world', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            timestamp = data.get('timestamp', 'Unknown')
            VALUABLE_BROKEN.labels(player=player, block=block, world=world, x=str(x), y=str(y), z=str(z), time=timestamp).inc()
            logging.info(f"💎 VALUABLE BLOCK {block} broken by {player} at {x},{y},{z}")

        elif event_type == 'grief_alert':
            player = data.get('player', 'Unknown')
            blocks_broken = data.get('blocks_broken', 0)
            timestamp = data.get('timestamp', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            GRIEF_ALERTS.labels(player=player, blocks_broken=str(blocks_broken), time=timestamp).inc()
            logging.warning(f"🚨 GRIEF ALERT: {player} broke {blocks_broken} blocks in 10 sec at {x},{y},{z}")
            send_telegram_message(f"🚨 ГРИФ АЛЕРТ! Игрок {player} сломал {blocks_broken} блоков за 10 секунд на координатах {x}, {y}, {z}!")

    except json.JSONDecodeError:
        pass
    except Exception as e:
        logging.error(f"Error processing event: {e}")

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
</html>
'''

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
    </div>
    <table>
        <thead><tr><th>Время</th><th>Событие</th><th>Игрок</th><th>Детали</th></tr></thead>
        <tbody>{rows}</tbody>
    </table>
</body>
</html>'''

EVENT_DISPLAY = {
    'place_tnt': ('🧨', 'e-tnt', 'TNT установлен'),
    'explode_tnt': ('💥', 'e-tnt', 'TNT взорван'),
    'player_join': ('✅', 'e-join', 'Вход'),
    'player_quit': ('🚪', 'e-quit', 'Выход'),
    'break_valuable': ('💎', 'e-val', 'Ценный блок'),
    'grief_alert': ('🚨', 'e-grief', 'ГРИФ'),
    'dangerous_mob_spawn': ('🧟', 'e-mob', 'Опасный моб'),
    'place_lava': ('🌋', 'e-lava', 'Лава'),
    'place_crystal': ('🔮', 'e-crys', 'Кристалл'),
    'explode_crystal': ('💥', 'e-crys', 'Взрыв кристалла'),
    'suspicious_command': ('⚠️', 'e-cmd', 'Команда'),
    'player_kill': ('⚔️', 'e-kill', 'PvP'),
    'failed_login': ('❌', 'e-fail', 'Неверный пароль'),
    'high_entity_count': ('🐄', 'e-ent', 'Много мобов'),
    'lag_machine': ('🤖', 'e-lag', 'Лаг-машина'),
}

def render_dashboard():
    events = []
    if os.path.exists(LOG_FILE):
        try:
            with open(LOG_FILE, 'r', encoding='utf-8') as f:
                lines = collections.deque(f, maxlen=100)
            for line in reversed(list(lines)):
                line = line.strip()
                if line:
                    try:
                        events.append(json.loads(line))
                    except json.JSONDecodeError:
                        pass
        except Exception:
            pass

    rows = []
    if not events:
        rows.append('<tr><td colspan="4" class="empty">Нет событий</td></tr>')
    else:
        for ev in events:
            etype = ev.get('event', 'unknown')
            emoji, css, label = EVENT_DISPLAY.get(etype, ('❓', '', etype))
            ts = ev.get('timestamp', '')
            if 'T' in ts:
                time_part = ts.split('T')[1]
                ts = time_part.split('.')[0] if '.' in time_part else time_part.replace('Z', '')
            player = ev.get('player', ev.get('killer', '—'))
            parts = []
            if 'block' in ev:
                parts.append(ev['block'].replace('_', ' ').title())
            if 'command' in ev:
                parts.append(ev['command'])
            if 'mob' in ev:
                parts.append(ev['mob'])
            if etype == 'player_kill' and 'victim' in ev:
                parts.append(f'→ {ev["victim"]}')
            if etype == 'explode_tnt' and 'source' in ev:
                parts.append(f'от {ev["source"]}')
            if 'blocks_broken' in ev:
                parts.append(f'{ev["blocks_broken"]} бл/10с')
            if 'count' in ev:
                parts.append(f'{ev["count"]} шт')
            if 'x' in ev:
                if 'y' in ev:
                    parts.append(f'{ev["x"]},{ev["y"]},{ev["z"]}')
                else:
                    parts.append(f'{ev["x"]},{ev["z"]}')
            if 'world' in ev:
                parts.append(ev['world'])
            detail = ' · '.join(parts) if parts else '—'
            rows.append(f'<tr><td>{ts}</td><td class="{css}">{emoji} {label}</td><td>{player}</td><td>{detail}</td></tr>')

    try:
        s = MC_ONLINE._value.get()
    except Exception:
        s = 0
    try:
        p = int(MC_PLAYERS._value.get())
    except Exception:
        p = 0
    try:
        t = MC_TPS._value.get()
    except Exception:
        t = 0
    try:
        pg = int(MC_PING._value.get())
    except Exception:
        pg = 0

    return DASHBOARD_HTML.format(
        status='🟢 Online' if s == 1 else '🔴 Offline',
        players=p,
        tps=f'{t:.1f}' if t > 0 else '—',
        tps_class='tps-good' if t >= 18 else ('tps-warn' if t >= 15 else 'tps-bad'),
        ping=pg,
        rows='\n'.join(rows)
    )

class WebhookHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == '/alert':
            qs = urllib.parse.parse_qs(parsed.query)
            msg = qs.get('msg', ['ВНИМАНИЕ! Сервер будет перезагружен через 5 минут!'])[0]
            
            try:
                with MCRcon(MC_HOST, RCON_PASSWORD, port=RCON_PORT) as mcr:
                    # Minecraft title json format
                    mcr.command(f'title @a title {{"text":"ВНИМАНИЕ","color":"red","bold":true}}')
                    mcr.command(f'title @a subtitle {{"text":"{msg}","color":"yellow"}}')
                    mcr.command(f'say [ALERT] {msg}')
                
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b'Alert broadcasted successfully!\n')
                logging.info(f"Broadcasted alert to server: {msg}")
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(f'Failed to connect to RCON: {e}\n'.encode())
                logging.error(f"Failed to send alert via RCON: {e}")
                
        elif parsed.path == '/tg':
            config = get_tg_config()
            html = HTML_PAGE.format(bot_token=config.get("bot_token", ""), chat_id=config.get("chat_id", ""), message="")
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            self.wfile.write(html.encode('utf-8'))
        elif parsed.path == '/dashboard':
            html = render_dashboard()
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            self.wfile.write(html.encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'Not Found\n')

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == '/tg':
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length).decode('utf-8')
            fields = urllib.parse.parse_qs(post_data)
            
            bot_token = fields.get('bot_token', [''])[0]
            chat_id = fields.get('chat_id', [''])[0]
            
            save_tg_config(bot_token, chat_id)
            
            # Send test message
            threading.Thread(target=send_telegram_message_sync, args=("✅ Telegram уведомления успешно настроены!",), daemon=True).start()
            
            msg = '<div class="success" style="display:block;">Сохранено! Отправлено тестовое сообщение.</div>'
            html = HTML_PAGE.format(bot_token=bot_token, chat_id=chat_id, message=msg)
            
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            self.wfile.write(html.encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

    # Suppress default HTTP logging to avoid spam
    def log_message(self, format, *args):
        pass

def start_webhook_server():
    server = HTTPServer(('0.0.0.0', WEBHOOK_PORT), WebhookHandler)
    logging.info(f"Started Webhook API on port {WEBHOOK_PORT}")
    server.serve_forever()

if __name__ == '__main__':
    logging.info(f"Starting Prometheus exporter on port {PROMETHEUS_PORT}...")
    start_http_server(PROMETHEUS_PORT)
    
    # Start the background liveness checker
    t = threading.Thread(target=liveness_monitor, daemon=True)
    t.start()
    
    # Start the TPS monitor
    t2 = threading.Thread(target=tps_monitor, daemon=True)
    t2.start()
    
    # Start the log tailer in a background thread
    def run_tailer():
        for line in tail(LOG_FILE):
            process_event(line)
    threading.Thread(target=run_tailer, daemon=True).start()
    
    # Start the webhook server in the main thread (needed for RCON signals)
    start_webhook_server()
