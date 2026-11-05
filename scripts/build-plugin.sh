#!/bin/bash
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PLUGIN="${1:?Usage: build-plugin.sh <PluginName> (folder in plugins-src)}"
PLUGIN_DIR="$PROJECT_ROOT/plugins-src/$PLUGIN"
POM_FILE="$PLUGIN_DIR/pom.xml"

if [ ! -f "$POM_FILE" ]; then
    echo "❌ Error: pom.xml not found at $POM_FILE"
    exit 1
fi

# Извлекаем версию плагина из pom.xml
PLUGIN_VERSION=$(grep -m1 '<version>' "$POM_FILE" | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
JAR_NAME="${PLUGIN}-${PLUGIN_VERSION}.jar"
JAR_PATH="$PROJECT_ROOT/data/plugins/$JAR_NAME"

# Удаляем старые версии плагина из workspace, иначе они уедут на сервер вместе с текущей
# (Paper: "Ambiguous plugin name")
find "$PROJECT_ROOT/data/plugins" -maxdepth 1 -name "${PLUGIN}-*.jar" ! -name "$JAR_NAME" -print -delete 2>/dev/null || true

echo "🔍 Проверка версии $PLUGIN_VERSION..."
if [ -f "$JAR_PATH" ]; then
    echo "✅ Плагин $PLUGIN версии $PLUGIN_VERSION уже собран ($JAR_NAME). Пропускаем сборку."
    # Export build info for CI — mark as skipped (no new version)
    BUILD_INFO="$PLUGIN_DIR/build-info.properties"
    echo "PLUGIN_VERSION=${PLUGIN_VERSION}" > "$BUILD_INFO"
    echo "JAR_PATH=${JAR_PATH}" >> "$BUILD_INFO"
    echo "JAR_NAME=${JAR_NAME}" >> "$BUILD_INFO"
    echo "BUILD_NEW=false" >> "$BUILD_INFO"
    exit 0
fi

echo "🏗️ Сборка плагина $PLUGIN (версия $PLUGIN_VERSION) через Docker..."
docker run --rm \
    -u "$(id -u):$(id -g)" \
    -v "$PLUGIN_DIR:/usr/src/app" \
    -v "$PROJECT_ROOT/.m2:/var/maven/.m2" \
    -e MAVEN_CONFIG=/var/maven/.m2 \
    -w /usr/src/app \
    maven:3.9-eclipse-temurin-25 mvn -Duser.home=/var/maven clean package

echo "📦 Копирование готового JAR в data/plugins..."
mkdir -p "$PROJECT_ROOT/data/plugins"
cp "$PLUGIN_DIR/target/${PLUGIN}-${PLUGIN_VERSION}.jar" "$JAR_PATH"

# Export build info for CI — mark as fresh build (new version)
BUILD_INFO="$PLUGIN_DIR/build-info.properties"
echo "PLUGIN_VERSION=${PLUGIN_VERSION}" > "$BUILD_INFO"
echo "JAR_PATH=${JAR_PATH}" >> "$BUILD_INFO"
echo "JAR_NAME=${JAR_NAME}" >> "$BUILD_INFO"
echo "BUILD_NEW=true" >> "$BUILD_INFO"

echo "✅ Сборка успешно завершена!"
