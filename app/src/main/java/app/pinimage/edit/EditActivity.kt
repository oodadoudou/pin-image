package app.pinimage.edit

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import app.pinimage.util.BitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EditActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var cropView: CropView
    private val scope = CoroutineScope(Dispatchers.Main)

    private var source: Bitmap? = null
    private var current: Bitmap? = null
    private var pendingRotation = 0
    private var flipH = false
    private var flipV = false
    private var sourceUri: String? = null
    private var itemId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent.getStringExtra(EXTRA_URI)
        itemId = intent.getStringExtra(EXTRA_ITEM_ID)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        cropView = CropView(this)
        root.addView(
            cropView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val toolbar = HorizontalScrollView(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(8, 8, 8, 8) }
        toolbar.addView(row)
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addButton(row, "Reset") { reset() }
        addButton(row, "Free") { cropView.setAspect(0f) }
        addButton(row, "1:1") { cropView.setAspect(1f) }
        addButton(row, "4:3") { cropView.setAspect(4f / 3f) }
        addButton(row, "3:4") { cropView.setAspect(3f / 4f) }
        addButton(row, "16:9") { cropView.setAspect(16f / 9f) }
        addButton(row, "Rotate L") { rotate(-90f) }
        addButton(row, "Rotate R") { rotate(90f) }
        addButton(row, "Flip H") { flipH = !flipH; applyTransform() }
        addButton(row, "Flip V") { flipV = !flipV; applyTransform() }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(12, 8, 12, 16) }
        val cancel = Button(this).apply { text = "Cancel"; setOnClickListener { finish() } }
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
        val done = Button(this).apply { text = "Done"; setOnClickListener { applyAndFinish() } }
        bottom.addView(cancel)
        bottom.addView(spacer)
        bottom.addView(done)
        root.addView(bottom)

        setContentView(root)

        load()
    }

    private fun addButton(row: LinearLayout, label: String, action: () -> Unit) {
        val btn = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        row.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun load() {
        val uri = sourceUri ?: return finish()
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(Uri.parse(uri)).use { BitmapFactory.decodeStream(it) }
                } catch (_: Exception) {
                    null
                }
            }
            if (bmp == null) {
                finish(); return@launch
            }
            source = bmp
            current = bmp.copy(Bitmap.Config.ARGB_8888, true)
            cropView.setImage(current!!)
        }
    }

    private fun reset() {
        pendingRotation = 0
        flipH = false
        flipV = false
        val src = source ?: return
        if (current != src) current?.recycle()
        current = src.copy(Bitmap.Config.ARGB_8888, true)
        cropView.setImage(current!!)
    }

    private fun rotate(degrees: Float) {
        pendingRotation += degrees.toInt()
        applyTransform()
    }

    private fun applyTransform() {
        val src = source ?: return
        val matrix = Matrix()
        matrix.postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
        matrix.postRotate(pendingRotation.toFloat())
        val transformed = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        current?.takeIf { it != src }?.recycle()
        current = transformed
        cropView.setImage(transformed)
    }

    private fun applyAndFinish() {
        val bmp = current ?: return finish()
        val cropRect = cropView.currentCropRect() ?: return finish()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val left = cropRect.left.coerceIn(0f, (bmp.width - 1).toFloat()).toInt()
                val top = cropRect.top.coerceIn(0f, (bmp.height - 1).toFloat()).toInt()
                val right = cropRect.right.coerceIn((left + 1).toFloat(), bmp.width.toFloat()).toInt()
                val bottom = cropRect.bottom.coerceIn((top + 1).toFloat(), bmp.height.toFloat()).toInt()
                val cropped = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
                val outFile = File(cacheDir, "edit_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (cropped != bmp) cropped.recycle()
                Uri.fromFile(outFile).toString()
            }
            if (itemId != null) {
                app.pinimage.float.ViewRegistry.get(itemId!!)?.replaceImage(result)
            }
            val data = Intent().apply {
                putExtra(EXTRA_RESULT_URI, result)
                putExtra(EXTRA_ITEM_ID, itemId)
            }
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_RESULT_URI = "extra_result_uri"
    }
}
