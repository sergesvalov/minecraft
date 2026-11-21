# 🛠️ Техническая документация

[← К общему описанию](../README.md)

Документация описывает Docker-инфраструктуру на Raspberry Pi 4, CI/CD-пайплайн Jenkins, автоматическое резервное копирование (Restic) и служебные скрипты.

---

## 1. Контейнеры и настройка под Raspberry Pi 4

Конфигурация описана в [docker-compose.yml](../docker-compose.yml):

| Сервис | Контейнер / образ | Назначение и ключевые параметры |
|---|---|---|
| `mc` | `mc-paper-geyser` (`itzg/minecraft-server:latest`) | Paper **26.3**, `ONLINE_MODE=false`, `GAMEMODE=creative`, `DIFFICULTY=easy`, `MEMORY=1536M`, `USE_AIKAR_FLAGS=true`, `VIEW_DISTANCE=8`, `SIMULATION_DISTANCE=6`, `oom_score_adj: -500` |
| `filebrowser` | `mc-filebrowser` (`filebrowser/filebrowser:latest`) | Веб-доступ к `/opt/minecraft/data` на порту `25580` (user `1000:1000`) |
| `mc-backup` | `mc-backup` (`itzg/mc-backup`) | Ежедневный инкрементальный бэкап в **Restic** (`/backups`) в `02:00`, удержание `--keep-last 7 --keep-daily 7` |
| `warden-monitor` | `warden-monitor` (сборка из `./warden-monitor`) | Экспортёр Prometheus (`8000`) и веб-дашборд / webhook / Telegram API (`8002 -> 8001`); код смонтирован read-only в `/app/src:ro`, логи — в `/app/logs` |

- Ограничение `MEMORY: "1536M"` и `oom_score_adj: -500` защищают игровой сервер от OOM-killer на 4 ГБ Raspberry Pi 4 во время параллельных сборок Jenkins.
- Игровые плагины из `MODRINTH_PROJECTS`: `authmereloaded,squaremap,luckperms,gsit,axgraves,chunky`; из `PLUGINS`: Geyser, Floodgate, `EssentialsX-2.22.0.jar`. Строка `FastAsyncWorldEdit` в [docker-compose.yml](../docker-compose.yml) временно закомментирована до совместимости с 26.3.
- На каждом старте через `RCON_CMDS_STARTUP` суперадмину `papa` выдаются `op papa` и права LuckPerms: `halloumi.tavern`, `halloumi.inn`, `greatbuildings.build`.

---

## 2. CI/CD в Jenkins

Пайплайн описан в [Jenkinsfile](../Jenkinsfile):

1. **Сборка плагинов:** запускает [build-plugin.sh](../scripts/build-plugin.sh) для `WardenLog`, `HalloumiTrader` и `GreatBuildings` через Docker-образ `maven:3.9-eclipse-temurin-25`.
   - Скрипт читает `<version>` из `pom.xml` и пропускает сборку, если `data/plugins/<Name>-<Version>.jar` уже существует.
   - Перед сборкой удаляет устаревшие `data/plugins/<Name>-*.jar` и в конце не допускает дубликатов (`Ambiguous plugin name`).
2. **Подготовка сервера (`Prepare Server Scripts`):**
   - по SSH очищает старые скрипты и старые JAR-файлы кастомных плагинов;
   - копирует `scripts/*`, `warden-monitor/*`, `data/plugins/*.jar`, Bedrock-ресурспак и маппинги Geyser ([HalloumiPack.mcpack](../resourcepack/dist/HalloumiPack.mcpack), [halloumitrader.json](../resourcepack/dist/halloumitrader.json));
   - делает `.sh` и [verify_backup.py](../scripts/verify_backup.py) исполняемыми;
   - пересобирает образ `warden-monitor`.
3. **Параметр `DEPLOY_MONITOR_ONLY`:** при включении обновляет только `docker-compose.yml` и `warden-monitor/*` с пересборкой контейнера `warden-monitor` без рестарта `mc-paper-geyser`.

---

