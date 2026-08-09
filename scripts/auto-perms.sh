#!/bin/bash

echo "[Auto-Perms] Запущен фоновый демон (режим выдачи прав без авто-деОпа)."

(
  # 1. Ждем полного старта сервера (RCON становится доступным)
  until rcon-cli "list" > /dev/null 2>&1; do
    sleep 5
  done

  # 2. Базовая настройка группы admin (на случай если сервер запущен впервые)
  rcon-cli "lp group admin create" > /dev/null 2>&1
  rcon-cli "lp group admin permission set * true" > /dev/null 2>&1
  rcon-cli "lp group admin permission set minecraft.command.teleport false" > /dev/null 2>&1
  rcon-cli "lp group admin permission set essentials.tp.override false floodgate=true" > /dev/null 2>&1
  rcon-cli "lp group admin permission set essentials.tpo false floodgate=true" > /dev/null 2>&1

  # Бесконечный цикл работы скрипта
  while true; do
    echo "[Auto-Perms] Проверяем ops.json на наличие новых операторов..."
    
    if [ -f "/data/ops.json" ]; then
      # Ищем всех операторов (и Java, и Bedrock)
      # Используем jq, если доступен, иначе fallback на grep
      if command -v jq >/dev/null 2>&1; then
        ALL_OPS=$(jq -r '.[] | .name' /data/ops.json 2>/dev/null)
      else
        ALL_OPS=$(grep '"name":' /data/ops.json 2>/dev/null | awk -F'"' '{print $4}')
      fi
      
      if [ -n "$ALL_OPS" ]; then
        echo "$ALL_OPS" | while IFS= read -r player; do
          if [ -n "$player" ]; then
            if [ "$player" = "papa" ]; then
              echo "[Auto-Perms] Пропускаем суперадмина: $player (оставляем ванильный OP)"
              continue
            fi
            
            echo "[Auto-Perms] Назначаем LuckPerms права для игрока: $player"
            rcon-cli "lp user \"$player\" parent set admin"
            echo "[Auto-Perms] Снимаем ванильный OP у: $player"
            rcon-cli "deop \"$player\""
          fi
        done
      fi
    fi
    
    # 3. Вычисляем, сколько секунд спать до 03:00 ночи (надежный способ без date -d для Alpine/BusyBox)
    H=$(date +%H)
    M=$(date +%M)
    S=$(date +%S)
    
    # Считаем сколько секунд прошло с полуночи (используем 10# чтобы избежать octal ошибок)
    SEC_SINCE_MIDNIGHT=$((10#$H * 3600 + 10#$M * 60 + 10#$S))
    TARGET_SEC=$((3 * 3600)) # 3 часа ночи
    
    if [ "$SEC_SINCE_MIDNIGHT" -le "$TARGET_SEC" ]; then
      SLEEP_SECONDS=$((TARGET_SEC - SEC_SINCE_MIDNIGHT))
    else
      SLEEP_SECONDS=$((86400 - SEC_SINCE_MIDNIGHT + TARGET_SEC))
    fi
    
    echo "[Auto-Perms] Следующая проверка через $SLEEP_SECONDS секунд (в 03:00 ночи)."
    sleep "$SLEEP_SECONDS"
  done
) &
