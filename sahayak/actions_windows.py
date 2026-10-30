"""Windows system actions. Every OS-specific import is guarded so this
module imports cleanly on other platforms (actions then report
'Windows only' instead of crashing)."""
import datetime
import os
import platform
import random
import re
import shutil
import subprocess
import webbrowser
from pathlib import Path

WINDOWS = platform.system() == "Windows"

# Common apps -> how to launch them on Windows
APP_MAP = {
    "chrome": r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    "edge": r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "notepad": "notepad.exe",
    "calculator": "calc.exe",
    "explorer": "explorer.exe",
    "file explorer": "explorer.exe",
    "terminal": "wt.exe",
    "cmd": "cmd.exe",
    "command prompt": "cmd.exe",
    "vscode": "code",
    "vs code": "code",
    "spotify": os.path.expandvars(r"%APPDATA%\Spotify\Spotify.exe"),
    "settings": "ms-settings:",
}

JOKES = {
    "en": [
        "Why do programmers prefer dark mode? Because light attracts bugs.",
        "I told my computer I needed a break. Now it won't stop sending me KitKat ads.",
        "Why did the developer go broke? He used up all his cache.",
    ],
    "ne": [
        "कम्प्युटरलाई किन चिसो मन पर्छ? किनभने त्यसलाई भाइरसबाट ज्वरो आउँछ!",
        "प्रोग्रामरको मनपर्ने चिया कुन हो? जाभा चिया!",
    ],
}


def _reply(en, ne, lang):
    return ne if lang == "ne" else en


def do(intent, slots, lang="en", cfg=None):
    """Execute an intent. Returns the reply text to speak."""
    _ = cfg
    if intent == "time":
        now = datetime.datetime.now().strftime("%I:%M %p")
        return _reply(f"It's {now}.", f"अहिले {now} बज्यो।", lang)
    if intent == "date":
        today = datetime.datetime.now().strftime("%A, %B %d, %Y")
        return _reply(f"Today is {today}.", f"आज {today} हो।", lang)
    if intent == "open_app":
        return _open_app(slots.get("app", ""), lang)
    if intent == "volume_up":
        _volume_keys("volumeup")
        return _reply("Volume up.", "भोल्युम बढाएँ।", lang)
    if intent == "volume_down":
        _volume_keys("volumedown")
        return _reply("Volume down.", "भोल्युम घटाएँ।", lang)
    if intent == "volume_mute":
        _volume_keys("volumemute")
        return _reply("Muted.", "म्यूट गरें।", lang)
    if intent == "brightness_up":
        _brightness(+20)
        return _reply("Brightness up.", "ब्राइटनेस बढाएँ।", lang)
    if intent == "brightness_down":
        _brightness(-20)
        return _reply("Brightness down.", "ब्राइटनेस घटाएँ।", lang)
    if intent == "screenshot":
        return _screenshot(lang)
    if intent == "lock":
        if WINDOWS:
            import ctypes
            ctypes.windll.user32.LockWorkStation()
            return _reply("Locked.", "लक गरें।", lang)
        return _not_windows(lang)
    if intent == "shutdown":
        if WINDOWS:
            os.system("shutdown /s /t 10 /c \"Sahayak: shutting down in 10 seconds\"")
            return _reply("Shutting down in 10 seconds.", "१० सेकेन्डमा शटडाउन हुँदैछ।", lang)
        return _not_windows(lang)
    if intent == "restart":
        if WINDOWS:
            os.system("shutdown /r /t 10")
            return _reply("Restarting in 10 seconds.", "१० सेकेन्डमा रिस्टार्ट हुँदैछ।", lang)
        return _not_windows(lang)
    if intent == "sleep_pc":
        if WINDOWS:
            os.system("rundll32.exe powrprof.dll,SetSuspendState 0,1,0")
            return _reply("Good night.", "शुभ रात्री।", lang)
        return _not_windows(lang)
    if intent == "battery":
        try:
            import psutil
            b = psutil.sensors_battery()
            if b:
                state = "charging" if b.power_plugged else "on battery"
                return _reply(f"Battery is at {int(b.percent)} percent, {state}.",
                              f"ब्याट्री {int(b.percent)} प्रतिशत छ।", lang)
        except Exception:
            pass
        return _reply("I couldn't read the battery.", "ब्याट्री पढ्न सकिनँ।", lang)
    if intent == "search_web":
        q = slots.get("query", "")
        webbrowser.open(f"https://www.google.com/search?q={q}")
        return _reply(f"Searching for {q}.", f"{q} खोज्दैछु।", lang)
    if intent == "note":
        return _note(slots.get("text", ""), lang, cfg)
    if intent == "weather":
        return _weather(lang)
    if intent == "joke":
        return random.choice(JOKES.get(lang, JOKES["en"]))
    if intent == "help":
        return _reply(
            "I can tell the time and date, open apps, control volume and brightness, "
            "take screenshots, lock or shut down the PC, check battery, search the web, "
            "take notes, and tell the weather. Try 'speak in Nepali' any time.",
            "म समय बताउन, एप खोल्न, भोल्युम र ब्राइटनेस मिलाउन, स्क्रिनसट लिन, "
            "कम्प्युटर लक वा शटडाउन गर्न, ब्याट्री हेर्न, वेब खोज्न र नोट लिन सक्छु।",
            lang)
    if intent == "stop_listening":
        return _reply("Going quiet. Say the wake word when you need me.",
                      "म चुप लाग्छु। चाहिँदा वेक वर्ड भन्नुहोस्।", lang)
    return _reply("I didn't catch that.", "मैले बुझिनँ।", lang)


