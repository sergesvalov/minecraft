"""Warden Monitor entrypoint: Prometheus exporter, server liveness/TPS pollers, log tailer, and HTTP dashboard."""

import json
import logging
import re
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer

from mcstatus import JavaServer
from mcrcon import MCRcon
from prometheus_client import start_http_server

import config
import metrics
from backup_watcher import backup_monitor
from dashboard import render_dashboard, render_tg_page
from events import process_event, tail
from telegram import get_tg_config, save_tg_config, send_telegram_message, send_telegram_message_sync

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | [WARDEN] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

server = JavaServer.lookup(f"{config.MC_HOST}:{config.MC_PORT}")


def liveness_monitor() -> None:
    """Background thread to continuously check if the Minecraft server is alive."""
    logging.info(f"Started liveness monitor for {config.MC_HOST}:{config.MC_PORT}")
    was_online = True
    while True:
        try:
            status = server.status()
            metrics.MC_ONLINE.set(1)
            metrics.MC_PLAYERS.set(status.players.online)
            metrics.MC_PING.set(status.latency)

            if not was_online:
                logging.info(f"✅ Server is back ONLINE! Version: {status.version.name}")
                send_telegram_message(f"✅ Сервер Minecraft ({status.version.name}) запущен и доступен!")
                was_online = True
        except Exception as e:
            metrics.MC_ONLINE.set(0)
            metrics.MC_PLAYERS.set(0)
            metrics.MC_PING.set(0)
            if was_online:
                logging.error(f"❌ SERVER IS DOWN! Failed to ping {config.MC_HOST}:{config.MC_PORT}. Error: {e}")
                send_telegram_message(f"❌ СЕРВЕР НЕДОСТУПЕН! Произошла ошибка: {e}")
                was_online = False

        time.sleep(15)


def tps_monitor() -> None:
    """Background thread to poll server TPS via RCON."""
    logging.info("Started TPS monitor")
    time.sleep(30)
    while True:
        try:
            with MCRcon(config.RCON_HOST, config.RCON_PASSWORD, port=config.RCON_PORT) as mcr:
                response = mcr.command("tps")
                clean = re.sub(r"\xa7.", "", response)
                numbers = re.findall(r"\d+\.\d+", clean)
                if numbers:
                    metrics.MC_TPS.set(float(numbers[0]))
        except Exception:
            pass
        time.sleep(30)


class WebhookHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/alert":
            qs = urllib.parse.parse_qs(parsed.query)
            msg = qs.get("msg", ["ВНИМАНИЕ! Сервер будет перезагружен через 5 минут!"])[0]
            try:
                title_cmd = "title @a title " + json.dumps({"text": "ВНИМАНИЕ", "color": "red", "bold": True})
                sub_cmd = "title @a subtitle " + json.dumps({"text": msg, "color": "yellow"})
                with MCRcon(config.RCON_HOST, config.RCON_PASSWORD, port=config.RCON_PORT) as mcr:
                    mcr.command(title_cmd)
                    mcr.command(sub_cmd)
                    mcr.command(f"say [ALERT] {msg}")
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b"Alert broadcasted successfully!\n")
                logging.info(f"Broadcasted alert to server: {msg}")
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(f"Failed to connect to RCON: {e}\n".encode())
                logging.error(f"Failed to send alert via RCON: {e}")

        elif parsed.path == "/tg":
            cfg = get_tg_config()
            html = render_tg_page(cfg.get("bot_token", ""), cfg.get("chat_id", ""))
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html.encode("utf-8"))

        elif parsed.path == "/dashboard":
            html = render_dashboard()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html.encode("utf-8"))

        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not Found\n")

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/tg":
            content_length = int(self.headers.get("Content-Length", 0))
            post_data = self.rfile.read(content_length).decode("utf-8")
            fields = urllib.parse.parse_qs(post_data)

            bot_token = fields.get("bot_token", [""])[0]
            chat_id = fields.get("chat_id", [""])[0]
            save_tg_config(bot_token, chat_id)

            threading.Thread(
                target=send_telegram_message_sync,
                args=("✅ Telegram уведомления успешно настроены!",),
                daemon=True,
            ).start()

            msg = '<div class="success" style="display:block;">Сохранено! Отправлено тестовое сообщение.</div>'
            html = render_tg_page(bot_token, chat_id, msg)
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html.encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass


def start_webhook_server() -> None:
    httpd = HTTPServer(("0.0.0.0", config.WEBHOOK_PORT), WebhookHandler)
    logging.info(f"Started Webhook API on port {config.WEBHOOK_PORT}")
    httpd.serve_forever()


def main() -> None:
    logging.info(f"Starting Prometheus exporter on port {config.PROMETHEUS_PORT}...")
    start_http_server(config.PROMETHEUS_PORT)

    threading.Thread(target=liveness_monitor, daemon=True).start()
    threading.Thread(target=tps_monitor, daemon=True).start()
    threading.Thread(target=backup_monitor, daemon=True).start()

    def run_tailer():
        for line in tail(config.LOG_FILE):
            process_event(line)

    threading.Thread(target=run_tailer, daemon=True).start()
    start_webhook_server()


if __name__ == "__main__":
    main()
