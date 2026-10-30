package com.samir.sahayak

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP client for the Sahayak laptop brain. No dependencies. */
class SahayakClient(
    private val baseUrl: String,
    private val token: String,
) {
    data class Reply(val text: String, val action: String?, val lang: String?)

    /** GET /v1/health — true when the laptop server answers. */
    fun ping(timeoutMs: Int = 4000): Boolean = try {
        val conn = (URL("$baseUrl/v1/health").openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs; readTimeout = timeoutMs
            requestMethod = "GET"
        }
        val ok = conn.responseCode == 200
        conn.disconnect()
        ok
    } catch (_: Exception) {
        false
    }

    /** POST /v1/command — throws on network/auth errors so callers can fall back. */
    @Throws(Exception::class)
    fun command(text: String, deviceId: String, lang: String): Reply {
        val conn = (URL("$baseUrl/v1/command").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Token", token)
        }
        val body = JSONObject()
            .put("text", text)
            .put("device_id", deviceId)
            .put("lang", lang)
            .toString()
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()

        if (code == 401 || code == 429) throw SecurityException("server refused ($code)")
        if (code !in 200..299) throw Exception("server error $code")
        val json = JSONObject(raw)
        fun optStr(key: String): String? =
            if (json.isNull(key)) null else json.optString(key, null)
        return Reply(
            text = json.optString("reply_text", ""),
            action = optStr("action"),
            lang = optStr("lang"),
        )
    }
}
