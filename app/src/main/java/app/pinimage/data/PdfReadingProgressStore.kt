package app.pinimage.data

import android.content.Context
import app.pinimage.float.ContentTransform
import java.security.MessageDigest

/** Keeps the last viewport for each local PDF even after its floating window is closed. */
class PdfReadingProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences("pdf_reading_progress", Context.MODE_PRIVATE)

    fun get(uri: String): ContentTransform? {
        val key = key(uri)
        if (!preferences.contains("${key}_zoom")) return null
        return ContentTransform(
            zoom = preferences.getFloat("${key}_zoom", 1f),
            offsetX = preferences.getFloat("${key}_x", 0f),
            offsetY = preferences.getFloat("${key}_y", 0f),
            rotation = preferences.getFloat("${key}_rotation", 0f),
        )
    }

    fun put(uri: String, transform: ContentTransform) {
        val key = key(uri)
        preferences.edit()
            .putFloat("${key}_zoom", transform.zoom)
            .putFloat("${key}_x", transform.offsetX)
            .putFloat("${key}_y", transform.offsetY)
            .putFloat("${key}_rotation", transform.rotation)
            .apply()
    }

    private fun key(uri: String): String = MessageDigest.getInstance("SHA-256")
        .digest(uri.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
