package com.samir.sahayak

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
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
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sahayak on Android: tap the mic, speak (English or Nepali), and the laptop
 * brain answers. Works on any Android 8.0+ device.
 *
 * If the laptop is unreachable, a few on-device actions still work offline:
 * torch on/off and the time — add more in [handleLocally].
 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var prefs: SharedPreferences
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private lateinit var statusText: TextView
    private lateinit var heardText: TextView
    private lateinit var replyText: TextView
    private lateinit var micButton: Button
    private lateinit var typeBox: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("sahayak", MODE_PRIVATE)

        statusText = findViewById(R.id.statusText)
        heardText = findViewById(R.id.heardText)
        replyText = findViewById(R.id.replyText)
        micButton = findViewById(R.id.micButton)
        typeBox = findViewById(R.id.typeBox)
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val t = typeBox.text.toString().trim()
            if (t.isNotEmpty()) askBrain(t)
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        micButton.setOnClickListener { startListening() }

        tts = TextToSpeech(this, this)
        ensureMicPermission()
    }

    // ---------- speech output ----------
    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        // Prefer a Nepali voice when the reply is Nepali; handled per-utterance.
        tts?.language = Locale("en", "IN")
    }

    private fun speak(text: String, lang: String?) {
        val t = tts ?: return
        val locale = if (lang == "ne" || text.any { it in '\u0900'..'\u097F' })
            Locale("ne", "NP") else Locale("en", "IN")
        // If the exact voice is missing, TTS falls back gracefully.
        t.language = locale
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sahayak1")
    }

    // ---------- speech input ----------
    private fun ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Voice recognition isn't available — type your command below.")
            typeBox.visibility = View.VISIBLE
            findViewById<Button>(R.id.sendButton).visibility = View.VISIBLE
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val heard = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (heard.isBlank()) say("I didn't catch that.")
                    else askBrain(heard)
                }
                override fun onError(error: Int) { say("I didn't catch that. Try again.") }
                override fun onReadyForSpeech(p: Bundle?) { status("Listening…") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { status("Thinking…") }
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Hint both languages; the recognizer picks what it hears.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ne-NP")
        }
        recognizer?.startListening(intent)
    }

    // ---------- brain ----------
    private fun prefs_(): Triple<String, String, String> = Triple(
        prefs.getString("server_url", "http://192.0.2.1:8770")!!.trim().trimEnd('/'),
        prefs.getString("server_token", "")!!,
        prefs.getString("language", "auto")!!,
    )

    private fun askBrain(text: String) {
        heardText.text = "You: $text"
        status("Thinking…")
        val (url, token, lang) = prefs_()
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Thread {
            try {
                val client = SahayakClient(url, token)
                val reply = client.command(text, deviceId ?: "android", lang)
                runOnUiThread { say(reply.text, reply.lang) }
            } catch (se: SecurityException) {
                runOnUiThread { say("The laptop refused the connection. Check the token in settings.") }
            } catch (_: Exception) {
                // Laptop unreachable — try the on-device fallbacks.
                val local = handleLocally(text)
                runOnUiThread {
                    if (local != null) say(local.first, local.second)
                    else say("I can't reach your laptop. It's either offline or the address in settings is wrong.")
                }
            }
        }.start()
    }

    /** On-device actions that work with no laptop. Returns (reply, lang) or null. */
    private fun handleLocally(text: String): Pair<String, String?>? {
        val t = text.lowercase(Locale.US)
        val ne = text.any { it in '\u0900'..'\u097F' }
        val lang = if (ne) "ne" else "en"
        if (Regex("torch|flashlight|टर्च|बत्ती").containsMatchIn(t)) {
            val on = !t.contains(Regex("off|बन्द"))
            setTorch(on)
            return (if (ne) (if (on) "टर्च बालें।" else "टर्च निभाएँ।")
                    else (if (on) "Torch on." else "Torch off.")) to lang
        }
        if (Regex("what.*time|समय|कति बज्यो").containsMatchIn(t)) {
            val now = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
            return (if (ne) "अहिले $now बज्यो।" else "It's $now.") to lang
        }
        return null
    }

    private fun setTorch(on: Boolean) {
        try {
            val cm = getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) cm.setTorchMode(id, on)
        } catch (_: Exception) { }
    }

    private fun say(text: String, lang: String? = null) {
        status(if (text.length > 60) "Answered." else "")
        replyText.text = text
        speak(text, lang)
    }

    private fun status(s: String) = runOnUiThread { statusText.text = s }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
