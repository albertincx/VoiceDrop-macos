#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

APP_NAME="VoiceDrop"
APP_BUNDLE="${APP_NAME}.app"
CONTENTS_DIR="${APP_BUNDLE}/Contents"
MACOS_DIR="${CONTENTS_DIR}/MacOS"
RESOURCES_DIR="${CONTENTS_DIR}/Resources"

# Use release mode in CI or when --release is passed; fast incremental debug mode locally
BUILD_CONFIG="debug"
if [ "${CI:-}" = "true" ] || [ "${1:-}" = "--release" ]; then
    BUILD_CONFIG="release"
fi

echo "🔨 Сборка ${APP_NAME} (${BUILD_CONFIG})..."

echo "⚙️ Инкрементальная компиляция Swift (SwiftPM)..."
swift build -c "${BUILD_CONFIG}"

BIN_PATH="$(swift build -c "${BUILD_CONFIG}" --show-bin-path)/${APP_NAME}"

# Create bundle directory layout (update in-place instead of wiping everything)
mkdir -p "${MACOS_DIR}"
mkdir -p "${RESOURCES_DIR}"

# Copy binary, plist and resources
cp "${BIN_PATH}" "${MACOS_DIR}/${APP_NAME}"
cp Resources/Info.plist "${CONTENTS_DIR}/Info.plist"
if [ -f Resources/AppIcon.icns ]; then
    cp Resources/AppIcon.icns "${RESOURCES_DIR}/AppIcon.icns"
fi
cp transcribe.py "${RESOURCES_DIR}/transcribe.py"
chmod +x "${RESOURCES_DIR}/transcribe.py"
if [ -f denoise.py ]; then
    cp denoise.py "${RESOURCES_DIR}/denoise.py"
    chmod +x "${RESOURCES_DIR}/denoise.py"
fi
if [ -f convert_mp3.py ]; then
    cp convert_mp3.py "${RESOURCES_DIR}/convert_mp3.py"
    chmod +x "${RESOURCES_DIR}/convert_mp3.py"
fi
if [ -f convert_ogg.py ]; then
    cp convert_ogg.py "${RESOURCES_DIR}/convert_ogg.py"
    chmod +x "${RESOURCES_DIR}/convert_ogg.py"
fi

echo "🔐 Локальная подпись бандла с правами доступа к микрофону..."
codesign --force --deep --sign - --entitlements Resources/VoiceDrop.entitlements "${APP_BUNDLE}"

echo "✅ Успешно собрано: ${SCRIPT_DIR}/${APP_BUNDLE}"
