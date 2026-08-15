package app.pinimage.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.pinimage.R
import app.pinimage.float.MediaKind
import java.io.File
import java.security.MessageDigest

/** Human-readable metadata for files copied into the private local library. */
class LibraryMetadataStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("library_metadata", Context.MODE_PRIVATE)

    fun put(uri: String, displayName: String, kind: MediaKind) {
        val key = key(uri)
        preferences.edit()
            .putString("${key}_name", displayName)
            .putString("${key}_kind", kind.name)
            .apply()
    }

    fun rename(uri: String, displayName: String) {
        val cleaned = displayName.trim()
        if (cleaned.isNotEmpty()) preferences.edit().putString("${key(uri)}_name", cleaned).apply()
    }

    fun storedDisplayName(uri: String): String? =
        preferences.getString("${key(uri)}_name", null)?.takeIf { it.isNotBlank() }

    fun displayName(uri: String): String {
        return storedDisplayName(uri)
            ?: fallbackName(Uri.parse(uri))
    }

    fun mediaKind(uri: String): MediaKind? = preferences.getString("${key(uri)}_kind", null)
        ?.let { runCatching { MediaKind.valueOf(it) }.getOrNull() }

    fun resolveOriginalName(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
                }
            }.getOrNull()?.let { return it }
        }
        return fallbackName(uri)
    }

    private fun fallbackName(uri: Uri): String {
        val name = File(uri.path ?: "").name.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: return context.getString(R.string.untitled_file)
        return when {
            name.matches(Regex("(?:pin|board|screenshot)_\\d+_[0-9a-f-]+\\.png", RegexOption.IGNORE_CASE)) ->
                context.getString(R.string.library_image)
            name.matches(Regex("pdf_[0-9a-f]{32,}\\.pdf", RegexOption.IGNORE_CASE)) ->
                context.getString(R.string.pdf_document)
            name.matches(Regex("epub_[0-9a-f]{32,}\\.epub", RegexOption.IGNORE_CASE)) ->
                context.getString(R.string.epub_document)
            else -> name
        }
    }

    private fun key(uri: String): String = MessageDigest.getInstance("SHA-256")
        .digest(uri.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
