# 🎙️ Sahayak — your personal bilingual voice assistant

A complete voice assistant for your **Windows laptop** + **any Android phone**, built to beat
Siri/Google where it matters for you:

- **Real system control** — open apps, volume/brightness, screenshots, lock/shutdown,
  battery, notes, web search… on *your* machine, not a walled garden.
- **Private & offline-first** — speech recognition (faster-whisper) and voice (Piper)
  run on-device. Nothing leaves your laptop unless you enable the online fallback.
- **Bilingual English/Nepali** — say "speak in Nepali" and it switches. Genuinely
  understands Nepali commands (समय कति भयो, भोल्युम बढाऊ, क्रोम खोल…).
- **One shared brain** — the laptop runs the server; the Android app talks to it,
  so both devices know the same commands.

## Layout

```
voice-assistant/
├── sahayak/            # laptop brain (Python)
│   ├── main.py         # entry: voice loop / --server / --text
│   ├── brain.py        # text -> intent -> action -> reply
│   ├── intents.py      # bilingual intent patterns (EN + NE)
│   ├── actions_windows.py  # Windows system control
│   ├── stt.py          # hybrid STT: faster-whisper offline, OpenAI online
│   ├── tts.py          # hybrid TTS: Piper offline, edge-tts online
│   ├── wake.py         # openWakeWord listener ("hey jarvis" by default)
│   ├── server.py       # FastAPI server for the Android app
│   └── config.py       # sahayak_config.json (auto-created)
├── android/            # native Kotlin app (any Android 8.0+ device)
├── setup.bat           # one-time Windows setup
└── requirements.txt
```

## Windows laptop — setup

1. Install Python 3.10+ from python.org (tick **"Add python.exe to PATH"**).
2. Double-click **`setup.bat`** — creates `.venv`, installs everything, downloads the
   offline English voice, and prints your **server token** (needed for the phone app).
3. Talk: `.venv\Scripts\python -m sahayak.main`
   - If the mic/wake-word libraries work: say **"hey jarvis"**, then your command.
   - Otherwise: press Enter to talk (push-to-talk fallback).

## Android phone — setup

1. Open the `android/` project in Android Studio and build a debug APK
   (see `android/README-ANDROID.md`), then install it on your phone.
2. Connect both devices to the **same network** (or Tailscale).
3. In the app's settings: enter your laptop's IP, port `8770`, and the server token
   from setup.
4. On the laptop, run the server: `.venv\Scripts\python -m sahayak.main --server`
5. Tap the mic in the app and speak — English or Nepali.

## Speech modes (in `sahayak_config.json`)

| | Offline (default) | Online fallback |
|---|---|---|
| Hear (STT) | faster-whisper `small` model | OpenAI Whisper API (needs `openai_api_key`) |
| Speak (TTS) | Piper voice (English) · espeak-ng | edge-tts, free, no key (English + Nepali) |

`stt_mode` / `tts_mode`: `offline`, `online`, or `auto` (offline first, online on failure).
Nepali voice is fully supported online via edge-tts; offline Nepali voice needs a
Piper `ne` model (set `piper_voices.ne` if you add one).

## Commands (v1)

Time, date · open/launch apps · volume up/down/mute · brightness up/down ·
screenshot · lock / shutdown / restart / sleep · battery · web search ·
notes ("remember to…") · weather · jokes · help · "speak in Nepali/English" ·
"go to sleep" (stop listening). Every command works in Nepali too.

Add your own: one function in `actions_windows.py` + one pattern block in `intents.py`.
