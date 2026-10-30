package com.samir.sahayak

/** A command the phone can handle itself, with an optional slot (app name, note…). */
data class PhoneIntent(val name: String, val slot: String = "")

/**
 * On-device intent matching — English + Nepali. Pure Kotlin, no dependencies.
 * Anything this returns null for is "not understood on the phone".
 */
object SahayakIntents {

    private fun hasDevanagari(t: String) = t.any { it in '\u0900'..'\u097F' }

    private fun slotOf(text: String, vararg patterns: String): String {
        for (p in patterns) {
            val m = Regex(p, RegexOption.IGNORE_CASE).find(text) ?: continue
            // first non-empty capture group wins
            val g = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            if (g != null) return g.trim().trim('.', ',', '!', '?')
        }
        return ""
    }

    fun match(raw: String): PhoneIntent? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val ne = hasDevanagari(t)
        val l = t.lowercase()

        fun has(vararg words: String) = words.any { w -> Regex(w).containsMatchIn(l) }

        // --- time & date ---
        if (has("what.*time|current time|time now") || (ne && has("समय|कति बज्यो")))
            return PhoneIntent("time")
        if (has("what.*date|today's date|what day") || (ne && has("मिति|कति गते|कुन बार")))
            return PhoneIntent("date")

        // --- torch ---
        if (has("torch|flashlight") || (ne && has("टर्च|बत्ती"))) {
            val off = has("off|बन्द")
            return PhoneIntent(if (off) "torch_off" else "torch_on")
        }

        // --- volume ---
        if (has("volume up|increase.*volume|louder") || (ne && has("भोल्युम बढा|आवाज बढा")))
            return PhoneIntent("volume_up")
        if (has("volume down|decrease.*volume|quieter") || (ne && has("भोल्युम घटा|आवाज घटा")))
            return PhoneIntent("volume_down")
        if (has("mute") || (ne && has("म्यूट|आवाज बन्द")))
            return PhoneIntent("volume_mute")

        // --- brightness ---
        if (has("brightness up|brighter") || (ne && has("ब्राइटनेस बढा|उज्यालो बढा")))
            return PhoneIntent("brightness_up")
        if (has("brightness down|dimmer") || (ne && has("ब्राइटनेस घटा|उज्यालो घटा")))
            return PhoneIntent("brightness_down")

        // --- wifi / bluetooth (system-restricted: open panels) ---
        if (has("wifi|wi-fi") || (ne && has("वाइफाइ"))) {
            val off = has("off|बन्द")
            return PhoneIntent(if (off) "wifi_off" else "wifi_on")
        }
        if (has("bluetooth") || (ne && has("ब्लुटुथ"))) {
            val off = has("off|बन्द")
            return PhoneIntent(if (off) "bluetooth_off" else "bluetooth_on")
        }

        // --- apps ---
        if (has("\\bopen\\b|\\blaunch\\b") || (ne && has("खोल"))) {
            val app = slotOf(t, "\\bopen (.+)", "\\blaunch (.+)", "(.+) खोल")
            return PhoneIntent("open_app", app)
        }

        // --- alarm ---
        if (has("alarm") || (ne && has("अलार्म")))
            return PhoneIntent("set_alarm", t)

        // --- do not disturb ---
        if (has("do not disturb|dnd|silent mode") || (ne && has("डिस्टर्ब"))) {
            val off = has("off|बन्द")
            return PhoneIntent(if (off) "dnd_off" else "dnd_on")
        }

        // --- battery ---
        if (has("battery") || (ne && has("ब्याट्री")))
            return PhoneIntent("battery")

        // --- notes ---
        if (has("\\bnote\\b|remember") || (ne && has("नोट|याद राख"))) {
            val note = slotOf(
                t,
                "(?:take|make) a note(?: that)? (.+)", "\\bremember (.+)",
                "\\bnote (.+)", "नोट गर (.+)", "याद राख (.+)",
            ).removePrefix("to ").trim()
            return PhoneIntent("note", note)
        }

        // --- call ---
        if (has("\\bcall\\b") || (ne && has("फोन गर|कल गर"))) {
            val who = slotOf(t, "\\bcall (.+)", "(.+)लाई (?:फोन|कल) गर", "(.+) (?:फोन|कल) गर")
            return PhoneIntent("call", who)
        }

        // --- help ---
        if (has("\\bhelp\\b|what can you do") || (ne && has("के के गर्न सक्छौ|सहयोग")))
            return PhoneIntent("help")

        return null
    }
}
