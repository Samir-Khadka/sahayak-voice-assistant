"""Configuration — JSON file next to the project root, created with defaults."""
import json
import secrets
from pathlib import Path

CONFIG_PATH = Path(__file__).resolve().parent.parent / "sahayak_config.json"

DEFAULTS = {
    "assistant_name": "Sahayak",
    "language": "auto",          # en | ne | auto (auto = detect Devanagari)
    "stt_mode": "auto",          # offline | online | auto
    "tts_mode": "auto",          # offline | online | auto
    "whisper_model": "small",    # tiny | base | small | medium
    "openai_api_key": "",        # optional: online STT + chat fallback
    "server_host": "0.0.0.0",
    "server_port": 8770,
    "server_token": "",
    "wake_word_enabled": True,
    "wake_word_model": "hey_jarvis",  # openWakeWord built-in model name
    "piper_voices": {"en": "", "ne": ""},  # .onnx paths; setup.bat downloads English
    "edge_voices": {"en": "en-US-AriaNeural", "ne": "ne-NP-SagarNeural"},
    "notes_file": str(Path.home() / "sahayak_notes.txt"),
}


class Config:
    def __init__(self, path=CONFIG_PATH):
        self.path = Path(path)
        self.data = dict(DEFAULTS)
        if self.path.exists():
            try:
                self.data.update(json.loads(self.path.read_text(encoding="utf-8")))
            except Exception:
                pass
        if not self.data.get("server_token"):
            self.data["server_token"] = secrets.token_hex(16)
        self.save()

    def save(self):
        self.path.write_text(json.dumps(self.data, indent=2, ensure_ascii=False), encoding="utf-8")

    def __getitem__(self, k):
        return self.data.get(k, DEFAULTS.get(k))

    def __setitem__(self, k, v):
        self.data[k] = v
        self.save()
