# VoiceDrop for Android 🎙️✨

> **Native Android Voice Studio** with instant MP3 (192 kbps) & Lossless WAV recording, dual clipboard copying (`[ 📋 Copy MP3 ]` & `[ 📋 Copy WAV ]`), **Local On-Device Speech-to-Text (Offline AI)** with **`[ 📋 Copy Text ]`**, in-app audio preview, and real-time visualizer. Inspired by [VoiceDrop macOS](https://github.com/albertincx/VoiceDrop-macos).

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-purple.svg)](https://developer.android.com/jetpack/compose)
[![MP3](https://img.shields.io/badge/Audio-MP3%20192kbps%20(LAME)-orange.svg)](https://lame.sourceforge.net/)
[![STT](https://img.shields.io/badge/AI-On--Device%20STT%20(Offline)-purple.svg)](https://developer.android.com/reference/android/speech/SpeechRecognizer)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 🌟 Highlights

- **🎙️ One-Touch Studio Recording**: Simultaneously records high-fidelity audio in both **MP3 (192 kbps)** and **Lossless WAV (16-bit PCM Mono, 44.1 kHz)**.
- **🧠 Local On-Device Speech-to-Text (Offline AI)**:
  - 100% private, on-device neural speech recognition (`EXTRA_PREFER_OFFLINE`).
  - Zero cloud latency and zero data transmitted over network.
  - Multi-language support: Auto (System), Russian (🇷🇺), English (🇺🇸), German (🇩🇪), Spanish (🇪🇸), French (🇫🇷).
  - Live dictation and real-time streaming recognition preview with editable text box.
- **📋 One-Click Clipboard Actions**:
  - **`[ 📋 Copy Text ]`**: Copies recognized transcription directly to clipboard with visual confirmation (`✓ Copied!`).
  - **`[ 📋 Copy MP3 ]`**: Copies `.mp3` content URI to clipboard with `audio/mpeg` MIME type — paste directly as an audio file into Telegram Web, Slack, Chrome, ChatGPT.
  - **`[ 📋 Copy WAV ]`**: Copies lossless `.wav` content URI to clipboard for studio audio sharing.
  - **`[ Copy & Minimize ]`**: Copies text or audio and minimizes the app back to your chat in one touch.
- **🗜️ Ultra-Compact Minified Build (1.2 MB)**: Full R8 code shrinking and Proguard resource optimization preserve a minuscule install footprint.
- **🔊 Crystal Clear Audio Volume**: Full dynamic range scaling (`shl 16`) ensuring loud, crisp vocal playback without attenuation.
- **↗ System Share Sheet**: Share MP3, WAV, or transcribed text directly to any installed app (Telegram, WhatsApp, Discord, Gmail).
- **💾 Save to Downloads**: One-tap export to `Downloads/VoiceDrop/`.
- **📊 Real-Time Dynamic Waveform**: Frequency visualizer bars animated in real time based on microphone amplitude.
- **🌐 Bilingual Interface (English Default & Russian)**:
  - English is the default application language.
  - Full Russian interface translation (`res/values-ru/strings.xml` and in-app localization).
  - Instant one-tap in-app language switcher `[ 🇺🇸 EN | 🇷🇺 RU ]` in the top bar with preference persistence.
- **🎵 In-App Preview Player**: Built-in player with timeline scrubber and recent recordings history drawer.

---

## 📸 Architecture

```
VoiceDrop-Android/
├── VoiceDrop-v1.2.0.apk             # Pre-built, minified & signed APK (1.2 MB)
├── app/
│   ├── libs/jump3r.jar              # Pure JVM LAME MP3 encoder
│   └── src/main/
│       ├── AndroidManifest.xml      # RECORD_AUDIO, queries & FileProvider
│       └── java/com/voicedrop/android/
│           ├── MainActivity.kt      # Permissions, STT & UI Host
│           ├── audio/
│           │   ├── SpeechTranscriber.kt # 100% On-Device Speech-to-Text engine
│           │   ├── AudioRecorder.kt     # Dual MP3 & WAV recording pipeline
│           │   ├── WavWriter.kt         # High-performance 16-bit PCM WAV writer
│           │   ├── Mp3Encoder.kt        # LAME 192 kbps MP3 encoder (fixed volume)
│           │   ├── AudioPlayer.kt       # MediaPlayer playback & seek
│           │   └── AudioItem.kt         # Recording model (MP3 + WAV)
│           ├── util/
│           │   └── ClipboardHelper.kt   # Text, MP3 & WAV clipboard engine
│           └── ui/
│               ├── VoiceDropScreen.kt    # Main Studio UI & history drawer
│               ├── RecordingCard.kt      # Player, [Copy MP3/WAV], [Recognize] & [Copy Text]
│               └── WaveformVisualizer.kt # Live waveform animation
```

---

## 🚀 Building & Running

### Install APK via ADB:
```bash
adb install -r /home/antigravity/VoiceDrop-Android/VoiceDrop-v1.2.0.apk
```

### Build from source:
```bash
cd /home/antigravity/VoiceDrop-Android
./gradlew assembleDebug
```
