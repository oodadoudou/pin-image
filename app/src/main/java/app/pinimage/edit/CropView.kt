package app.pinimage.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val Float.dp: Float get() = this * density

    private var bitmap: Bitmap? = null
    private val imageRect = RectF()
    private val cropRect = RectF()
    private var aspect: Float = 0f

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint().apply { color = 0x99000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF67D5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f.dp
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF67D5FF.toInt()
        style = Paint.Style.FILL
    }
    private val handleOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp
    }
    private val contentPadding = 28f.dp
    private val handleRadius = 8f.dp

    private enum class Mode { None, Move, Left, Top, Right, Bottom, TopLeft, TopRight, BottomLeft, BottomRight }
    private var mode = Mode.None
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = 24f.dp

    fun setImage(bmp: Bitmap, resetCrop: Boolean = true) {
        val oldImage = RectF(imageRect)
        val oldCrop = RectF(cropRect)
        bitmap = bmp
        layoutImage()
        if (resetCrop || oldImage.isEmpty) {
            resetCropToImage()
        } else {
            val left = (oldCrop.left - oldImage.left) / oldImage.width()
            val top = (oldCrop.top - oldImage.top) / oldImage.height()
            val right = (oldCrop.right - oldImage.left) / oldImage.width()
            val bottom = (oldCrop.bottom - oldImage.top) / oldImage.height()
            cropRect.set(
                imageRect.left + left * imageRect.width(), imageRect.top + top * imageRect.height(),
                imageRect.left + right * imageRect.width(), imageRect.top + bottom * imageRect.height(),
            )
        }
        invalidate()
    }

    fun setAspect(value: Float) {
        aspect = value
        if (bitmap != null) resetCropToImage()
        invalidate()
    }

    fun currentCropRect(): RectF? {
        val bmp = bitmap ?: return null
        if (imageRect.width() == 0f) return null
        val sx = bmp.width.toFloat() / imageRect.width()
        val sy = bmp.height.toFloat() / imageRect.height()
        return RectF(
            (cropRect.left - imageRect.left) * sx,
            (cropRect.top - imageRect.top) * sy,
            (cropRect.right - imageRect.left) * sx,
            (cropRect.bottom - imageRect.top) * sy,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bitmap != null) layoutImage()
    }

    private fun layoutImage() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val availableW = (width - contentPadding * 2f).coerceAtLeast(1f)
        val availableH = (height - contentPadding * 2f).coerceAtLeast(1f)
        val bmpRatio = bmp.width.toFloat() / bmp.height
        val viewRatio = availableW / availableH
        val (w, h) = if (bmpRatio > viewRatio) {
            availableW to availableW / bmpRatio
        } else {
            availableH * bmpRatio to availableH
        }
        imageRect.set(
            (width - w) / 2f,
            (height - h) / 2f,
            (width + w) / 2f,
            (height + h) / 2f,
        )
        if (cropRect.isEmpty) resetCropToImage()
    }

    private fun resetCropToImage() {
        val bmp = bitmap ?: return
        if (imageRect.isEmpty) {
            cropRect.set(0f, 0f, 0f, 0f)
            return
        }
        if (aspect <= 0f) {
            cropRect.set(imageRect)
            return
        }
        val w: Float
        val h: Float
        val imageRatio = imageRect.width() / imageRect.height()
        if (imageRatio > aspect) {
            h = imageRect.height()
            w = h * aspect
        } else {
            w = imageRect.width()
            h = w / aspect
        }
        cropRect.set(
            imageRect.centerX() - w / 2f,
            imageRect.centerY() - h / 2f,
            imageRect.centerX() + w / 2f,
            imageRect.centerY() + h / 2f,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        if (imageRect.isEmpty) layoutImage()
        canvas.drawBitmap(bmp, null, imageRect, imagePaint)
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), cropRect.top)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.restore()
        canvas.save()
        canvas.clipRect(0f, cropRect.bottom, width.toFloat(), height.toFloat())
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.restore()
        canvas.save()
        canvas.clipRect(0f, cropRect.top, cropRect.left, cropRect.bottom)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.restore()
        canvas.save()
        canvas.clipRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.restore()
        canvas.drawRect(cropRect, borderPaint)
        drawHandle(canvas, cropRect.left, cropRect.top)
        drawHandle(canvas, cropRect.centerX(), cropRect.top)
        drawHandle(canvas, cropRect.right, cropRect.top)
        drawHandle(canvas, cropRect.left, cropRect.centerY())
        drawHandle(canvas, cropRect.right, cropRect.centerY())
        drawHandle(canvas, cropRect.left, cropRect.bottom)
        drawHandle(canvas, cropRect.centerX(), cropRect.bottom)
        drawHandle(canvas, cropRect.right, cropRect.bottom)
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
        canvas.drawCircle(x, y, handleRadius, handleOutlinePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                mode = hitTest(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                when (mode) {
                    Mode.Move -> moveCrop(dx, dy)
                    Mode.Left -> resizeCrop(left = cropRect.left + dx)
                    Mode.Right -> resizeCrop(right = cropRect.right + dx)
                    Mode.Top -> resizeCrop(top = cropRect.top + dy)
                    Mode.Bottom -> resizeCrop(bottom = cropRect.bottom + dy)
                    Mode.TopLeft -> resizeCrop(left = cropRect.left + dx, top = cropRect.top + dy)
                    Mode.TopRight -> resizeCrop(right = cropRect.right + dx, top = cropRect.top + dy)
                    Mode.BottomLeft -> resizeCrop(left = cropRect.left + dx, bottom = cropRect.bottom + dy)
                    Mode.BottomRight -> resizeCrop(right = cropRect.right + dx, bottom = cropRect.bottom + dy)
                    Mode.None -> Unit
                }
                downX = event.x; downY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                mode = Mode.None
            }
            MotionEvent.ACTION_CANCEL -> mode = Mode.None
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Mode {
        val nearLeft = abs(x - cropRect.left) <= touchSlop
        val nearRight = abs(x - cropRect.right) <= touchSlop
        val nearTop = abs(y - cropRect.top) <= touchSlop
        val nearBottom = abs(y - cropRect.bottom) <= touchSlop
        if (nearLeft && nearTop) return Mode.TopLeft
        if (nearRight && nearTop) return Mode.TopRight
        if (nearLeft && nearBottom) return Mode.BottomLeft
        if (nearRight && nearBottom) return Mode.BottomRight
        if (nearLeft && y in cropRect.top..cropRect.bottom) return Mode.Left
        if (nearRight && y in cropRect.top..cropRect.bottom) return Mode.Right
        if (nearTop && x in cropRect.left..cropRect.right) return Mode.Top
        if (nearBottom && x in cropRect.left..cropRect.right) return Mode.Bottom
        return if (cropRect.contains(x, y)) Mode.Move else Mode.None
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun moveCrop(dx: Float, dy: Float) {
        val w = cropRect.width()
        val h = cropRect.height()
        var left = (cropRect.left + dx).coerceIn(imageRect.left, imageRect.right - w)
        var top = (cropRect.top + dy).coerceIn(imageRect.top, imageRect.bottom - h)
        cropRect.set(left, top, left + w, top + h)
    }

    private fun resizeCrop(
        left: Float = cropRect.left,
        top: Float = cropRect.top,
        right: Float = cropRect.right,
        bottom: Float = cropRect.bottom,
    ) {
        var l = left.coerceIn(imageRect.left, imageRect.right - 40)
        var t = top.coerceIn(imageRect.top, imageRect.bottom - 40)
        var r = right.coerceIn(imageRect.left + 40, imageRect.right)
        var b = bottom.coerceIn(imageRect.top + 40, imageRect.bottom)
        if (aspect > 0f) {
            if (mode == Mode.Left || mode == Mode.Right) {
                val w = r - l
                var h = w / aspect
                val centerY = (t + b) / 2f
                t = centerY - h / 2f
                b = centerY + h / 2f
                if (t < imageRect.top) { t = imageRect.top; b = t + h }
                if (b > imageRect.bottom) { b = imageRect.bottom; t = b - h }
            } else if (mode == Mode.Top || mode == Mode.Bottom) {
                val h = b - t
                var w = h * aspect
                val centerX = (l + r) / 2f
                l = centerX - w / 2f
                r = centerX + w / 2f
                if (l < imageRect.left) { l = imageRect.left; r = l + w }
                if (r > imageRect.right) { r = imageRect.right; l = r - w }
            } else {
                var h = (r - l) / aspect
                if (mode == Mode.TopLeft || mode == Mode.TopRight) t = b - h else b = t + h
                if (t < imageRect.top || b > imageRect.bottom) {
                    t = t.coerceAtLeast(imageRect.top)
                    b = b.coerceAtMost(imageRect.bottom)
                    val w = (b - t) * aspect
                    if (mode == Mode.TopLeft || mode == Mode.BottomLeft) l = r - w else r = l + w
                }
            }
        }
        if (r - l >= 40 && b - t >= 40) cropRect.set(l, t, r, b)
    }
}
