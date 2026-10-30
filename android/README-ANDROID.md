# Sahayak — Android app

A **standalone** voice assistant for your phone — like "hey google", but yours:
say **"Jarvis"** and the phone obeys. No laptop needed, no account, no cloud.

## How it works

- **Always-on "Jarvis"** — a foreground service (persistent notification, the
  legitimate way to hold the mic) runs Porcupine wake-word detection on-device.
  On wake, the app opens and starts listening immediately.
- **Commands run on the phone itself**: torch, volume, brightness, wifi/bluetooth
  (opens the system panel where Android restricts apps), open apps by name,
  alarms, do-not-disturb, battery, notes, dialling, time/date — in **English or
  Nepali** (`SahayakIntents.kt` + `DeviceActions.kt`).
- **Mic button** for tap-to-talk when always-on is off; typed fallback if the
  device has no speech recognition.
- **Optional laptop link** (off by default): misunderstood commands can be
  forwarded to the Sahayak brain on your Windows laptop (`SahayakClient.kt`).
  The two are fully independent otherwise.

## Setup

1. Open this `android/` folder in Android Studio → let Gradle sync →
   **Build → Build APK(s)** → install on your phone.
2. For always-on: get a **free** access key at console.picovoice.ai
   (personal use is free), paste it in the app's Settings, then tap
   **"Always-on: OFF"** to enable it. Without a key, the mic button still
   works — nothing else is lost.
3. Grant the permissions it asks for (mic, notifications, bluetooth).
   Brightness and Do-Not-Disturb each ask once more, via their system screens.

## API contract (only used when laptop forwarding is enabled)

- `GET /v1/health` → `{"ok": true}`
- `POST /v1/command` `{"text","device_id","lang"}` with header `X-Token`
  → `{"reply_text","action","data","lang"}` · `401` bad token · `429` rate-limited

## Permissions (why each is needed)

- `RECORD_AUDIO` — mic button + always-on wake word.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` — the always-on service.
- `POST_NOTIFICATIONS` — the persistent "listening" notification.
- `INTERNET` — optional laptop forwarding only.
- `FLASHLIGHT`, `BLUETOOTH_CONNECT`, `WRITE_SETTINGS`,
  `ACCESS_NOTIFICATION_POLICY`, `SET_ALARM` — the on-device actions above.

## Honest limits

- Wifi toggling: Android 10+ forbids apps from flipping wifi — the app opens
  the system internet panel instead of pretending.
- Bluetooth toggle: works where the OS allows, otherwise opens settings.
- Wake-word accuracy needs the Porcupine key; without it there is no always-on.
