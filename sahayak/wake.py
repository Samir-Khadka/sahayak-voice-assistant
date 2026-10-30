"""Wake-word listener (openWakeWord). Falls back gracefully when the
library or audio backend is missing — main.py then uses push-to-talk."""
import numpy as np

SAMPLE_RATE = 16000
CHUNK = 1280  # 80ms @ 16kHz, as openWakeWord expects


class WakeListener:
    def __init__(self, model_name="hey_jarvis", on_wake=None):
        self.model_name = model_name
        self.on_wake = on_wake or (lambda: None)
        self._running = False
        self._oww = None

    def available(self):
        try:
            import openwakeword  # noqa: F401
            import sounddevice  # noqa: F401
            return True
        except Exception:
            return False

    def start(self):
        """Block and listen; call on_wake() on each detection."""
        from openwakeword.model import Model
        import sounddevice as sd
        self._oww = Model(wakeword_models=[self.model_name])
        self._running = True
        print(f"[wake] listening for '{self.model_name}' …")
        with sd.InputStream(samplerate=SAMPLE_RATE, channels=1, dtype="int16",
                            blocksize=CHUNK) as stream:
            while self._running:
                audio, _ = stream.read(CHUNK)
                scores = self._oww.predict(np.frombuffer(audio, dtype=np.int16))
                for name, score in scores.items():
                    if score > 0.5:
                        print(f"[wake] detected '{name}' ({score:.2f})")
                        self.on_wake()
                        self._oww.reset()

    def stop(self):
        self._running = False
