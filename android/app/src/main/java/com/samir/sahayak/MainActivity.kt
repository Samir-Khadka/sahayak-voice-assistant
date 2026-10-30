package com.samir.sahayak

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

/**
 * Sahayak on Android — a standalone voice assistant, like "hey google":
 * say "Jarvis" (always-on service) or tap the mic, and the phone obeys.
 *
 * Commands run ON THE DEVICE (torch, volume, apps, alarms, bluetooth…).
 * Optionally, misunderstood commands can be forwarded to the laptop brain
 * (Settings → off by default; the two are independent).
 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var actions: DeviceActions
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    private lateinit var statusText: TextView
    private lateinit var heardText: TextView
    private lateinit var replyText: TextView
    private lateinit var micButton: Button
    private lateinit var alwaysOnButton: Button
    private lateinit var typeBox: EditText
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("sahayak", MODE_PRIVATE)
        actions = DeviceActions(this)

        statusText = findViewById(R.id.statusText)
        heardText = findViewById(R.id.heardText)
        replyText = findViewById(R.id.replyText)
        micButton = findViewById(R.id.micButton)
        alwaysOnButton = findViewById(R.id.alwaysOnButton)
        typeBox = findViewById(R.id.typeBox)
        sendButton = findViewById(R.id.sendButton)

        micButton.setOnClickListener { startListening() }
        alwaysOnButton.setOnClickListener { toggleAlwaysOn() }
        sendButton.setOnClickListener {
            val t = typeBox.text.toString().trim()
            if (t.isNotEmpty()) handleCommand(t)
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        tts = TextToSpeech(this, this)
        ensurePermissions()
        refreshAlwaysOnLabel()

        if (intent.getBooleanExtra(WakeWordService.EXTRA_LISTEN, false)) {
            intent.removeExtra(WakeWordService.EXTRA_LISTEN)
            startListening()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(WakeWordService.EXTRA_LISTEN, false)) startListening()
    }

    override fun onResume() {
        super.onResume()
        refreshAlwaysOnLabel()
    }

    // ---------- always-on ----------
    private fun toggleAlwaysOn() {
        val key = prefs.getString("porcupine_key", "").orEmpty().trim()
        if (key.isEmpty()) {
            Toast.makeText(this,
                "Add your free Picovoice access key in Settings first.",
                Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val svc = Intent(this, WakeWordService::class.java)
        // No official "is running" query — track it ourselves.
        val on = prefs.getBoolean("always_on", false)
        if (on) {
            stopService(svc)
            prefs.edit().putBoolean("always_on", false).apply()
        } else {
            ContextCompat.startForegroundService(this, svc)
            prefs.edit().putBoolean("always_on", true).apply()
            Toast.makeText(this, "Always-on listening enabled — say “Jarvis”.",
                Toast.LENGTH_SHORT).show()
        }
        refreshAlwaysOnLabel()
    }

    private fun refreshAlwaysOnLabel() {
        val on = prefs.getBoolean("always_on", false)
        alwaysOnButton.text = if (on) "Always-on: ON" else "Always-on: OFF"
    }

    // ---------- speech output ----------
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("en", "IN")
    }

    private fun speak(text: String, nepali: Boolean) {
        val t = tts ?: return
        t.language = if (nepali) Locale("ne", "NP") else Locale("en", "IN")
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sahayak1")
    }

    private fun say(text: String, nepali: Boolean = false) {
        replyText.text = text
        statusText.text = ""
        speak(text, nepali)
    }

    // ---------- speech input ----------
    private fun ensurePermissions() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.BLUETOOTH_CONNECT
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Voice recognition isn't available — type your command below.")
            typeBox.visibility = View.VISIBLE
            sendButton.visibility = View.VISIBLE
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val heard = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (heard.isBlank()) say("I didn't catch that.")
                    else handleCommand(heard)
                }
                override fun onError(error: Int) { say("I didn't catch that. Try again.") }
                override fun onReadyForSpeech(p: Bundle?) { statusText.text = "Listening…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { statusText.text = "Thinking…" }
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        })
    }

    // ---------- command routing: phone first, laptop optional ----------
    private fun handleCommand(text: String) {
        heardText.text = "You: $text"
        statusText.text = "Thinking…"
        val nepali = text.any { it in '\u0900'..'\u097F' }
        val intent = SahayakIntents.match(text)
        if (intent != null) {
            say(actions.execute(intent, nepali), nepali)
            return
        }
        if (prefs.getBoolean("laptop_forward", false)) {
            forwardToLaptop(text, nepali)
        } else {
            say(if (nepali) "यो गर्न सक्दिनँ।" else "I can't do that on the phone yet.", nepali)
        }
    }

    private fun forwardToLaptop(text: String, nepali: Boolean) {
        val url = prefs.getString("server_url", "").orEmpty().trim().trimEnd('/')
        val token = prefs.getString("server_token", "").orEmpty()
        if (url.isEmpty() || token.isEmpty()) {
            say("Laptop forwarding is on but the server isn't configured.", nepali)
            return
        }
        val lang = prefs.getString("language", "auto") ?: "auto"
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Thread {
            try {
                val reply = SahayakClient(url, token)
                    .command(text, deviceId ?: "android", lang)
                runOnUiThread { say(reply.text, reply.lang == "ne") }
            } catch (_: Exception) {
                runOnUiThread { say("Couldn't reach your laptop.", nepali) }
            }
        }.start()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
