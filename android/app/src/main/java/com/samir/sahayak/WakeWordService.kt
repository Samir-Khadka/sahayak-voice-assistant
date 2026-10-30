package com.samir.sahayak

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineWakeWordCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Always-on "Jarvis" listener. Runs as a foreground service (persistent
 * notification) — the legitimate way to hold the mic on Android.
 *
 * Needs a free Picovoice access key (paste it in Settings; get one at
 * console.picovoice.ai — free for personal use). Without a key the service
 * refuses to start and the app falls back to the tap-to-talk mic button.
 *
 * On wake: fires a full-screen notification that opens MainActivity and
 * starts listening immediately — the "hey google" feel.
 */
class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "sahayak_wake"
        const val ALERT_CHANNEL_ID = "sahayak_alert"
        const val NOTIF_ID = 41
        const val WAKE_NOTIF_ID = 42
        const val EXTRA_LISTEN = "com.samir.sahayak.LISTEN"
    }

    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sahayak listening",
                NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Sahayak wake alert",
                NotificationManager.IMPORTANCE_HIGH))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val key = getSharedPreferences("sahayak", MODE_PRIVATE)
            .getString("porcupine_key", "").orEmpty().trim()
        if (key.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, persistentNotification())
        if (!running) {
            running = true
            worker = Thread(::listenLoop, "sahayak-wake").apply { start() }
        }
        return START_STICKY
    }

    private fun persistentNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sahayak is listening")
            .setContentText("Say “Jarvis” any time")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun listenLoop() {
        var porcupine: Porcupine? = null
        var audioRecord: AudioRecord? = null
        try {
            val key = getSharedPreferences("sahayak", MODE_PRIVATE)
                .getString("porcupine_key", "").orEmpty().trim()
            porcupine = Porcupine.Builder()
                .setAccessKey(key)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setWakeWordCallback(object : PorcupineWakeWordCallback {
                    override fun invoke() = onWakeWord()
                })
                .build(applicationContext)

            val frameLength = porcupine.frameLength
            val sampleRate = porcupine.sampleRate
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                frameLength * 2 * 4,
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) return
            audioRecord.startRecording()
            val buffer = ShortArray(frameLength)
            while (running) {
                val n = audioRecord.read(buffer, 0, frameLength)
                if (n == frameLength) porcupine.process(buffer)
            }
        } catch (_: Exception) {
            // Bad key / no mic: service just stops; mic button still works.
        } finally {
            try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) { }
            try { porcupine?.delete() } catch (_: Exception) { }
            running = false
            stopSelf()
        }
    }

    private fun onWakeWord() {
        // Full-screen intent: the sanctioned way to surface from background.
        val full = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_LISTEN, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            this, 1, full,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Jarvis?")
            .setContentText("Listening…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pending, true)
            .setAutoCancel(true)
            .build()
        nm.notify(WAKE_NOTIF_ID, notif)
    }

    override fun onDestroy() {
        running = false
        worker?.join(1000)
        super.onDestroy()
    }
}
