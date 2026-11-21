# 📋 TODO

[← К общему описанию](../README.md)

## Бэкапы

- [ ] **Удалить старые архивы на rpi4** (~2.6 GB).
  ```bash
  cd /opt/minecraft/backups && sudo rm world-202608*.tar.gz cold-20261105-0545-before-docker-rebuild.tar.gz
  ```
- [ ] **Копия бэкапов вне SD-карты.** Сейчас restic-репозиторий `/opt/minecraft/backups` лежит на той же карте, что и мир: если карта умрёт, пропадёт и то и другое. Варианты: `restic copy` во второй репозиторий на NAS или другой машине, либо `rclone sync` каталога репозитория по cron после ночного бэкапа (02:00).
- [x] **Оповещение, если бэкапа нет больше 26 часов.** Реализовано через [verify-backup.sh](../scripts/verify-backup.sh) (порог 26 ч в отчёте, алерт в Telegram и на дашборд в `warden-monitor` при `> 28` ч).
- [x] **Регулярная проверка восстановления:** `verify-backup.sh --subset 10%` автоматически запускается по cron в `04:00` (`restic check` + потоковая проверка `level.dat`, `.mca`-чанков, SQLite, H2, JSON, текстовых списков плагинов и RCON-логов; полную проверку `--subset 100%` или пробный restore можно запускать вручную раз в месяц).
- [ ] **Разобраться с 2 битыми чанками (`r.-4.1.mca`, `r.1.1.mca`)** и при необходимости пересмотреть хранение после предгенерации мира (`Chunky`): проверить `df -h` и `PRUNE_RESTIC_RETENTION`.

## Секреты

- [ ] **Вынести пароли из `docker-compose.yml` в `/opt/minecraft/.env`.** Файл `.env` не хранится в git; в [docker-compose.yml](../docker-compose.yml) заменить значения в `mc` и `mc-backup` на `${RCON_PASSWORD}` и `${RESTIC_PASSWORD}` (`warden-monitor` уже принимает `${RCON_PASSWORD:-admin}`).
- [ ] **Сменить `RCON_PASSWORD: admin`** во всех трёх сервисах (`mc`, `mc-backup`, `warden-monitor`).
- [ ] **Сменить `RESTIC_PASSWORD: 123`** (сначала `restic key add`, проверка доступа, затем `restic key remove <старый id>` и обновление env; новый пароль сохранить вне Pi).
- [ ] **Ограничить доступ к `filebrowser`:** сейчас отдаёт весь `data/`, включая `plugins/WardenLog/tg_config.json` и `plugins/AuthMe/authme.db`. Сменить пароль admin и ограничить корень или вынести секреты.

