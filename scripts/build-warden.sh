#!/bin/bash
# Обёртка для обратной совместимости: собирает плагин WardenLog
exec bash "$(dirname "$0")/build-plugin.sh" WardenLog
