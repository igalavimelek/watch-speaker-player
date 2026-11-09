# Watch Speaker Player (Wear OS 5)

An Android Wear OS 5 application built for the **OnePlus Watch 3** that routes and plays audio directly out of the watch's built-in speaker.

---

## Origin & Context
* **Conversation ID**: `ec8feb4f-c768-4889-9899-d43ff9b246fb`
* **Antigravity Chat Link**: `conversation://ec8feb4f-c768-4889-9899-d43ff9b246fb`

---

## Key Features

1. **Explicit Watch Speaker Output**
   - Discovers `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` dynamically using `AudioManager`.
   - Explicitly directs playback using `MediaPlayer.setPreferredDevice(speakerDevice)`.

2. **Wear OS 5 Speaker-Suppression Bypass**
   - Includes a stream switch (`AudioAttributes.USAGE_ALARM` vs `USAGE_MEDIA`) that circumvents Wear OS 5's default behavior of suppressing audio playback when Bluetooth headsets are absent.

3. **Out-of-the-Box Demo Audio Tracks**
   - Synthesizes 3 16-bit 44.1kHz PCM `.wav` files on first launch (*Horizon Chimes*, *Pulse Synth Beat*, and *Speaker Range Check*) so you can test audio immediately.

4. **Multi-Source Audio Discovery**
   - Scans `MediaStore.Audio`, `/sdcard/Music`, `/sdcard/Download`, and `/sdcard/Android/data/com.example.watchspeakerplayer/files/Music/` for `.mp3`, `.wav`, `.m4a`, `.ogg`, `.flac`, `.aac`, and `.opus`.
   - Includes an in-app file picker for external audio files.

5. **Wear Compose Material 3 UI**
   - Built with `androidx.wear.compose:compose-material3` for circular watch screens.
   - Rotatable crown scrolling with `TransformingLazyColumn`.
   - Full transport controls (`Prev`, `Play/Pause`, `Next`, `-5s`, `+5s`, `Vol-`, `Vol+`).

---

## Deployment & Run Commands

### 1. Connect Watch via ADB Wireless Debugging
```powershell
adb pair <WATCH_IP>:<PAIRING_PORT> <PAIRING_CODE>
adb connect <WATCH_IP>:<CONNECTION_PORT>
```

### 2. Deploy Using Android CLI
```powershell
android run --apks="app\build\outputs\apk\debug\app-debug.apk" --activity=".MainActivity"
```

### 3. Deploy Using Gradle + ADB
```powershell
.\gradlew.bat :app:installDebug
adb shell am start -n com.example.watchspeakerplayer/.MainActivity
```

### 4. Push Audio Files to the Watch
```powershell
adb push path\to\file.mp3 /sdcard/Android/data/com.example.watchspeakerplayer/files/Music/
```
