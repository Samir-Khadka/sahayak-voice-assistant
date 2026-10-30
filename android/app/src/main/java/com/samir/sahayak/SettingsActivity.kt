package com.samir.sahayak

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/** Server URL, token, and language — stored in SharedPreferences. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = getSharedPreferences("sahayak", MODE_PRIVATE)

        val urlBox = findViewById<EditText>(R.id.urlBox)
        val tokenBox = findViewById<EditText>(R.id.tokenBox)
        val langGroup = findViewById<RadioGroup>(R.id.langGroup)

        urlBox.setText(prefs.getString("server_url", "http://192.0.2.1:8770"))
        tokenBox.setText(prefs.getString("server_token", ""))
        when (prefs.getString("language", "auto")) {
            "en" -> langGroup.check(R.id.langEn)
            "ne" -> langGroup.check(R.id.langNe)
            else -> langGroup.check(R.id.langAuto)
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val lang = when (langGroup.checkedRadioButtonId) {
                R.id.langEn -> "en"
                R.id.langNe -> "ne"
                else -> "auto"
            }
            prefs.edit()
                .putString("server_url", urlBox.text.toString().trim().trimEnd('/'))
                .putString("server_token", tokenBox.text.toString().trim())
                .putString("language", lang)
                .apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
