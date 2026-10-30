package com.samir.sahayak

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Executes on-device intents. Returns the reply to speak.
 * Actions the OS restricts (wifi toggle on Android 10+, etc.) open the right
 * settings panel instead of pretending — honest over fake.
 */
class DeviceActions(private val activity: AppCompatActivity) {

    private val ctx: Context get() = activity

    fun execute(intent: PhoneIntent, nepali: Boolean = false): String {
        lastNe = nepali
        return when (intent.name) {
            "time" -> reply("It's ${now("hh:mm a")}.", "अहिले ${now("hh:mm a")} बज्यो।")
            "date" -> reply("Today is ${now("EEEE, MMMM d")}.", "आज ${now("EEEE, MMMM d")} हो।")
            "torch_on" -> { setTorch(true); reply("Torch on.", "टर्च बालें।") }
            "torch_off" -> { setTorch(false); reply("Torch off.", "टर्च निभाएँ।") }
            "volume_up" -> { adjustVolume(AudioManager.ADJUST_RAISE); reply("Volume up.", "भोल्युम बढाएँ।") }
            "volume_down" -> { adjustVolume(AudioManager.ADJUST_LOWER); reply("Volume down.", "भोल्युम घटाएँ।") }
            "volume_mute" -> { mute(); reply("Muted.", "म्यूट गरें।") }
            "brightness_up" -> changeBrightness(+30)
            "brightness_down" -> changeBrightness(-30)
            "wifi_on", "wifi_off" -> openPanel(
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY,
                "Android doesn't let apps flip wifi directly — opened the wifi panel for you.",
                "वाइफाइ प्यानल खोलें।")
            "bluetooth_on" -> setBluetooth(true)
            "bluetooth_off" -> setBluetooth(false)
            "open_app" -> openApp(intent.slot)
            "set_alarm" -> setAlarm(intent.slot)
            "dnd_on" -> setDnd(true)
            "dnd_off" -> setDnd(false)
            "battery" -> battery()
            "note" -> saveNote(intent.slot)
            "call" -> dial(intent.slot)
            "help" -> reply(
                "I can tell the time, toggle the torch, change volume and brightness, " +
                    "open apps, set alarms, flip do-not-disturb, check battery, take notes, " +
                    "and dial numbers — in English or Nepali.",
                "म समय बताउन, टर्च बाल्न, भोल्युम-ब्राइटनेस मिलाउन, एप खोल्न, " +
                    "अलार्म लगाउन, ब्याट्री हेर्न र नोट लिन सक्छु।")
            else -> reply("I can't do that yet.", "यो गर्न सक्दिनँ।")
        }
    }

    // ---------- helpers ----------
    private var lastNe = false
    private fun reply(en: String, ne: String): String = if (lastNe) ne else en

    private fun now(fmt: String) = SimpleDateFormat(fmt, Locale.US).format(Date())

