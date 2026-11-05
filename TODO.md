# TODO

## Бэкапы

- [ ] **Удалить старые архивы на rpi4** (~2.6 GB). Restic проверен 2026-11-05: `check` без ошибок, последний снапшот `ec2770bf` (06:04).
  ```bash
  cd /opt/minecraft/backups && sudo rm world-202608*.tar.gz cold-20261105-0545-before-docker-rebuild.tar.gz
  ```
- [ ] **Копия бэкапов вне SD-карты.** Сейчас restic-репозиторий `/opt/minecraft/backups` лежит на той же карте, что и мир: если карта умрёт, пропадёт и то и другое. Варианты: `restic copy` во второй репозиторий на NAS или другой машине, либо `rclone sync` каталога репозитория по cron после ночного бэкапа (02:00).
- [ ] **Оповещение, если бэкапа нет больше 26 часов.** Warden-monitor уже шлёт сообщения в Telegram: можно раз в час смотреть время последнего снапшота (`restic snapshots --latest 1 --json`) или mtime `backups/snapshots/`.
- [ ] **Регулярная проверка восстановления** (раз в месяц):
  ```bash
  docker exec mc-backup restic -r /backups check --read-data          # ~7.5 мин на Pi
  docker exec mc-backup restic -r /backups restore latest --target /backups/restore-verify
  # сравнить region-файлы с data/world/dimensions/minecraft/*/region, потом удалить restore-verify
  ```
- [ ] **Пересмотреть хранение после предгенерации мира (Chunky).** Мир вырастет на несколько GB, вместе с ним снапшоты и место на карте. Проверить `df -h` и при необходимости `PRUNE_RESTIC_RETENTION`.

## Секреты

- [ ] **Вынести пароли из `docker-compose.yml` в `/opt/minecraft/.env`.** Файл не в git; деплой из `mylib` уже вызывает `docker compose --env-file .env`. В compose заменить значения на `${RCON_PASSWORD}` и `${RESTIC_PASSWORD}`.
- [ ] **Сменить `RCON_PASSWORD: admin`.** Он задан в трёх местах: `mc`, `mc-backup` и захардкожен в `warden-monitor/monitor.py` (`RCON_PASSWORD = 'admin'`). Монитор должен читать пароль из переменной окружения.
- [ ] **Сменить `RESTIC_PASSWORD: 123`.** Одной переменной не обойтись: пароль — это ключ репозитория. Сначала `restic key add` с новым паролем, проверить доступ, затем `restic key remove <старый id>`, и только потом менять env. Новый пароль сохранить вне Pi, без него бэкапы не расшифровать.
- [ ] **filebrowser отдаёт весь `data/`**, включая `plugins/WardenLog/tg_config.json` (токен Telegram-бота) и `plugins/AuthMe/authme.db` (хэши паролей игроков). Задать новый пароль admin (база пересоздана 2026-11-05), а токен перенести в `.env` или ограничить корень filebrowser.