def _not_windows(lang):
    return _reply("That only works on Windows.", "यो विन्डोजमा मात्र काम गर्छ।", lang)


def _sanitize_app_name(name):
    """Allow only safe characters; return '' if the name looks hostile."""
    key = (name or "").lower().strip()
    if not re.fullmatch(r"[a-z0-9][a-z0-9 _\-.]{0,40}", key):
        return ""
    return key


def _open_app(name, lang):
    key = _sanitize_app_name(name)
    if not key:
        return _reply("I can't open that.", "त्यो खोल्न मिल्दैन।", lang)
    if not WINDOWS:
        return _not_windows(lang)
    try:
        target = APP_MAP.get(key)
        if target:
            # Known app: open directly, never through a shell.
            if os.path.exists(target):
                os.startfile(target)  # noqa: SLF001
            elif target.endswith(":"):
                os.startfile(target)  # noqa: SLF001  (ms-settings:)
            else:
                found = shutil.which(target)
                if found:
                    subprocess.Popen([found], shell=False)
                else:
                    raise FileNotFoundError(target)
        else:
            # Unknown name: only launch if it resolves to a real executable.
            found = shutil.which(key)
            if not found:
                return _reply(f"I couldn't find an app called {name}.",
                              f"{name} नामको एप भेटिएन।", lang)
            subprocess.Popen([found], shell=False)
        return _reply(f"Opening {name}.", f"{name} खोल्दैछु।", lang)
    except Exception:
        return _reply(f"I couldn't open {name}.", f"{name} खोल्न सकिनँ।", lang)


def _volume_keys(key):
    try:
        import pyautogui
        pyautogui.press(key)
    except Exception:
        pass


def _brightness(delta):
    try:
        import screen_brightness_control as sbc
        cur = sbc.get_brightness(display=0)
        cur = cur[0] if isinstance(cur, list) else cur
        sbc.set_brightness(max(5, min(100, cur + delta)), display=0)
    except Exception:
        _volume_keys("brightnessup" if delta > 0 else "brightnessdown")


def _screenshot(lang):
    try:
        import pyautogui
        path = str(Path.home() / "Pictures" / f"sahayak_{datetime.datetime.now():%Y%m%d_%H%M%S}.png")
        Path(path).parent.mkdir(exist_ok=True)
        pyautogui.screenshot(path)
        return _reply(f"Screenshot saved to {path}.", f"स्क्रिनसट सेभ भयो।", lang)
    except Exception:
        return _reply("I couldn't take a screenshot.", "स्क्रिनसट लिन सकिनँ।", lang)


def _note(text, lang, cfg):
    if not text:
        return _reply("What should I note down?", "के नोट गर्ने?", lang)
    path = Path(cfg["notes_file"]) if cfg else Path.home() / "sahayak_notes.txt"
    with open(path, "a", encoding="utf-8") as f:
        f.write(f"{datetime.datetime.now():%Y-%m-%d %H:%M} — {text}\n")
    return _reply("Noted.", "नोट गरें।", lang)


def _weather(lang):
    try:
        import urllib.request
        req = urllib.request.Request("https://wttr.in/?format=3", headers={"User-Agent": "sahayak"})
        with urllib.request.urlopen(req, timeout=10) as r:
            report = r.read().decode().strip()
        return _reply(f"The weather: {report}.", f"मौसम: {report}।", lang)
    except Exception:
        return _reply("I couldn't reach the weather service. Are you online?",
                      "मौसम सेवा उपलब्ध छैन। इन्टरनेट छ?", lang)
