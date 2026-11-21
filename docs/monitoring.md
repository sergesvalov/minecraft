# 📊 Система мониторинга и аналитики (Warden)

[← К общему описанию](../README.md) · [Плагин WardenLog →](plugins/warden-log.md)

Система мониторинга состоит из двух частей:
1. **[WardenLog](plugins/warden-log.md)** (Java-плагин Paper) — пишет игровые события, гриф-триггеры и лаг-машины в `/plugins/WardenLog/events.jsonl`.
2. **Warden Monitor** (`warden-monitor/`) — Python-сервис (`python:3.11-slim`), который опрашивает сервер по статусу и RCON, читает `events.jsonl` и `backup_verify.json`, экспортирует метрики Prometheus, отдаёт веб-дашборд и шлёт уведомления в Telegram.

---

## 1. Модульная структура `warden-monitor/`

| Модуль | Ответственность |
|---|---|
| [monitor.py](../warden-monitor/monitor.py) | Точка входа, HTTP-сервер (`8001` → `8002`), опросы статуса (каждые 15 с) и RCON TPS (каждые 30 с) |
| [config.py](../warden-monitor/config.py) | Чтение параметров из окружения (`RCON_PASSWORD`, `MC_HOST`, `PROMETHEUS_PORT`, `BACKUP_STALE_HOURS` и др.) |
| [metrics.py](../warden-monitor/metrics.py) | Определение всех метрик Prometheus |
| [events.py](../warden-monitor/events.py) | Хвостовое чтение `events.jsonl` (с поддержкой ротации) и маршрутизация всех 15 типов событий |
| [backup_watcher.py](../warden-monitor/backup_watcher.py) | Чтение `backup_verify.json` раз в минуту, обновление метрик бэкапа и Telegram-алерты об ошибках/устаревании |
| [dashboard.py](../warden-monitor/dashboard.py) | XSS-безопасный HTML-рендер `/dashboard` (с 5 карточками, включая статус бэкапа) и формы `/tg` |
| `telegram.py` | Чтение/сохранение `tg_config.json` и асинхронная отправка уведомлений в Telegram |
| `test_monitor.py` | Модульные тесты `unittest` (3 теста) для событий, бэкап-вотчера и дашборда |

---

## 2. Веб-интерфейсы и API

- **Дашборд:** `http://<SERVER_IP>:8002/dashboard` — автообновление каждые 30 сек, карточки `Статус`, `Игроки`, `TPS`, `Пинг`, `Бэкап` (`✅ OK`, `⚠️ <возраст>`, `❌ Ошибка (N)`), таблица последних 100 событий из `events.jsonl` и последнего архива `events-*.jsonl`.
- **Настройка Telegram:** `http://<SERVER_IP>:8002/tg` — ввод `Bot Token` и `Chat ID` с сохранением в `/app/logs/tg_config.json` и отправкой тестового сообщения.
- **Webhook объявлений в игре:** `GET http://<SERVER_IP>:8002/alert?msg=Текст` — через RCON выводит `title @a` («ВНИМАНИЕ») и дублирует сообщение в чат.
- **Prometheus:** `http://<SERVER_IP>:8000/metrics`.

---

## 3. Полный список метрик Prometheus (`metrics.py`)

### Статус сервера и бэкапов (10 Gauge-метрик)

| Метрика | Описание |
|---|---|
| `minecraft_server_online` | Доступность сервера (`1` — онлайн, `0` — офлайн) |
| `minecraft_player_count` | Текущее число игроков онлайн |
| `minecraft_server_ping_ms` | Задержка ответа сервера (мс) |
| `minecraft_server_tps` | Последнее значение TPS (за 1 минуту) через RCON `tps` |
| `minecraft_backup_verify_ok` | Результат последней проверки бэкапа (`1` = ОК, `0` = ошибка, `-1` = нет отчёта) |
| `minecraft_backup_last_verify_timestamp` | Unix-время последней проверки бэкапа |
| `minecraft_backup_snapshot_age_seconds` | Возраст последнего проверенного снапшота в секундах |
| `minecraft_backup_snapshot_size_bytes` | Размер последнего проверенного снапшота в байтах |
| `minecraft_backup_corrupt_chunks` | Число повреждённых чанков `.mca`, найденных при проверке |
| `minecraft_backup_verify_errors` | Общее количество ошибок в отчёте `backup_verify.json` |

### События безопасности (12 Counter-метрик)

| Метрика | Метки (Labels) | Событие в `events.jsonl` |
|---|---|---|
| `warden_tnt_placed_total` | `player`, `world`, `x`, `y`, `z`, `time` | `place_tnt` |
| `warden_tnt_exploded_total` | `source`, `world`, `x`, `y`, `z`, `time` | `explode_tnt` |
| `warden_player_joins_total` | `player`, `time` | `player_join` |
| `warden_player_quits_total` | `player`, `time` | `player_quit` |
| `warden_dangerous_mob_spawn_total` | `mob`, `player`, `world`, `x`, `y`, `z`, `time` | `dangerous_mob_spawn` |
| `warden_lava_placed_total` | `player`, `world`, `x`, `y`, `z`, `time` | `place_lava` |
| `warden_crystal_placed_total` | `player`, `world`, `x`, `y`, `z`, `time` | `place_crystal` |
| `warden_suspicious_command_total` | `player`, `command`, `time` | `suspicious_command` |
| `warden_player_kills_total` | `killer`, `victim`, `time` | `player_kill` |
| `warden_failed_logins_total` | `player`, `time` | `failed_login` |
| `warden_valuable_block_broken_total` | `player`, `block`, `world`, `x`, `y`, `z`, `time` | `break_valuable` |
| `warden_grief_alerts_total` | `player`, `blocks_broken`, `time` | `grief_alert` |

*(События `explode_crystal`, `high_entity_count` и `lag_machine` также отображаются на дашборде и отправляются в Telegram, без отдельных Counter-метрик в Prometheus).*

---

## 4. Уведомления в Telegram

В Telegram отправляются оповещения о событиях:
- падение и восстановление сервера (`mc`);
- ошибка проверки консистентности бэкапа, восстановление после сбоя и устаревание снапшота (`> BACKUP_STALE_HOURS`, по умолчанию 28 ч);
- вход/выход игроков, PvP-убийства, ошибки пароля (AuthMe) и опасные команды;
- установка и взрыв TNT / Кристаллов Энда, разлив лавы;
- спавн опасных мобов (`WITHER`, `ENDER_DRAGON`, `WARDEN`, `RAVAGER`, `EVOKER`);
- гриф-всплески, скопления сущностей и лаг-машины на поршнях или цепном TNT.

---

## 5. Защита от перегрузок (Raspberry Pi 4)

- **Скопление мобов:** `EntityEventListener` раз в минуту (каждые 1200 тиков на основном тике сервера) проверяет количество сущностей в загруженных чанках и сигнализирует при превышении `entities_per_chunk` (по умолчанию `150`).
- **Лаг-машины на поршнях:** `RateTracker` считает срабатывания поршней за 5-секундный интервал в каждом чанке; при превышении `lag_machine_pistons` (`500`) отправляет событие `lag_machine` и ставит чанк на 5-минутный cooldown.
- **Запись на диск без лагов:** события буферизуются в памяти и асинхронно сбрасываются в `events.jsonl` раз в 5 секунд (`EventLogWriter`).

