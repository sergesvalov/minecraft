#!/bin/bash
# Скрипт для применения пост-настроек сервера после первого запуска

# --- Настройки ---
SERVER_DIR="/opt/minecraft"
TIMEZONE="Europe/Athens"
AUTHME_TIMEOUT="120"
# -----------------

echo "=== Применение ручных настроек сервера ==="

echo "1. Настройка часового пояса ($TIMEZONE) и синхронизации времени (NTP)..."
sudo timedatectl set-timezone "$TIMEZONE"
sudo timedatectl set-ntp true

echo "2. Проверка файла конфигурации AuthMe..."
AUTHME_CONFIG="${SERVER_DIR}/data/plugins/AuthMe/config.yml"

if [ -f "$AUTHME_CONFIG" ]; then
    echo "Внесение исправлений в конфиг AuthMe..."
    # Разрешаем точку в никах для Bedrock-игроков (Floodgate)
    sudo sed -i "s/allowedNicknameCharacters: '\[a-zA-Z0-9_\]\*'/allowedNicknameCharacters: '\[a-zA-Z0-9_.\]\*'/g" "$AUTHME_CONFIG"
    
    # Увеличиваем таймаут ввода пароля
    sudo sed -i "s/timeout: 30/timeout: $AUTHME_TIMEOUT/g" "$AUTHME_CONFIG"
    
    # Включаем автоматический вход (без пароля) для Bedrock-игроков через Floodgate
    sudo sed -i "s/floodgate: false/floodgate: true/g" "$AUTHME_CONFIG"
    
    echo "Перезагрузка сервера для применения настроек..."
    sudo docker restart mc-paper-geyser
    echo "Настройки AuthMe успешно применены!"
else
    echo "Файл $AUTHME_CONFIG не найден!"
    echo "Внимание: Сервер (или плагин AuthMe) должен быть запущен хотя бы один раз, чтобы конфигурация сгенерировалась."
fi

echo "=== Готово! ==="
