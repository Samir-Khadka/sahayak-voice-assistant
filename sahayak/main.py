"""Sahayak entry point.

  python -m sahayak.main --server        run the phone API server
  python -m sahayak.main --text "open chrome"   one-shot command, no mic
  python -m sahayak.main                voice loop (wake word if available,
                                        otherwise press Enter to talk)
"""
import argparse
import tempfile
import wave

import numpy as np

from .brain import Brain
from .config import Config
from .intents import detect_lang
from .wake import WakeListener, SAMPLE_RATE


def record_seconds(seconds=6):
    import sounddevice as sd
    print(f"[mic] recording {seconds}s …")
    audio = sd.rec(int(seconds * SAMPLE_RATE), samplerate=SAMPLE_RATE, channels=1, dtype="int16")
    sd.wait()
    path = tempfile.mktemp(suffix=".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(audio.tobytes())
    return path


def voice_loop(brain, cfg):
    wake = WakeListener(cfg["wake_word_model"]) if cfg["wake_word_enabled"] else None

    def one_command():
        try:
            path = record_seconds()
            text = brain.stt.transcribe(path, lang_hint=cfg["language"])
            print(f"[heard] {text!r}")
            if not text:
                reply = "Sorry, I didn't hear anything."
                print(f"[sahayak] {reply}")
                brain.speak(reply, "en")
                return
            lang = cfg["language"] if cfg["language"] in ("en", "ne") else detect_lang(text)
            reply, intent, _ = brain.handle_text(text)
            print(f"[sahayak] {reply}")
            brain.speak(reply, lang)
        except Exception as e:
            print(f"[main] command failed ({e}); listening again…")

    if wake and wake.available():
        print(f"[main] say '{cfg['wake_word_model']}' to wake me, Ctrl+C to quit.")
        wake.on_wake = one_command
        try:
            wake.start()
        except KeyboardInterrupt:
            wake.stop()
    else:
        print("[main] wake word unavailable — press Enter to talk, Ctrl+C to quit.")
        try:
            while True:
                input("  (press Enter) ")
                one_command()
        except (KeyboardInterrupt, EOFError):
            pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", action="store_true")
    ap.add_argument("--text", default=None)
    ap.add_argument("--dry-run", action="store_true",
                    help="don't touch the real system (for testing)")
    args = ap.parse_args()

    cfg = Config()
    if args.server:
        from .server import run
        print(f"[server] http://{cfg['server_host']}:{cfg['server_port']}  token={cfg['server_token']}")
        run()
        return

    brain = Brain(cfg, dry_run=args.dry_run)
    if args.text:
        lang = cfg["language"] if cfg["language"] in ("en", "ne") else detect_lang(args.text)
        reply, intent, slots = brain.handle_text(args.text)
        print(f"intent={intent} slots={slots}\nreply[{lang}]: {reply}")
        return

    voice_loop(brain, cfg)


if __name__ == "__main__":
    main()
