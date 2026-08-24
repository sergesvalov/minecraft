#!/bin/bash
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
POM_FILE="$PROJECT_ROOT/plugins-src/WardenLog/pom.xml"

if [ ! -f "$POM_FILE" ]; then
    echo "❌ Error: pom.xml not found at $POM_FILE"
    exit 1
fi

# Извлекаем версию плагина из pom.xml
PLUGIN_VERSION=$(grep -m1 '<version>' "$POM_FILE" | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
JAR_NAME="WardenLog-${PLUGIN_VERSION}.jar"
JAR_PATH="$PROJECT_ROOT/data/plugins/$JAR_NAME"

echo "🔍 Проверка версии $PLUGIN_VERSION..."
if [ -f "$JAR_PATH" ]; then
    echo "✅ Плагин WardenLog версии $PLUGIN_VERSION уже собран ($JAR_NAME). Пропускаем сборку."
    exit 0
fi

echo "🏗️ Сборка плагина WardenLog (версия $PLUGIN_VERSION) через Docker..."
docker run --rm \
    -v "$PROJECT_ROOT/plugins-src/WardenLog:/usr/src/app" \
    -v "$PROJECT_ROOT/.m2:/root/.m2" \
    -w /usr/src/app \
    maven:3.9-eclipse-temurin-21 mvn clean package

echo "📦 Копирование готового JAR в data/plugins..."
mkdir -p "$PROJECT_ROOT/data/plugins"
cp "$PROJECT_ROOT/plugins-src/WardenLog/target/WardenLog-${PLUGIN_VERSION}.jar" "$JAR_PATH"

echo "✅ Сборка успешно завершена!"
