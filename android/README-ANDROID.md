# Sahayak — Android app

Native Kotlin app (no frameworks beyond AndroidX) that talks to the Sahayak
brain running on your Windows laptop. Runs on **any Android 8.0+ device** —
nothing Samsung/Pixel specific, no Google Play Services dependency beyond what
the system's own `SpeechRecognizer` needs.

## What it does

- **Mic button** → speech-to-text via Android's `SpeechRecognizer`
  (falls back to a typed command box if recognition is unavailable).
- Sends the text to `POST /v1/command` on your laptop server with your
  `X-Token`, shows the reply, and **speaks it** via Android TTS
  (Nepali voice when the reply is Nepali, if installed).
- **Settings screen**: server URL, token, language (Auto/English/Nepali).
- **Offline fallback**: if the laptop is unreachable, the phone itself handles
  torch on/off and "what time is it" — extend `MainActivity.handleLocally()`.

## API contract (implemented by `sahayak/server.py`)

- `GET /v1/health` → `{"ok": true}`
- `POST /v1/command` `{"text","device_id","lang"}` with header `X-Token`
  → `{"reply_text","action","data","lang"}` · `401` bad token · `429` rate-limited

## Build

1. Open this `android/` folder in Android Studio.
2. Let Gradle sync (needs internet once for dependencies).
3. Run on your phone, or **Build → Build APK(s)** for a debug APK to sideload.

## Setup

1. On the laptop: `python -m sahayak.main --server` (note the token printed
   by `setup.bat`, or read `sahayak_config.json`).
2. Connect the phone to the same network as the laptop (or Tailscale —
   recommended: it encrypts the traffic carrying your token).
3. In the app's Settings: server URL = `http://<laptop-ip>:8770`,
   token = your token.
4. Tap the mic and speak — English or Nepali.

## Permissions (why each is needed)

- `RECORD_AUDIO` — the mic button.
- `INTERNET` — talking to your laptop's server.
- `FLASHLIGHT` — the offline torch action only.
