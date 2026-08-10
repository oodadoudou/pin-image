package app.pinimage.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

suspend fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    title: String,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
): Uri? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val resolver = context.contentResolver
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val extension = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
    val mimeType = if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$title.$extension")
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PinImage")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(collection, values) ?: return@withContext null
    resolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(format, if (format == Bitmap.CompressFormat.JPEG) 92 else 100, out)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val pending = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, pending, null, null)
    }
    uri
}
