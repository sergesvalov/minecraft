#!/bin/bash

# Скрипт для интерактивного восстановления сервера из бэкапа
# Рекомендуется запускать из корня проекта или из папки scripts

set -e

# Переходим в корень проекта (где лежит docker-compose.yml)
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

if [ ! -f "docker-compose.yml" ]; then
    echo "❌ Ошибка: docker-compose.yml не найден в $PROJECT_ROOT"
    exit 1
fi

if [ ! -d "backups" ]; then
    echo "❌ Ошибка: Папка backups не найдена!"
    exit 1
fi

echo "🔍 Поиск доступных бэкапов..."
# Собираем список архивов
shopt -s nullglob
BACKUP_FILES=(backups/*.tar backups/*.tar.gz backups/*.tgz)
shopt -u nullglob

RESTIC_PASSWORD="123"

# Проверка наличия restic репозитория
HAS_RESTIC=false
if [ -f "backups/config" ]; then
    HAS_RESTIC=true
fi

if [ ${#BACKUP_FILES[@]} -eq 0 ] && [ "$HAS_RESTIC" = false ]; then
    echo "❌ Ошибка: В папке backups не найдено архивов или restic репозитория!"
    exit 1
fi

echo "=========================================="
echo "📦 Доступные бэкапы:"
echo "=========================================="

INDEX=1
TAR_MAP=()
RESTIC_MAP=()

if [ ${#BACKUP_FILES[@]} -gt 0 ]; then
    echo "--- Архивы (Tar) ---"
    for FILE in "${BACKUP_FILES[@]}"; do
        SIZE=$(du -h "$FILE" | cut -f1)
        DATE=$(date -r "$FILE" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || stat -c %y "$FILE" 2>/dev/null || echo "Неизвестно")
        echo "$INDEX) $(basename "$FILE") (Размер: $SIZE, Дата: $DATE)"
        TAR_MAP[$INDEX]="$FILE"
        ((INDEX++))
    done
fi

if [ "$HAS_RESTIC" = true ]; then
    echo "--- Restic Snapshots (Разностные) ---"
    # Получаем список снапшотов
    # Используем docker для запуска restic
    SNAPSHOTS=$(docker run --rm -v "$PROJECT_ROOT/backups:/backups" -e RESTIC_PASSWORD="$RESTIC_PASSWORD" restic/restic -r /backups snapshots 2>/dev/null || true)
    
    if [ -n "$SNAPSHOTS" ]; then
        # Извлекаем только строки со снапшотами (содержат ID из 8 символов в начале)
        while read -r line; do
            if [[ "$line" =~ ^[0-9a-f]{8}\  ]]; then
                ID=$(echo "$line" | awk '{print $1}')
                DATE=$(echo "$line" | awk '{print $2, $3}')
                echo "$INDEX) Restic Snapshot: $ID (Дата: $DATE)"
                RESTIC_MAP[$INDEX]="$ID"
                ((INDEX++))
            fi
        done <<< "$SNAPSHOTS"
    else
        echo "  Снапшотов пока нет."
    fi
fi

echo "0) Отмена"
echo "=========================================="

read -p "👉 Введите номер бэкапа для восстановления: " choice

if [[ "$choice" == "0" ]]; then
    echo "Отмена операции."
    exit 0
fi

if ! [[ "$choice" =~ ^[0-9]+$ ]] || [ "$choice" -lt 1 ] || [ "$choice" -ge "$INDEX" ]; then
    echo "❌ Ошибка: Неверный выбор."
    exit 1
fi

IS_TAR=false
IS_RESTIC=false

if [ -n "${TAR_MAP[$choice]}" ]; then
    SELECTED_BACKUP="${TAR_MAP[$choice]}"
    IS_TAR=true
    echo ""
    echo "✅ Выбран TAR бэкап: $SELECTED_BACKUP"
elif [ -n "${RESTIC_MAP[$choice]}" ]; then
    SELECTED_BACKUP="${RESTIC_MAP[$choice]}"
    IS_RESTIC=true
    echo ""
    echo "✅ Выбран Restic Snapshot: $SELECTED_BACKUP"
fi

echo "⚠️ Внимание: Текущая папка data будет переименована (сохранена)."
read -p "Продолжить? (y/n): " confirm

if [[ "$confirm" != "y" && "$confirm" != "Y" && "$confirm" != "д" && "$confirm" != "Д" ]]; then
    echo "Отмена."
    exit 0
fi

echo ""
echo "🛑 Остановка сервера (mc, mc-backup)..."
docker compose stop mc mc-backup

echo ""
echo "💾 Сохранение текущих данных..."
if [ -d "data" ]; then
    TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
    BACKUP_DIR="data_old_${TIMESTAMP}"
    mv data "$BACKUP_DIR"
    echo "✔️ Текущая папка data переименована в $BACKUP_DIR"
else
    echo "⚠️ Папка data не найдена (возможно, это первый запуск)."
fi

echo ""
echo "📂 Создание новой папки data..."
mkdir -p data

echo ""
if [ "$IS_TAR" = true ]; then
    echo "📦 Распаковка архива $SELECTED_BACKUP..."
    tar -xf "$SELECTED_BACKUP" -C data
    echo "✔️ Распаковка завершена."
elif [ "$IS_RESTIC" = true ]; then
    echo "📦 Восстановление из Restic Snapshot $SELECTED_BACKUP..."
    # Оригинальный путь внутри бэкапа - /data. Восстанавливаем в корень, так как мы смонтировали нашу папку data в /data контейнера
    docker run --rm -v "$PROJECT_ROOT/data:/data" -v "$PROJECT_ROOT/backups:/backups" -e RESTIC_PASSWORD="$RESTIC_PASSWORD" restic/restic -r /backups restore "$SELECTED_BACKUP" --target /
    echo "✔️ Восстановление завершено."
fi

echo ""
echo "🚀 Запуск сервера..."
docker compose start mc mc-backup

echo ""
echo "🎉 Восстановление успешно завершено!"
echo "Вы можете следить за запуском сервера командой: docker compose logs -f mc"
