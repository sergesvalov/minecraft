import os
import time
import json
import logging
import threading
from datetime import datetime
from prometheus_client import start_http_server, Gauge, Counter
from mcstatus import JavaServer

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

# Prometheus Metrics
MC_ONLINE = Gauge('minecraft_server_online', 'Is the Minecraft server online (1) or down (0)')
MC_PLAYERS = Gauge('minecraft_player_count', 'Number of players currently online')
MC_PING = Gauge('minecraft_server_ping_ms', 'Server ping latency in milliseconds')

TNT_PLACED = Counter('warden_tnt_placed_total', 'Total TNT blocks placed')
TNT_EXPLODED = Counter('warden_tnt_exploded_total', 'Total TNT explosions')

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
    while not os.path.exists(file_path):
        logging.info(f"Waiting for log file {file_path}...")
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
            TNT_PLACED.inc()
            player = data.get('player', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            logging.warning(f"🧨 TNT PLACED by {player} at {x},{y},{z}")
            
        elif event_type == 'explode_tnt':
            TNT_EXPLODED.inc()
            source = data.get('source', 'Unknown')
            x, y, z = data.get('x'), data.get('y'), data.get('z')
            logging.error(f"💥 TNT EXPLODED (Source: {source}) at {x},{y},{z}")
            
    except json.JSONDecodeError:
        pass
    except Exception as e:
        logging.error(f"Error processing event: {e}")

if __name__ == '__main__':
    logging.info(f"Starting Prometheus exporter on port {PROMETHEUS_PORT}...")
    start_http_server(PROMETHEUS_PORT)
    
    # Start the background liveness checker
    t = threading.Thread(target=liveness_monitor, daemon=True)
    t.start()
    
    for line in tail(LOG_FILE):
        process_event(line)
