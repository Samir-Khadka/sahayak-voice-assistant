package com.samir.sahayak

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/** All Sahayak settings — stored in SharedPreferences. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = getSharedPreferences("sahayak", MODE_PRIVATE)

        val keyBox = findViewById<EditText>(R.id.keyBox)
        val urlBox = findViewById<EditText>(R.id.urlBox)
        val tokenBox = findViewById<EditText>(R.id.tokenBox)
        val forwardBox = findViewById<CheckBox>(R.id.forwardBox)
        val langGroup = findViewById<RadioGroup>(R.id.langGroup)

        keyBox.setText(prefs.getString("porcupine_key", ""))
        urlBox.setText(prefs.getString("server_url", "http://192.0.2.1:8770"))
        tokenBox.setText(prefs.getString("server_token", ""))
        forwardBox.isChecked = prefs.getBoolean("laptop_forward", false)
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
                .putString("porcupine_key", keyBox.text.toString().trim())
                .putString("server_url", urlBox.text.toString().trim().trimEnd('/'))
                .putString("server_token", tokenBox.text.toString().trim())
                .putBoolean("laptop_forward", forwardBox.isChecked)
                .putString("language", lang)
                .apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
