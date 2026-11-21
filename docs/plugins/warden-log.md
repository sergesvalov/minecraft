# 🛡️ WardenLog (`v2.1`) — Логирование событий безопасности и анти-лага

[← К общему описанию](../../README.md) · [Система мониторинга Warden →](../monitoring.md)

Лёгкий плагин для **Paper 26.3**, записывающий критические игровые события в файл `plugins/WardenLog/events.jsonl` в формате JSON Lines для потребления сервисом `warden-monitor`. Опционально интегрируется с **AuthMeReloaded** (`softdepend: [AuthMe]`).

---

## 1. Настройки (`config.yml`)

Файл [config.yml](../../plugins-src/WardenLog/src/main/resources/config.yml):

| Ключ | По умолчанию | Описание |
|---|---|---|
| `limits.entities_per_chunk` | `150` | Порог числа сущностей в чанке (проверка раз в 60 сек) |
| `limits.grief_blocks` | `45` | Порог сломанных блоков одним игроком за 10 секунд (кулдаун 60 сек) |
| `limits.lag_machine_pistons` | `500` | Порог срабатываний поршней в чанке за 5 секунд (кулдаун 5 мин) |
| `log_retention_days` | `14` | Сколько дней хранить архивы `events-YYYY-MM-DD.jsonl` |

---

## 2. Формат `events.jsonl` (15 типов событий)

Каждая строка содержит поля `timestamp` (ISO-8601 UTC) и `event`:

| `event` | Дополнительные поля | Источник / триггер |
|---|---|---|
| `place_tnt` | `player`, `x`, `y`, `z`, `world` | Установка блока `TNT` |
| `explode_tnt` | `source`, `x`, `y`, `z`, `world` | Взрыв `TNTPrimed` (с непустым `blockList`) |
| `place_crystal` | `player`, `x`, `y`, `z`, `world` | ПКМ кристаллом Энда по блоку |
| `explode_crystal` | `x`, `y`, `z`, `world` | Взрыв `END_CRYSTAL` |
| `place_lava` | `player`, `x`, `y`, `z`, `world` | Разлив `LAVA_BUCKET` |
| `break_valuable` | `player`, `block`, `x`, `y`, `z`, `world` | Разрушение сундуков, бочек, воронок, алмазных/незеритовых/золотых/железных блоков, спавнеров, маяков, шалкеров, печей и др. |
| `grief_alert` | `player`, `blocks_broken`, `x`, `y`, `z`, `world` | Превышение `limits.grief_blocks` за 10 сек |
| `dangerous_mob_spawn` | `mob`, `player`, `x`, `y`, `z`, `world` | Спавн `WITHER`, `ENDER_DRAGON`, `WARDEN`, `RAVAGER`, `EVOKER` (игрок в радиусе 50 блоков или `Unknown`) |
| `suspicious_command` | `player`, `command` | `/op`, `/deop`, `/ban`, `/kick`, `/stop`, `//set`, `//replace` (включая префиксы вида `/minecraft:op`) |
| `player_join` | `player` | Вход игрока |
| `player_quit` | `player` | Выход игрока |
| `player_kill` | `killer`, `victim` | PvP-убийство |
| `failed_login` | `player` | Событие AuthMe `FailedLoginEvent` |
| `high_entity_count` | `count`, `x`, `z`, `world` | Чанк превысил `limits.entities_per_chunk` |
| `lag_machine` | `x`, `z`, `world`, опц. `reason` | Взрыв цепной реакции `> 2` блоков TNT или превышение `limits.lag_machine_pistons` |

События складываются в неблокирующую очередь `EventLogWriter` и сбрасываются на диск асинхронно каждые `5` секунд (а также при `onDisable()`). Раз в сутки файл ротируется в `events-YYYY-MM-DD.jsonl`, архивы старше `log_retention_days` удаляются.

---

## 3. Архитектура, сборка и тесты

Пакеты в `com.server.wardenlog`:
- `WardenLogPlugin`: точка входа и планировщик 4 задач (с отменой в `onDisable()`).
- `config.WardenConfig`: валидированный record параметров `config.yml`.
- `log`: `EventJson`, `EventLogWriter`.
- `tracking`: `CommandFilter`, `RateTracker` (очереди `ArrayDeque` со скользящим окном).
- `listener`: `BlockEventListener`, `EntityEventListener`, `PlayerEventListener`, `AuthMeListener`.

Сборка через Docker Maven:
```bash
bash scripts/build-plugin.sh WardenLog
```
Модульные тесты JUnit 5: `WardenLogTest`.

