package app.pinimage.util

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.pinimage.float.PdfPageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberPdfThumbnail(uri: String): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            PdfPageSource.open(context, uri)?.use { source ->
                if (source.pages.isEmpty()) null else source.render(0, 512)
            }
        }
    }
    return bitmap
}
