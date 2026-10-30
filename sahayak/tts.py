"""Hybrid text-to-speech.

Offline chain: Piper voice model (.onnx, configured) -> espeak-ng fallback.
Online: edge-tts (free, no key). mode = offline | online | auto.
"""
import os
import platform
import subprocess
import tempfile


class HybridTTS:
    def __init__(self, cfg):
        self.cfg = cfg

    @property
    def mode(self):
        return self.cfg["tts_mode"]

    def speak(self, text, lang="en"):
        """Speak text. Never raises — falls back down the chain quietly."""
        if not text:
            return
        mode = self.mode
        if mode in ("offline", "auto"):
            if self._speak_piper(text, lang) or mode == "offline":
                return
            if self._speak_espeak(text, lang):
                return
        if mode in ("online", "auto"):
            if self._speak_edge(text, lang):
                return
        print(f"[tts] no backend available. Would say: {text}")

    # ---- backends ----
    def _speak_piper(self, text, lang):
        model = (self.cfg["piper_voices"] or {}).get(lang) or ""
        if not model or not os.path.exists(model):
            return False
        try:
            from piper import PiperVoice
            voice = PiperVoice.load(model)
            wav = tempfile.mktemp(suffix=".wav")
            with open(wav, "wb") as f:
                voice.synthesize_wav(text, f)
            self._play(wav)
            return True
        except Exception as e:
            print(f"[tts] piper failed: {e}")
            return False

    def _speak_espeak(self, text, lang):
        try:
            voice = "ne" if lang == "ne" else "en"
            wav = tempfile.mktemp(suffix=".wav")
            subprocess.run(["espeak-ng", "-v", voice, "-w", wav, text],
                           check=True, capture_output=True, timeout=30)
            self._play(wav)
            return True
        except Exception:
            return False

    def _speak_edge(self, text, lang):
        try:
            import asyncio
            import edge_tts
            voice = (self.cfg["edge_voices"] or {}).get(lang, "en-US-AriaNeural")
            wav = tempfile.mktemp(suffix=".mp3")

            async def _run():
                tts = edge_tts.Communicate(text, voice)
                await tts.save(wav)
            asyncio.run(_run())
            self._play(wav)
            return True
        except Exception as e:
            print(f"[tts] edge failed: {e}")
            return False

    @staticmethod
    def _play(path):
        try:
            if platform.system() == "Windows":
                import winsound
                winsound.PlaySound(path, winsound.SND_FILENAME)
            elif platform.system() == "Darwin":
                subprocess.run(["afplay", path], check=False)
            else:
                subprocess.run(["aplay", "-q", path], check=False)
        except Exception:
            pass
