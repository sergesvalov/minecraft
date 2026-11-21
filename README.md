# 🎮 Minecraft-сервер (Paper 26.3 + Geyser)

Кроссплатформенный сервер Minecraft (**Java + Bedrock**) на Raspberry Pi 4 с автоматической сборкой и деплоем через Jenkins.

## Подключение и веб-сервисы

| Сервис | Адрес | Описание |
|---|---|---|
| **Java Edition** | `<SERVER_IP>:25566` | Основной вход для ПК-игроков |
| **Bedrock Edition** | `<SERVER_IP>:19132` | Вход с телефонов и консолей (UDP/TCP) |
| **Squaremap** | `http://<SERVER_IP>:25581` | Интерактивная 2D-карта мира в браузере |
| **FileBrowser** | `http://<SERVER_IP>:25580` | Веб-менеджер файлов сервера |
| **Warden Dashboard** | `http://<SERVER_IP>:8002/dashboard` | Онлайн-панель: статус, TPS, пинг, бэкапы и последние 100 событий |
| **Warden Telegram** | `http://<SERVER_IP>:8002/tg` | Настройка уведомлений в Telegram |
| **Prometheus Metrics** | `http://<SERVER_IP>:8000/metrics` | Метрики сервера, бэкапов и событий безопасности |

## Права и состав сервера

- **Авторизация (`ONLINE_MODE=false`):** плагин **AuthMeReloaded** (`/register пароль пароль`, далее `/login пароль` в игровом чате — для Java и Bedrock).
- **Суперадмин `papa`:** при каждом старте сервера `RCON_CMDS_STARTUP` в [docker-compose.yml](docker-compose.yml) выдаёт `papa` права оператора (`op papa`) и эксклюзивные права через **LuckPerms**:
  - `halloumi.tavern`, `halloumi.inn` — постройка кипрской таверны командой `/halloumi tavern` (`/halloumi inn`);
  - `greatbuildings.build` — постройка исторических сооружений в масштабе 1:1 (`/gb build`, `/gb confirm`, `/gb undo`, `/gb tp`).
- **Сторонние плагины:** `Geyser`, `Floodgate`, `EssentialsX 2.22.0`, `AuthMeReloaded`, `Squaremap`, `LuckPerms`, `GSit`, `AxGraves`, `Chunky` (`FAWE` временно отключён до выхода сборки под 26.3).
- **Кастомные плагины проекта:** `WardenLog v2.1`, `HalloumiTrader v1.6`, `GreatBuildings v1.10`.

## Документация (`docs/`)

| Раздел | Содержание |
|---|---|
| [Руководство для игроков](docs/player-guide.md) | Регистрация, карта, телепорты, анимации, могилы, таверны и великие здания |
| [Техническая документация](docs/technical.md) | Архитектура Docker, настройки под RPi 4, Jenkins CI/CD, бэкапы Restic и скрипты |
| [Мониторинг Warden](docs/monitoring.md) | Архитектура `WardenLog` + `warden-monitor`, дашборд, Telegram, все 22 метрики Prometheus |
| [GreatBuildings (`v1.10`)](docs/plugins/great-buildings.md) | 6 исторических построек 1:1 (Грузия, Кипр, Израиль), команды, архитектура |
| [HalloumiTrader (`v1.6`)](docs/plugins/halloumi-trader.md) | Торговцы халуми, таверны по биомам, меню мезе, постоялец, сиденья |
| [WardenLog (`v2.1`)](docs/plugins/warden-log.md) | Логирование событий в `events.jsonl`, фильтры, лимиты анти-лага и ротация логов |
| [Ресурспак «Халуми»](docs/resourcepack.md) | Иконки блюд 16×16 для Java и Bedrock (Geyser) |
| [TODO](docs/todo.md) | Открытые и выполненные задачи по инфраструктуре и безопасности |
