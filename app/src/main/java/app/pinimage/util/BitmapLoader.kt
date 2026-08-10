package app.pinimage.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Simple in-memory bitmap loader. Samples bitmaps down to a sensible max
 * dimension so multiple pins don't OOM the process. Caches the decoded
 * bitmap keyed by URI string.
 */
object BitmapLoader {

    private const val MAX_DIMENSION = 2048

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun evict(uri: String) {
        synchronized(cache) { cache.remove(uri) }
    }

    suspend fun load(context: Context, uri: String): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache.get(uri) }?.let { return@withContext it }
        val bitmap = decode(context, uri) ?: return@withContext null
        synchronized(cache) { cache.put(uri, bitmap) }
        bitmap
    }

    private fun decode(context: Context, uri: String): Bitmap? {
        return try {
            if (uri.startsWith("content://")) {
                val resolver = context.contentResolver
                resolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, bounds)
                    val sample = calculateSample(bounds.outWidth, bounds.outHeight)
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, opts)
                }
            } else {
                val file = File(uri.removePrefix("file://"))
                if (!file.exists()) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val sample = calculateSample(bounds.outWidth, bounds.outHeight)
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateSample(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w > MAX_DIMENSION || h > MAX_DIMENSION) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }
}
