# TODO

## Бэкапы

- [ ] **Удалить старые архивы на rpi4** (~2.6 GB). Restic проверен 2026-11-05: `check` без ошибок, последний снапшот `ec2770bf` (06:04).
  ```bash
  cd /opt/minecraft/backups && sudo rm world-202608*.tar.gz cold-20261105-0545-before-docker-rebuild.tar.gz
  ```
- [ ] **Копия бэкапов вне SD-карты.** Сейчас restic-репозиторий `/opt/minecraft/backups` лежит на той же карте, что и мир: если карта умрёт, пропадёт и то и другое. Варианты: `restic copy` во второй репозиторий на NAS или другой машине, либо `rclone sync` каталога репозитория по cron после ночного бэкапа (02:00).
- [x] **Оповещение, если бэкапа нет больше 26 часов.** Реализовано через `scripts/verify-backup.sh` (порог 26 ч в отчёте, алерт в Telegram и на дашборд в `warden-monitor` при > 28 ч).
- [x] **Регулярная проверка восстановления:** `scripts/verify-backup.sh --subset 10%` автоматически гоняется по cron в 04:00 (`restic check` + потоковая проверка `level.dat`, `.mca` чанков, SQLite, H2, JSON и RCON-логов; полную проверку `--subset 100%` или пробный restore можно запускать вручную раз в месяц).
- [ ] **Пересмотреть хранение после предгенерации мира (Chunky).** Мир вырастет на несколько GB, вместе с ним снапшоты и место на карте. Проверить `df -h` и при необходимости `PRUNE_RESTIC_RETENTION`.

## Секреты

- [ ] **Вынести пароли из `docker-compose.yml` в `/opt/minecraft/.env`.** Файл не в git; деплой из `mylib` уже вызывает `docker compose --env-file .env`. В compose заменить значения на `${RCON_PASSWORD}` и `${RESTIC_PASSWORD}`.
- [ ] **Сменить `RCON_PASSWORD: admin`.** Он задан в `mc`, `mc-backup` и `${RCON_PASSWORD:-admin}` в `warden-monitor` (`warden-monitor/config.py` читает его из переменной окружения `RCON_PASSWORD`).
- [ ] **Сменить `RESTIC_PASSWORD: 123`.** Одной переменной не обойтись: пароль — это ключ репозитория. Сначала `restic key add` с новым паролем, проверить доступ, затем `restic key remove <старый id>`, и только потом менять env. Новый пароль сохранить вне Pi, без него бэкапы не расшифровать.
- [ ] **filebrowser отдаёт весь `data/`**, включая `plugins/WardenLog/tg_config.json` (токен Telegram-бота) и `plugins/AuthMe/authme.db` (хэши паролей игроков). Задать новый пароль admin (база пересоздана 2026-11-05), а токен перенести в `.env` или ограничить корень filebrowser.
