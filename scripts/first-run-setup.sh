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
    
    # Разрешаем до 4 аккаунтов/подключений с одного IP (для семьи/друзей из одной сети)
    sudo sed -i -E "s/^[[:space:]]*maxRegPerIp:.*/    maxRegPerIp: 4/g" "$AUTHME_CONFIG"
    sudo sed -i -E "s/^[[:space:]]*maxJoinPerIp:.*/    maxJoinPerIp: 4/g" "$AUTHME_CONFIG"
    
    # Включаем автоматический вход (без пароля) для Bedrock-игроков через Floodgate
    if grep -q "floodgate:" "$AUTHME_CONFIG"; then
        sudo sed -i -E "s/^[[:space:]]*floodgate:.*/    floodgate: true/g" "$AUTHME_CONFIG"
    elif grep -q "^Hooks:" "$AUTHME_CONFIG"; then
        # Если опции нет, но есть секция Hooks, добавляем туда
        sudo sed -i -E "/^Hooks:/a \\    floodgate: true" "$AUTHME_CONFIG"
    else
        # Иначе добавляем секцию целиком
        echo -e "\nHooks:\n    floodgate: true" | sudo tee -a "$AUTHME_CONFIG" > /dev/null
    fi
    
    echo "Перезагрузка сервера для применения настроек..."
    sudo docker restart mc-paper-geyser
    echo "Настройки AuthMe успешно применены!"
else
    echo "Файл $AUTHME_CONFIG не найден!"
    echo "Внимание: Сервер (или плагин AuthMe) должен быть запущен хотя бы один раз, чтобы конфигурация сгенерировалась."
fi

echo "=== Готово! ==="
