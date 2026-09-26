package com.example.taskreminder.util

import android.content.Context
import android.os.Build
import com.example.taskreminder.BuildConfig
import com.example.taskreminder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Posts form submissions to the developer's Google Apps Script relay, which emails them.
 * Only what the user typed is sent, plus app version and Android version.
 */
object FeedbackSender {
    enum class Kind(val wire: String) { FEEDBACK("feedback") }

    private const val ALLOWED_HOST_PREFIX = "https://script.google.com/"
    private const val MAX_FIELD_CHARS = 2000

    /** False when this build has no relay configured (secrets.properties missing). */
    val isConfigured: Boolean
        get() = BuildConfig.FEEDBACK_ENDPOINT.startsWith(ALLOWED_HOST_PREFIX) && BuildConfig.FEEDBACK_TOKEN.isNotBlank()

    suspend fun send(context: Context, kind: Kind, fields: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext false
        val payload = JSONObject().apply {
            put("token", BuildConfig.FEEDBACK_TOKEN)
            put("kind", kind.wire)
            put("app", context.getString(R.string.app_name))
            put("version", runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "")
            put("android", Build.VERSION.RELEASE)
            put("installId", UserProfile.installId(context))
            fields.forEach { (key, value) -> put(key, value.trim().take(MAX_FIELD_CHARS)) }
        }

        val connection = URL(BuildConfig.FEEDBACK_ENDPOINT).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            // Apps Script answers with a redirect to the script's output; follow it
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode !in 200..299) return@withContext false
            val body = connection.inputStream.bufferedReader().use { it.readText().take(4096) }
            runCatching { JSONObject(body).optBoolean("ok", false) }.getOrDefault(false)
        } catch (e: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