    private fun setTorch(on: Boolean) {
        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            cm.cameraIdList.firstOrNull()?.let { cm.setTorchMode(it, on) }
        } catch (_: Exception) { }
    }

    private fun adjustVolume(dir: Int) {
        val am = ctx.getSystemService(AudioManager::class.java)
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
    }

    private fun mute() {
        val am = ctx.getSystemService(AudioManager::class.java)
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
    }

    private fun changeBrightness(delta: Int): String {
        if (!Settings.System.canWrite(ctx)) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return reply(
                "I need permission to change brightness — opened settings for you.",
                "ब्राइटनेसका लागि अनुमति चाहियो — सेटिङ खोलें।")
        }
        return try {
            val cur = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            val next = (cur + delta).coerceIn(10, 255)
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, next)
            // Manual mode so the value sticks
            Settings.System.putInt(ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            reply("Brightness ${if (delta > 0) "up" else "down"}.",
                if (delta > 0) "ब्राइटनेस बढाएँ।" else "ब्राइटनेस घटाएँ।")
        } catch (_: Exception) {
            reply("Couldn't change brightness.", "ब्राइटनेस बदल्न सकिनँ।")
        }
    }

    private fun openPanel(panelAction: String, en: String, ne: String): String {
        return try {
            ctx.startActivity(Intent(panelAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            reply(en, ne)
        } catch (_: Exception) {
            reply("Couldn't open settings.", "सेटिङ खोल्न सकिनँ।")
        }
    }

    @Suppress("DEPRECATION")
    private fun setBluetooth(on: Boolean): String {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) return reply("No bluetooth on this device.", "ब्लुटुथ छैन।")
            val changed = if (on) adapter.enable() else adapter.disable()
            if (changed) reply(
                if (on) "Bluetooth on." else "Bluetooth off.",
                if (on) "ब्लुटुथ खोलें।" else "ब्लुटुथ बन्द गरें।")
            else openPanel(Settings.ACTION_BLUETOOTH_SETTINGS,
                "Opened bluetooth settings — toggle it there.",
                "ब्लुटुथ सेटिङ खोलें।")
        } catch (_: SecurityException) {
            openPanel(Settings.ACTION_BLUETOOTH_SETTINGS,
                "Opened bluetooth settings — toggle it there.",
                "ब्लुटुथ सेटिङ खोलें।")
        }
    }

    private fun openApp(name: String): String {
        if (name.isBlank()) return reply("Which app?", "कुन एप?")
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull {
            it.loadLabel(pm).toString().contains(name, ignoreCase = true)
        }
        return if (hit != null) {
            val launch = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
            if (launch != null) {
                ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                reply("Opening ${hit.loadLabel(pm)}.", "${hit.loadLabel(pm)} खोल्दैछु।")
            } else reply("Couldn't open that app.", "एप खोल्न सकिनँ।")
        } else reply("No app found named $name.", "$name नामको एप भेटिएन।")
    }

    private fun setAlarm(text: String): String {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(text)
        return try {
            if (m != null) {
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                when (m.groupValues[3].lowercase()) {
                    "pm" -> if (h < 12) h += 12
                    "am" -> if (h == 12) h = 0
                }
                // Platform intent strings (== AlarmClock constants) to avoid a
                // compiler resolution quirk with android.app.AlarmClock.
                ctx.startActivity(Intent("android.intent.action.SET_ALARM").apply {
                    putExtra("android.intent.extra.alarm.HOUR", h)
                    putExtra("android.intent.extra.alarm.MINUTES", min)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                reply("Alarm set.", "अलार्म लगाएँ।")
            } else {
                ctx.startActivity(Intent("android.intent.action.SHOW_ALARMS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                reply("Opened your alarms.", "अलार्महरू खोलें।")
            }
        } catch (_: Exception) {
            reply("Couldn't set an alarm.", "अलार्म लगाउन सकिनँ।")
        }
    }

    private fun setDnd(on: Boolean): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return reply(
                "I need Do-Not-Disturb access — opened settings for you.",
                "डिस्टर्ब-मोडका लागि अनुमति चाहियो — सेटिङ खोलें।")
        }
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_NONE
        else NotificationManager.INTERRUPTION_FILTER_ALL)
        return reply(
            if (on) "Do not disturb on." else "Do not disturb off.",
            if (on) "डिस्टर्ब बन्द गरें।" else "डिस्टर्ब खोलें।")
    }

    private fun battery(): String {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return reply("Battery is at $pct percent.", "ब्याट्री $pct प्रतिशत छ।")
    }

    private fun saveNote(text: String): String {
        if (text.isBlank()) return reply("What should I note down?", "के नोट गर्ने?")
        return try {
            val f = File(ctx.filesDir, "sahayak_notes.txt")
            f.appendText("${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())} — $text\n")
            reply("Noted.", "नोट गरें।")
        } catch (_: Exception) {
            reply("Couldn't save the note.", "नोट सेभ गर्न सकिनँ।")
        }
    }

    private fun dial(who: String): String {
        val digits = who.filter { it.isDigit() || it == '+' }
        return if (digits.length >= 3) {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            reply("Dialling $digits.", "$digits डायल गर्दैछु।")
        } else reply("I need a phone number to dial.", "फोन नम्बर चाहियो।")
    }
}
