#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

APP_NAME="VoiceDrop"
APP_BUNDLE="${APP_NAME}.app"
CONTENTS_DIR="${APP_BUNDLE}/Contents"
MACOS_DIR="${CONTENTS_DIR}/MacOS"
RESOURCES_DIR="${CONTENTS_DIR}/Resources"

echo "🔨 Сборка ${APP_NAME}..."

# Clean previous build
rm -rf "${APP_BUNDLE}"

# Create bundle directory layout
mkdir -p "${MACOS_DIR}"
mkdir -p "${RESOURCES_DIR}"

# Copy plist and resources
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

# Compile Swift sources
SWIFT_FILES=(
    Sources/Localization.swift
    Sources/AudioDeviceManager.swift
    Sources/AudioRecorder.swift
    Sources/AudioPlayer.swift
    Sources/TranscriptionService.swift
    Sources/DraggableFileView.swift
    Sources/WaveformVisualizer.swift
    Sources/ContentView.swift
    Sources/AppDelegate.swift
    Sources/main.swift
)

echo "⚙️ Компиляция Swift файлов..."
swiftc -O \
    -target arm64-apple-macosx14.0 \
    -framework Cocoa \
    -framework AVFoundation \
    -framework SwiftUI \
    -framework Combine \
    "${SWIFT_FILES[@]}" \
    -o "${MACOS_DIR}/${APP_NAME}"

echo "🔐 Локальная подпись бандла с правами доступа к микрофону..."
codesign --force --deep --sign - --entitlements Resources/VoiceDrop.entitlements "${APP_BUNDLE}"

echo "✅ Успешно собрано: ${SCRIPT_DIR}/${APP_BUNDLE}"
