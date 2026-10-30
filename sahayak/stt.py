"""Hybrid speech-to-text: faster-whisper offline, OpenAI Whisper API online.

mode = offline | online | auto (try offline, fall back to online on failure)
"""
import os


class HybridSTT:
    def __init__(self, cfg):
        self.cfg = cfg
        self._model = None

    @property
    def mode(self):
        return self.cfg["stt_mode"]

    def _offline(self, audio_path, lang_hint):
        from faster_whisper import WhisperModel
        if self._model is None:
            self._model = WhisperModel(self.cfg["whisper_model"], device="cpu", compute_type="int8")
        lang = None if lang_hint in ("auto", None) else lang_hint
        segments, _ = self._model.transcribe(audio_path, language=lang, beam_size=5)
        return " ".join(s.text for s in segments).strip()

    def _online(self, audio_path, lang_hint):
        key = self.cfg["openai_api_key"] or os.environ.get("OPENAI_API_KEY", "")
        if not key:
            raise RuntimeError("online STT needs an OpenAI API key")
        from openai import OpenAI
        client = OpenAI(api_key=key)
        with open(audio_path, "rb") as f:
            out = client.audio.transcriptions.create(model="whisper-1", file=f,
                                                     language=None if lang_hint == "auto" else lang_hint)
        return (out.text or "").strip()

    def transcribe(self, audio_path, lang_hint="auto"):
        """Return recognized text (may be ''). Never raises for missing backends."""
        mode = self.mode
        if mode in ("offline", "auto"):
            try:
                text = self._offline(audio_path, lang_hint)
                if text or mode == "offline":
                    return text
            except Exception as e:
                if mode == "offline":
                    print(f"[stt] offline failed: {e}")
                    return ""
        if mode in ("online", "auto"):
            try:
                return self._online(audio_path, lang_hint)
            except Exception as e:
                print(f"[stt] online failed: {e}")
        return ""
