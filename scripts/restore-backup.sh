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

if [ ${#BACKUP_FILES[@]} -eq 0 ]; then
    echo "❌ Ошибка: В папке backups не найдено архивов!"
    exit 1
fi

echo "=========================================="
echo "📦 Доступные бэкапы:"
echo "=========================================="
for i in "${!BACKUP_FILES[@]}"; do
    FILE="${BACKUP_FILES[$i]}"
    # Для Linux/MacOS совместимого получения размера
    SIZE=$(du -h "$FILE" | cut -f1)
    DATE=$(date -r "$FILE" "+%Y-%m-%d %H:%M:%S" 2>/dev/null || stat -c %y "$FILE" 2>/dev/null || echo "Неизвестно")
    echo "$((i+1))) $(basename "$FILE") (Размер: $SIZE, Дата: $DATE)"
done
echo "0) Отмена"
echo "=========================================="

read -p "👉 Введите номер бэкапа для восстановления: " choice

if [[ "$choice" == "0" ]]; then
    echo "Отмена операции."
    exit 0
fi

if ! [[ "$choice" =~ ^[0-9]+$ ]] || [ "$choice" -lt 1 ] || [ "$choice" -gt "${#BACKUP_FILES[@]}" ]; then
    echo "❌ Ошибка: Неверный выбор."
    exit 1
fi

SELECTED_BACKUP="${BACKUP_FILES[$((choice-1))]}"
echo ""
echo "✅ Выбран бэкап: $SELECTED_BACKUP"
echo "⚠️ Внимание: Текущая папка data будет переименована (сохранена)."
read -p "Продолжить? (y/n): " confirm

if [[ "$confirm" != "y" && "$confirm" != "Y" && "$confirm" != "д" && "$confirm" != "Д" ]]; then
    echo "Отмена."
    exit 0
fi

echo ""
echo "🛑 Остановка сервера (mc)..."
docker compose stop mc

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
echo "📦 Распаковка архива..."
tar -xf "$SELECTED_BACKUP" -C data
echo "✔️ Распаковка завершена."

echo ""
echo "🚀 Запуск сервера..."
docker compose start mc

echo ""
echo "🎉 Восстановление успешно завершено!"
echo "Вы можете следить за запуском сервера командой: docker compose logs -f mc"
