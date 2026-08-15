package app.pinimage.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.pinimage.float.EpubBookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberEpubThumbnail(uri: String): Bitmap? {
    val context = LocalContext.current
    val result: State<Bitmap?> = produceState(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            val cover = EpubBookSource.prepare(context, uri)?.coverFile ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(cover.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 640) sample *= 2
            BitmapFactory.decodeFile(cover.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    return result.value
}
