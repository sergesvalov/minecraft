#!/bin/bash
# Этот скрипт автоматически монтируется в контейнер и запускается при старте.
# Он ждет появления файла конфигурации AuthMe и патчит его "на лету".

echo "[AuthMe-AutoPatch] Запуск фонового скрипта ожидания конфигурации..."

(
  AUTHME_CONFIG="/data/plugins/AuthMe/config.yml"
  
  # Ждем до 5 минут (150 * 2 сек), пока сервер не сгенерирует файл
  for i in {1..150}; do
    if [ -f "$AUTHME_CONFIG" ]; then
      # Проверяем, пропатчен ли файл уже (ищем floodgate: true)
      if ! grep -q "floodgate: true" "$AUTHME_CONFIG"; then
        echo "[AuthMe-AutoPatch] Файл найден! Применяем патчи..."
        
        # Надежная замена строк (заменяем всю строку целиком независимо от пробелов)
        sed -i -E "s/^[[:space:]]*allowedNicknameCharacters:.*/    allowedNicknameCharacters: '[a-zA-Z0-9_.]*'/g" "$AUTHME_CONFIG"
        sed -i -E "s/^[[:space:]]*timeout:.*/        timeout: 120/g" "$AUTHME_CONFIG"
        sed -i -E "s/^[[:space:]]*floodgate:.*/    floodgate: true/g" "$AUTHME_CONFIG"
        
        echo "[AuthMe-AutoPatch] Перезагрузка плагина через RCON..."
        # Немного ждем, чтобы сервер точно запустил плагины и RCON
        sleep 5
        rcon-cli authme reload
        echo "[AuthMe-AutoPatch] Успешно пропатчено и применено!"
      else
        echo "[AuthMe-AutoPatch] Файл уже содержит правильные настройки (floodgate: true)."
      fi
      break
    fi
    sleep 2
  done
) &
