"""Tail WardenLog JSONL events and dispatch Prometheus counters + Telegram alerts."""

import json
import logging
import os
import time

import metrics
from telegram import send_telegram_message


def tail(file_path: str):
    """Yields new lines appended to `file_path`, reopening seamlessly after daily log rotation."""
    logged_wait = False
    while not os.path.exists(file_path):
        if not logged_wait:
            logging.info(f"Waiting for log file {file_path}...")
            logged_wait = True
        time.sleep(5)

    f = open(file_path, "r", encoding="utf-8")
    f.seek(0, os.SEEK_END)
    logging.info("Connected to log file. Listening for TNT events...")

    while True:
        line = f.readline()
        if line:
            yield line.strip()
            continue

        time.sleep(0.5)
        f.seek(0, os.SEEK_CUR)

        try:
            st = os.stat(file_path)
        except FileNotFoundError:
            continue
        if st.st_ino != os.fstat(f.fileno()).st_ino or st.st_size < f.tell():
            for rest in f:
                if rest.strip():
                    yield rest.strip()
            f.close()
            f = open(file_path, "r", encoding="utf-8")
            logging.info("Log file rotated, reopened events.jsonl")


def process_event(event_line: str, notify=send_telegram_message) -> None:
    """Parses one WardenLog JSON line and updates Prometheus metrics / sends alerts."""
    try:
        data = json.loads(event_line)
    except json.JSONDecodeError:
        return

    try:
        event_type = data.get("event")
        player = data.get("player", "Unknown")
        world = data.get("world", "Unknown")
        x, y, z = data.get("x"), data.get("y"), data.get("z")
        ts = data.get("timestamp", "Unknown")

        if event_type == "place_tnt":
            metrics.TNT_PLACED.labels(player=player, world=world, x=str(x), y=str(y), z=str(z), time=ts).inc()
            logging.warning(f"🧨 TNT PLACED by {player} at {x},{y},{z}")
            notify(f"🧨 ВНИМАНИЕ: Игрок {player} установил динамит (TNT) на координатах {x}, {y}, {z} в мире {world}!")

        elif event_type == "explode_tnt":
            source = data.get("source", "Unknown")
            metrics.TNT_EXPLODED.labels(source=source, world=world, x=str(x), y=str(y), z=str(z), time=ts).inc()
            logging.error(f"💥 TNT EXPLODED (Source: {source}) at {x},{y},{z}")
            notify(f"💥 ВЗРЫВ! Динамит взорвался на координатах {x}, {y}, {z} в мире {world} (Источник: {source})!")

        elif event_type == "player_join":
            metrics.PLAYER_JOINED.labels(player=player, time=ts).inc()
            logging.info(f"✅ Player {player} joined")
            notify(f"👋 Игрок {player} зашел на сервер")

        elif event_type == "player_quit":
            metrics.PLAYER_QUIT.labels(player=player, time=ts).inc()
            logging.info(f"👋 Player {player} quit")
            notify(f"🚪 Игрок {player} покинул сервер")

        elif event_type == "dangerous_mob_spawn":
            mob = data.get("mob", "Unknown")
            metrics.DANGEROUS_MOB_SPAWN.labels(
                mob=mob, player=player, world=world, x=str(x), y=str(y), z=str(z), time=ts
            ).inc()
            logging.warning(f"🧟 DANGEROUS MOB SPAWNED: {mob} near {player} at {x},{y},{z}")
            notify(
                f"⚠️ ВНИМАНИЕ: Опасный моб {mob} заспавнен (или появился рядом с {player}) на координатах {x}, {y}, {z} в мире {world}!"
            )

        elif event_type == "place_lava":
            metrics.LAVA_PLACED.labels(player=player, world=world, x=str(x), y=str(y), z=str(z), time=ts).inc()
            logging.warning(f"🌋 LAVA PLACED by {player} at {x},{y},{z}")
            notify(f"🌋 Игрок {player} разлил лаву на координатах {x}, {y}, {z} в мире {world}!")

        elif event_type == "place_crystal":
            metrics.CRYSTAL_PLACED.labels(player=player, world=world, x=str(x), y=str(y), z=str(z), time=ts).inc()
            logging.warning(f"🔮 CRYSTAL PLACED by {player} at {x},{y},{z}")
            notify(f"🔮 Игрок {player} установил Кристалл Энда на координатах {x}, {y}, {z} в мире {world}!")

        elif event_type == "explode_crystal":
            logging.warning(f"💥 CRYSTAL EXPLODED at {x},{y},{z}")
            notify(f"💥 Взорвался Кристалл Энда на координатах {x}, {y}, {z} в мире {world}!")

        elif event_type == "suspicious_command":
            command = data.get("command", "Unknown")
            metrics.SUSPICIOUS_COMMAND.labels(player=player, command=command, time=ts).inc()
            logging.warning(f"⚠️ SUSPICIOUS COMMAND by {player}: {command}")
            notify(f"⚠️ Подозрительная команда от {player}: `{command}`")

        elif event_type == "player_kill":
            killer = data.get("killer", "Unknown")
            victim = data.get("victim", "Unknown")
            metrics.PLAYER_KILLS.labels(killer=killer, victim=victim, time=ts).inc()
            logging.info(f"⚔️ {killer} killed {victim}")
            notify(f"⚔️ Игрок {killer} убил {victim}!")

        elif event_type == "failed_login":
            metrics.FAILED_LOGINS.labels(player=player, time=ts).inc()
            logging.warning(f"❌ Failed login attempt for {player}")
            notify(f"❌ Неудачная попытка входа под ником {player} (Неверный пароль)!")

        elif event_type == "high_entity_count":
            count = data.get("count", 0)
            logging.warning(f"🐄 HIGH ENTITY COUNT ({count}) in {world} at {x},{z}")
            notify(f"🐄 Аномальное скопление мобов ({count} шт.) в чанке на координатах {x}, {z} в мире {world}!")

        elif event_type == "lag_machine":
            reason = data.get("reason", "Слишком много поршней")
            logging.warning(f"🤖 LAG MACHINE DETECTED in {world} at {x},{z} ({reason})")
            notify(f"🤖 ОБНАРУЖЕНА ЛАГ-МАШИНА ({reason}) в чанке {x}, {z} в мире {world}!")

        elif event_type == "break_valuable":
            block = data.get("block", "Unknown")
            metrics.VALUABLE_BROKEN.labels(
                player=player, block=block, world=world, x=str(x), y=str(y), z=str(z), time=ts
            ).inc()
            logging.info(f"💎 VALUABLE BLOCK {block} broken by {player} at {x},{y},{z}")

        elif event_type == "grief_alert":
            blocks_broken = data.get("blocks_broken", 0)
            metrics.GRIEF_ALERTS.labels(player=player, blocks_broken=str(blocks_broken), time=ts).inc()
            logging.warning(f"🚨 GRIEF ALERT: {player} broke {blocks_broken} blocks in 10 sec at {x},{y},{z}")
            notify(f"🚨 ГРИФ АЛЕРТ! Игрок {player} сломал {blocks_broken} блоков за 10 секунд на координатах {x}, {y}, {z}!")

    except Exception as e:
        logging.error(f"Error processing event: {e}")

