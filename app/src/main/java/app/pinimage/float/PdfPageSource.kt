package app.pinimage.float

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class PdfPageInfo(val width: Int, val height: Int)

/** A local, seekable PDF source. Page access is serialized as required by PdfRenderer. */
class PdfPageSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pages: List<PdfPageInfo>,
) : AutoCloseable {
    private val mutex = Mutex()

    suspend fun render(index: Int, targetWidth: Int): Bitmap = mutex.withLock {
        val info = pages[index]
        val width = targetWidth.coerceIn(1, MAX_RENDER_WIDTH)
        val height = (width * info.height.toFloat() / info.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        renderer.openPage(index).use { page ->
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
        bitmap
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }

    companion object {
        private const val MAX_RENDER_WIDTH = 2048

        fun open(context: Context, rawUri: String): PdfPageSource? = runCatching {
            val uri = Uri.parse(rawUri)
            val descriptor = if (uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")
            } else {
                ParcelFileDescriptor.open(File(uri.path ?: error("Missing PDF path")), ParcelFileDescriptor.MODE_READ_ONLY)
            } ?: return@runCatching null
            try {
                val renderer = PdfRenderer(descriptor)
                try {
                    val pages = (0 until renderer.pageCount).map { index ->
                        renderer.openPage(index).use { PdfPageInfo(it.width, it.height) }
                    }
                    PdfPageSource(descriptor, renderer, pages)
                } catch (error: Throwable) {
                    renderer.close()
                    throw error
                }
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
        }.getOrNull()
    }
}
