import os
import time
import json
import logging
import threading
from datetime import datetime
from prometheus_client import start_http_server, Gauge, Counter
from mcstatus import JavaServer
from http.server import BaseHTTPRequestHandler, HTTPServer
import urllib.parse
from mcrcon import MCRcon

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s | [WARDEN] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)

LOG_FILE = '/app/logs/events.jsonl'
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

server = JavaServer.lookup(f"{MC_HOST}:{MC_PORT}")

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
                was_online = True
                
        except Exception as e:
            MC_ONLINE.set(0)
            MC_PLAYERS.set(0)
            MC_PING.set(0)
            if was_online:
                logging.error(f"❌ SERVER IS DOWN! Failed to ping {MC_HOST}:{MC_PORT}. Error: {e}")
                was_online = False
                
        time.sleep(15) # Check every 15 seconds

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
            
        elif event_type == 'explode_tnt':
            source = data.get('source', 'Unknown')
            world = data.get('world', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            timestamp = data.get('timestamp', 'Unknown')
            TNT_EXPLODED.labels(source=source, world=world, x=str(x), y=str(y), z=str(z), time=timestamp).inc()
            logging.error(f"💥 TNT EXPLODED (Source: {source}) at {x},{y},{z}")
            
    except json.JSONDecodeError:
        pass
    except Exception as e:
        logging.error(f"Error processing event: {e}")

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
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'Not Found\n')
            
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
    
    # Start the webhook server
    threading.Thread(target=start_webhook_server, daemon=True).start()
    
    for line in tail(LOG_FILE):
        process_event(line)