## 3. Хранение данных, бэкапы и проверка консистентности

Все данные сервера на хосте лежат в `/opt/minecraft/data`, бэкапы Restic — в `/opt/minecraft/backups`.

### Автоматическая проверка (`verify-backup.sh` / `verify_backup.py`)

Скрипт [verify-backup.sh](../scripts/verify-backup.sh) вызывает [verify_backup.py](../scripts/verify_backup.py) и проверяет последний снапшот Restic потоково (без распаковки гигабайт данных на SD-карту):

1. `restic check --read-data-subset=...` (по умолчанию `10%`).
2. Контроль свежести снапшота (порог 26 ч) и отсутствия резкого падения объёма/числа файлов.
3. Подтверждение последовательности `rcon-cli save-off` → `save-all` → `save-on` в логах контейнера `mc-backup`.
4. Потоковая проверка содержимого из `restic dump --archive tar`:
   - `data/world/level.dat` (gzip + NBT);
   - все файлы регионов `*.mca` (заголовки, границы секторов, декомпрессия каждого чанка);
   - SQLite-базы (`PRAGMA integrity_check`) и заголовки H2 `.mv.db`;
   - JSON/текстовые файлы (`ops.json`, `banned-players.json`, `banned-ips.json`, `axgraves/data.json`, `luckperms/contexts.json`, `GreatBuildings/buildings.txt`, `HalloumiTrader/taverns.txt`).

На сервере по cron (`0 4 * * *`) проверка запускается автоматически с `--subset 10%` и пишет отчёт в `/opt/minecraft/data/plugins/WardenLog/backup_verify.json`, который читает `warden-monitor`.

Ручной полный прогон:
```bash
bash /opt/minecraft/scripts/verify-backup.sh --subset 100%
```

### Восстановление из бэкапа (`restore-backup.sh`)

Интерактивный скрипт [restore-backup.sh](../scripts/restore-backup.sh) поддерживает как `.tar`/`.tar.gz` архивы, так и снапшоты Restic:
```bash
bash /opt/minecraft/scripts/restore-backup.sh
```
Скрипт останавливает `mc` и `mc-backup`, переименовывает текущий `data/` в `data_old_<timestamp>`, восстанавливает выбранный снапшот и запускает контейнеры обратно.

---

## 4. Первичная настройка и скрипты (`scripts/`)

После самого первого запуска (когда плагины создадут свои конфиги) на хосте запускается [first-run-setup.sh](../scripts/first-run-setup.sh):
1. Настраивает часовой пояс `Europe/Athens` и NTP.
2. В `AuthMe/config.yml` разрешает точку `.` в никах (для Bedrock-игроков Floodgate) и до 4 регистраций с одного IP (`maxRegPerIp: 4`).
3. В `AuthMe/commands.yml` добавляет `/time set day` на события `onLogin` и `onRegister`.
4. В `Geyser-Spigot/config.yml` снижает `mtu: 1300` против `ClientDisconnection-41` по Wi-Fi.
5. Перезапускает `mc-paper-geyser`.

| Скрипт | Назначение |
|---|---|
| [build-plugin.sh](../scripts/build-plugin.sh) | Сборка любого плагина из `plugins-src/<Name>` через Docker Maven |
| [build-warden.sh](../scripts/build-warden.sh) | Обёртка обратной совместимости, вызывающая `build-plugin.sh WardenLog` |
| [first-run-setup.sh](../scripts/first-run-setup.sh) | Однократная (идемпотентная) патчинг-настройка AuthMe, Geyser и времени |
| [verify-backup.sh](../scripts/verify-backup.sh) | Запуск проверки консистентности последнего бэкапа Restic |
| [verify_backup.py](../scripts/verify_backup.py) | Реализация проверки Restic + потоковая валидация чанков и БД |
| `scripts/test_verify_backup.py` | Автотесты `unittest` (7 тестов) для `verify_backup.py` |
| [restore-backup.sh](../scripts/restore-backup.sh) | Интерактивное восстановление `data/` из `.tar` или Restic-снапшота |

