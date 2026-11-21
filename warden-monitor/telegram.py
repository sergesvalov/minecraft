"""Telegram notification settings storage and async message sender."""

import json
import logging
import os
import threading
import urllib.request

import config


def get_tg_config(path: str = None) -> dict:
    cfg_path = path or config.TG_CONFIG_FILE
    if os.path.exists(cfg_path):
        try:
            with open(cfg_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"bot_token": "", "chat_id": ""}


def save_tg_config(token: str, chat_id: str, path: str = None) -> None:
    cfg_path = path or config.TG_CONFIG_FILE
    with open(cfg_path, "w", encoding="utf-8") as f:
        json.dump({"bot_token": token, "chat_id": chat_id}, f)


def send_telegram_message_sync(msg: str, path: str = None) -> None:
    cfg = get_tg_config(path)
    token = cfg.get("bot_token")
    chat_id = cfg.get("chat_id")
    if not token or not chat_id:
        return

    url = f"https://api.telegram.org/bot{token}/sendMessage"
    data = json.dumps({"chat_id": chat_id, "text": msg}).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    try:
        urllib.request.urlopen(req, timeout=5)
    except Exception as e:
        logging.error(f"Failed to send Telegram message: {e}")


def send_telegram_message(msg: str) -> None:
    threading.Thread(target=send_telegram_message_sync, args=(msg,), daemon=True).start()

