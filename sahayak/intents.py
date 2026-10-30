"""Bilingual (English/Nepali) intent matching.

match(text) -> (intent_name | None, slots: dict, lang: 'en'|'ne')
Language is detected by the presence of Devanagari characters; the other
language's patterns are still tried as a fallback so mixed speech works.
"""
import re

DEVANAGARI = re.compile(r"[\u0900-\u097F]")


def detect_lang(text):
    return "ne" if DEVANAGARI.search(text or "") else "en"


def _slot(pattern, text, group=1):
    m = re.search(pattern, text, re.IGNORECASE)
    if not m:
        return ""
    if group == "any":
        for g in m.groups():
            if g:
                return g.strip(" .,!?")
        return ""
    val = m.group(group)
    return val.strip(" .,!?") if val else ""


# Each entry: (intent, [english patterns], [nepali patterns], slot_extractor or None)
# Slot extractors return a dict and are tried on the original text.
INTENTS = [
    ("time",
     [r"\bwhat('s| is) the time\b", r"\bwhat time is it\b", r"\btell me the time\b",
      r"\bcurrent time\b", r"\btime now\b"],
     [r"समय", r"कति बज्यो", r"टाइम कति", r"बज्यो"],
     None),
    ("date",
     [r"\bwhat('s| is) (the |today's )?date\b", r"\btoday's date\b", r"\bwhat day is it\b"],
     [r"आज कति गते", r"मिति कति", r"आज कुन बार", r"गते कति"],
     None),
    ("open_app",
     [r"\bopen (.+)", r"\blaunch (.+)", r"\bstart (.+)"],
     [r"(.+) खोल", r"(.+) खोलिदेऊ"],
     lambda t: {"app": _slot(r"\bopen (.+)|\blaunch (.+)|\bstart (.+)|(.+) खोल", t, "any")}),
    ("volume_up",
     [r"\bvolume up\b", r"\bincrease (the )?volume\b", r"\blouder\b", r"\bturn (it )?up\b"],
     [r"भोल्युम बढाऊ", r"आवाज बढाऊ", r"भोलुम बढा"],
     None),
    ("volume_down",
     [r"\bvolume down\b", r"\bdecrease (the )?volume\b", r"\bquieter\b", r"\bturn (it )?down\b"],
     [r"भोल्युम घटाऊ", r"आवाज घटाऊ", r"भोलुम घटा"],
     None),
    ("volume_mute",
     [r"\bmute( the volume)?\b", r"\bunmute\b"],
     [r"म्यूट गर", r"आवाज बन्द गर", r"साउन्ड बन्द"],
     None),
    ("brightness_up",
     [r"\bbrightness up\b", r"\bincrease brightness\b", r"\bbrighter\b"],
     [r"ब्राइटनेस बढाऊ", r"उज्यालो बढाऊ"],
     None),
    ("brightness_down",
     [r"\bbrightness down\b", r"\bdecrease brightness\b", r"\bdimmer\b"],
     [r"ब्राइटनेस घटाऊ", r"उज्यालो घटाऊ"],
     None),
    ("screenshot",
     [r"\bscreenshot\b", r"\btake a (screenshot|screen shot)\b", r"\bcapture (the )?screen\b"],
     [r"स्क्रिनसट", r"स्क्रिनशट लेऊ"],
     None),
    ("lock",
     [r"\block( the| my)? (pc|computer|laptop|screen)?\b"],
     [r"लक गर", r"कम्प्युटर लक"],
     None),
    ("shutdown",
     [r"\bshut ?down\b", r"\bturn off (the |my )?(computer|pc|laptop)\b", r"\bpower off\b"],
     [r"शटडाउन गर", r"कम्प्युटर बन्द गर"],
     None),
    ("restart",
     [r"\brestart\b", r"\breboot\b"],
     [r"रिस्टार्ट गर", r"रिबुट गर"],
     None),
    ("sleep_pc",
     [r"\bsleep\b", r"\bhibernate\b", r"\bput .* to sleep\b"],
     [r"स्लिप गर", r"सुताऊ"],
     None),
    ("battery",
     [r"\bbattery\b"],
     [r"ब्याट्री"],
     None),
    ("search_web",
     [r"\bsearch( the web)? for (.+)", r"\bgoogle (.+)", r"\blook up (.+)"],
     [r"(.+) खोज", r"(.+) सर्च गर"],
     lambda t: {"query": _slot(r"\bsearch(?: the web)? for (.+)|\bgoogle (.+)|\blook up (.+)|(.+) खोज|(.+) सर्च गर", t, "any")}),
    ("note",
     [r"\b(take|make) a note( that)? (.+)", r"\bremember (.+)", r"\bnote (.+)"],
     [r"नोट गर (.+)", r"याद राख (.+)"],
     lambda t: {"text": _slot(r"\b(?:take|make) a note(?: that)? (.+)|\bremember (.+)|\bnote (.+)|नोट गर (.+)|याद राख (.+)", t, "any").removeprefix("to ").strip()}),
    ("weather",
     [r"\bweather\b"],
     [r"मौसम"],
     None),
    ("joke",
     [r"\btell me a joke\b", r"\bjoke\b"],
     [r"चुट्किला", r"जोक सुनाऊ"],
     None),
    ("help",
     [r"\bhelp\b", r"\bwhat can you do\b"],
     [r"के के गर्न सक्छौ", r"सहयोग"],
     None),
    ("stop_listening",
     [r"\bgo to sleep\b", r"\bstop listening\b"],
     [r"अब नबोल", r"सुत"],
     None),
    ("speak_english",
     [r"\bspeak (in )?english\b"],
     [r"अंग्रेजीमा बोल"],
     None),
    ("speak_nepali",
     [r"\bspeak (in )?nepali\b"],
     [r"नेपालीमा बोल"],
     None),
]


def match(text):
    """Return (intent, slots, lang)."""
    text = (text or "").strip()
    if not text:
        return None, {}, "en"
    lang = detect_lang(text)
    order = [lang] + (["ne"] if lang == "en" else ["en"])
    for intent, en_pats, ne_pats, extract in INTENTS:
        pats = {"en": en_pats, "ne": ne_pats}
        for lg in order:
            for pat in pats[lg]:
                if re.search(pat, text, re.IGNORECASE):
                    slots = extract(text) if extract else {}
                    return intent, slots, lang
    return None, {}, lang
