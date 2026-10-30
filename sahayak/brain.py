"""The brain: text in -> intent -> action -> reply text out."""
import os

from . import actions_windows
from .config import Config
from .intents import detect_lang, match
from .stt import HybridSTT
from .tts import HybridTTS


class Brain:
    def __init__(self, cfg=None, dry_run=False):
        self.cfg = cfg or Config()
        self.dry_run = dry_run
        self.stt = HybridSTT(self.cfg)
        self.tts = HybridTTS(self.cfg)

    def _lang(self, text):
        pref = self.cfg["language"]
        return pref if pref in ("en", "ne") else detect_lang(text)

    def handle_text(self, text):
        """Process a command string, return the reply string."""
        text = (text or "").strip()
        if not text:
            return "", None, {}
        lang = self._lang(text)
        intent, slots, _ = match(text)

        if intent in ("speak_english", "speak_nepali"):
            self.cfg["language"] = "en" if intent == "speak_english" else "ne"
            reply = ("I'll speak English now." if intent == "speak_english"
                     else "अब म नेपालीमा बोल्छु।")
            return reply, intent, {}

        if intent:
            reply = actions_windows.do(intent, slots, lang=lang, cfg=self.cfg)
            return reply, intent, slots

        reply = self._chat_fallback(text, lang)
        return reply, None, {}

    def _chat_fallback(self, text, lang):
        key = self.cfg["openai_api_key"] or os.environ.get("OPENAI_API_KEY", "")
        if key:
            try:
                from openai import OpenAI
                client = OpenAI(api_key=key)
                sys = ("You are Sahayak, a concise bilingual voice assistant. "
                       "Reply in Nepali if the user wrote in Nepali, else English. "
                       "Keep replies under 30 words — they will be spoken aloud.")
                out = client.chat.completions.create(
                    model="gpt-4o-mini",
                    messages=[{"role": "system", "content": sys},
                              {"role": "user", "content": text}],
                    max_tokens=80)
                return (out.choices[0].message.content or "").strip()
            except Exception as e:
                print(f"[brain] chat fallback failed: {e}")
        if lang == "ne":
            return "मैले बुझिनँ। 'सहयोग' भन्नुहोस्, म के गर्न सक्छु बताउँछु।"
        return "I didn't catch that. Say 'help' and I'll list what I can do."

    def speak(self, text, lang="en"):
        if self.dry_run:
            print(f"[speak:{lang}] {text}")
        else:
            self.tts.speak(text, lang=lang)
