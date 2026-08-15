package app.pinimage.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Keeps app-generated images outside cache so persisted pins remain valid. */
object PersistentImageStore {
    private fun directory(context: Context): File = File(context.filesDir, "pin_images").apply { mkdirs() }

    fun createFile(context: Context, prefix: String, extension: String = "png"): File =
        File(directory(context), "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")

    suspend fun ensurePersistent(context: Context, rawUri: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(rawUri)
        val extension = when {
            isPdf(context, uri) -> "pdf"
            isEpub(context, uri) -> "epub"
            else -> "png"
        }
        val persistentRoot = directory(context).canonicalFile
        if (uri.scheme == "file") {
            val source = File(uri.path ?: return@withContext rawUri)
            val canonicalSource = runCatching { source.canonicalFile }.getOrNull() ?: return@withContext rawUri
            if (canonicalSource.toPath().startsWith(persistentRoot.toPath())) return@withContext rawUri
            if (!canonicalSource.exists()) return@withContext rawUri

            val target = createFile(context, "pin", extension)
            return@withContext runCatching {
                canonicalSource.inputStream().use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                Uri.fromFile(if (extension in setOf("pdf", "epub")) deduplicateDocument(context, target, extension) else target).toString()
            }.getOrElse {
                target.delete()
                rawUri
            }
        }
        if (uri.scheme != "content") return@withContext rawUri

        val target = createFile(context, "pin", extension)
        runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching rawUri
            input.use {
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            Uri.fromFile(if (extension in setOf("pdf", "epub")) deduplicateDocument(context, target, extension) else target).toString()
        }.getOrElse {
            target.delete()
            rawUri
        }
    }

    fun isPdf(context: Context, uri: Uri): Boolean {
        if (context.contentResolver.getType(uri)?.equals("application/pdf", ignoreCase = true) == true) return true
        if (uri.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true) return true
        return runCatching {
            val header = ByteArray(5)
            val count = if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: 0
            } else {
                File(uri.path ?: return@runCatching false).inputStream().use { it.read(header) }
            }
            count == 5 && header.contentEquals("%PDF-".toByteArray())
        }.getOrDefault(false)
    }

    fun isEpub(context: Context, uri: Uri): Boolean {
        if (context.contentResolver.getType(uri)?.equals("application/epub+zip", ignoreCase = true) == true) return true
        if (uri.lastPathSegment?.endsWith(".epub", ignoreCase = true) == true) return true
        return runCatching {
            val file = if (uri.scheme == "file") File(uri.path ?: return@runCatching false) else return@runCatching false
            java.util.zip.ZipFile(file).use { zip ->
                zip.getEntry("mimetype")?.let { entry ->
                    zip.getInputStream(entry).bufferedReader().use { it.readText().trim() == "application/epub+zip" }
                } ?: false
            }
        }.getOrDefault(false)
    }

    private fun deduplicateDocument(context: Context, temporary: File, extension: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
        temporary.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val stable = File(directory(context), "${extension}_$hash.$extension")
        if (stable.exists()) {
            temporary.delete()
            return stable
        }
        return if (temporary.renameTo(stable)) stable else temporary
    }
}
