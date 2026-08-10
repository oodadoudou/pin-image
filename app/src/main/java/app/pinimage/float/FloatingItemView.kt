package app.pinimage.float

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import app.pinimage.util.BitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class FloatingItemView(
    context: Context,
    private val item: FloatingItem,
    private val scope: CoroutineScope,
    val callbacks: Callbacks,
) : FrameLayout(context) {

    interface Callbacks {
        fun onItemUpdated(view: FloatingItemView, newItem: FloatingItem)
        fun onClose(view: FloatingItemView)
        fun onEdit(view: FloatingItemView)
        fun onReplace(view: FloatingItemView)
        fun onDuplicate(view: FloatingItemView)
        fun onSave(view: FloatingItemView)
        fun onBringToFront(view: FloatingItemView)
        fun onSendToBack(view: FloatingItemView)
        fun onRequestEditMode(view: FloatingItemView)
        fun onExitEditMode()
    }

    enum class EditMode { View, FrameEdit }

    var editMode: EditMode = EditMode.View
        private set

    private var onTapOutside: (() -> Unit)? = null

    fun setOnTapOutsideListener(listener: (() -> Unit)?) {
        onTapOutside = listener
    }

    private val contentMatrix = Matrix()

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 14f.dp
    }

    private var bitmap: Bitmap? = null
    private var loadJob: Job? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    val handleRadius = 14f.dp
    val edgePadding = 18f.dp

    private var downRawX = 0f
    private var downRawY = 0f
    private var downFrameX = 0
    private var downFrameY = 0
    private var downFrameW = 0
    private var downFrameH = 0
    private var downContentMatrixValues = FloatArray(9)

    private var mode = TouchMode.Idle
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastSpan = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var resizeEdge = 0

    private var toolbar: FloatingToolbar? = null
    private var toolbarHideJob: Job? = null
    private var didPanSinceDown = false

    private enum class TouchMode { Idle, FrameMove, Resize, ContentPan, ContentZoom }

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        alpha = item.display.opacity
        val init = FloatArray(9).also { contentMatrix.getValues(it) }
        init[Matrix.MSCALE_X] = item.content.zoom
        init[Matrix.MSCALE_Y] = item.content.zoom
        init[Matrix.MTRANS_X] = item.content.offsetX
        init[Matrix.MTRANS_Y] = item.content.offsetY
        contentMatrix.setValues(init)
        if (item.content.rotation != 0f) {
            contentMatrix.postRotate(item.content.rotation, width / 2f, height / 2f)
        }
        loadBitmap()
    }

    val currentItem: FloatingItem
        get() = item.copy(
            content = item.content.copy(
                zoom = readScale(),
                offsetX = readTranslateX(),
                offsetY = readTranslateY(),
            ),
        )

    val originalItem: FloatingItem get() = item

    fun applyExternalUpdate(newItem: FloatingItem) {
        alpha = newItem.display.opacity
        invalidate()
    }

    fun setOpacity(value: Float) {
        alpha = value
        callbacks.onItemUpdated(this, item.copy(display = item.display.copy(opacity = value)))
    }

    fun currentOpacity(): Float = alpha

    fun setEditMode(mode: EditMode) {
        this.editMode = mode
        if (mode == EditMode.View) callbacks.onExitEditMode()
        val lp = layoutParams as? android.view.WindowManager.LayoutParams
        if (lp != null) {
            if (mode == EditMode.FrameEdit) {
                lp.flags = lp.flags or android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            } else {
                lp.flags = lp.flags and android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
            }
            runCatching {
                val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                wm.updateViewLayout(this, lp)
            }
        }
        invalidate()
    }

    fun showToolbar() {
        if (toolbar == null) {
            val tb = FloatingToolbar(context, this)
            toolbar = tb
            addView(tb)
            val lp = tb.layoutParams as LayoutParams
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.topMargin = 8f.dp.toInt()
            tb.layoutParams = lp
        }
        toolbar?.visibility = VISIBLE
        toolbarHideJob?.cancel()
        toolbarHideJob = scope.launch {
            delay(3500)
            toolbar?.visibility = GONE
        }
    }

    fun hideToolbar() {
        toolbar?.visibility = GONE
    }

    fun refreshToolbarLockIcon() {
        toolbar?.bindLockIcon(item.state)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bitmap != null && oldw == 0 && oldh == 0) fitContentToFrame()
    }

    private fun loadBitmap() {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.Main) {
            val bmp = withContext(Dispatchers.IO) { BitmapLoader.load(context, item.imageUri) }
            bitmap = bmp
            if (bmp != null && width > 0 && height > 0) fitContentToFrame()
            invalidate()
        }
    }

    fun replaceImage(uri: String) {
        callbacks.onItemUpdated(this, item.copy(imageUri = uri))
        loadBitmap()
    }

    private fun fitContentToFrame() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val bmpRatio = bmp.width.toFloat() / bmp.height
        val frameRatio = width.toFloat() / height
        val (w, h) = if (bmpRatio > frameRatio) {
            width.toFloat() to width / bmpRatio
        } else {
            height * bmpRatio to height.toFloat()
        }
        val scale = w / bmp.width
        contentMatrix.reset()
        contentMatrix.postScale(scale, scale)
        contentMatrix.postTranslate((width - w) / 2f, (height - h) / 2f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap
        if (bmp != null) {
            val save = canvas.save()
            canvas.concat(contentMatrix)
            val src = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
            canvas.drawBitmap(bmp, null, src, bitmapPaint)
            canvas.restoreToCount(save)
        } else {
            canvas.drawText("Loading...", 12f.dp, height / 2f, textPaint)
        }

        if (editMode == EditMode.FrameEdit) {
            canvas.drawRect(edgePadding, edgePadding, width - edgePadding, height - edgePadding, borderPaint)
            drawHandle(canvas, edgePadding, edgePadding)
            drawHandle(canvas, width - edgePadding, edgePadding)
            drawHandle(canvas, edgePadding, height - edgePadding)
            drawHandle(canvas, width - edgePadding, height - edgePadding)
        }
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, handleRadius, handlePaint)
    }

    private fun readScale(): Float {
        val v = FloatArray(9)
        contentMatrix.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    private fun readTranslateX(): Float {
        val v = FloatArray(9)
        contentMatrix.getValues(v)
        return v[Matrix.MTRANS_X]
    }

    private fun readTranslateY(): Float {
        val v = FloatArray(9)
        contentMatrix.getValues(v)
        return v[Matrix.MTRANS_Y]
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (editMode == EditMode.FrameEdit) {
                setEditMode(EditMode.View)
            }
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUp(event)
        }
        return true
    }

    companion object {
        const val EDGE_LEFT = 1
        const val EDGE_RIGHT = 2
        const val EDGE_TOP = 4
        const val EDGE_BOTTOM = 8
    }

    private fun handleDown(ev: MotionEvent) {
        downRawX = ev.rawX
        downRawY = ev.rawY
        activePointerId = ev.getPointerId(0)
        didPanSinceDown = false
        mode = TouchMode.Idle

        if (item.state == LockState.FullyLocked) return

        if (editMode == EditMode.FrameEdit) {
            resizeEdge = hitTestHandle(ev.x, ev.y)
            captureFrameStart()
            mode = if (resizeEdge != 0) TouchMode.Resize else TouchMode.FrameMove
            return
        }

        beginContentGesture(ev)
        if (item.state == LockState.Unlocked) postLongPressCheck()
    }

    private fun beginContentGesture(ev: MotionEvent) {
        mode = TouchMode.ContentPan
        downContentMatrixValues = FloatArray(9).also { contentMatrix.getValues(it) }
    }

    private fun postLongPressCheck() {
        removeCallbacks(longPressRunnable)
        postDelayed(longPressRunnable, longPressTimeout)
    }

    private val longPressRunnable = Runnable {
        if (mode != TouchMode.ContentPan) return@Runnable
        if (didPanSinceDown) return@Runnable
        if (item.state != LockState.Unlocked) return@Runnable
        mode = TouchMode.Idle
        callbacks.onRequestEditMode(this)
    }

    private fun captureFrameStart() {
        val lp = layoutParams as? android.view.WindowManager.LayoutParams ?: return
        downFrameX = lp.x
        downFrameY = lp.y
        downFrameW = lp.width
        downFrameH = lp.height
    }

    private fun hitTestHandle(x: Float, y: Float): Int {
        val left = edgePadding
        val top = edgePadding
        val right = width - edgePadding
        val bottom = height - edgePadding
        var edge = 0
        val cornerRange = handleRadius * 2.5f
        if (abs(x - left) <= cornerRange) edge = edge or EDGE_LEFT
        if (abs(x - right) <= cornerRange) edge = edge or EDGE_RIGHT
        if (abs(y - top) <= cornerRange) edge = edge or EDGE_TOP
        if (abs(y - bottom) <= cornerRange) edge = edge or EDGE_BOTTOM
        if (edge != 0) return edge
        val edgeRange = 28f.dp
        if (abs(x - left) <= edgeRange) edge = edge or EDGE_LEFT
        if (abs(x - right) <= edgeRange) edge = edge or EDGE_RIGHT
        if (abs(y - top) <= edgeRange) edge = edge or EDGE_TOP
        if (abs(y - bottom) <= edgeRange) edge = edge or EDGE_BOTTOM
        return edge
    }

    private fun handlePointerDown(ev: MotionEvent) {
        removeCallbacks(longPressRunnable)
        if (item.state == LockState.FullyLocked) return
        if (editMode == EditMode.FrameEdit) return
        if (ev.pointerCount == 2) {
            mode = TouchMode.ContentZoom
            lastSpan = span(ev)
            val f = focus(ev)
            lastFocusX = f.first
            lastFocusY = f.second
            downContentMatrixValues = FloatArray(9).also { contentMatrix.getValues(it) }
        }
    }

    private fun handleMove(ev: MotionEvent) {
        when (mode) {
            TouchMode.ContentPan -> {
                val totalDx = ev.rawX - downRawX
                val totalDy = ev.rawY - downRawY
                if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                    didPanSinceDown = true
                    removeCallbacks(longPressRunnable)
                }
                if (item.state == LockState.FullyLocked) return
                val values = downContentMatrixValues.copyOf()
                values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X] + totalDx
                values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y] + totalDy
                contentMatrix.setValues(values)
                invalidate()
            }
            TouchMode.ContentZoom -> {
                val span = span(ev)
                if (lastSpan == 0f) return
                val factor = span / lastSpan
                val focus = focus(ev)
                val values = downContentMatrixValues.copyOf()
                val baseScale = values[Matrix.MSCALE_X]
                val newScale = (baseScale * factor).coerceIn(0.05f, 50f)
                values[Matrix.MSCALE_X] = newScale
                values[Matrix.MSCALE_Y] = newScale
                values[Matrix.MTRANS_X] = focus.first - (focus.first - values[Matrix.MTRANS_X]) * factor
                values[Matrix.MTRANS_Y] = focus.second - (focus.second - values[Matrix.MTRANS_Y]) * factor
                values[Matrix.MTRANS_X] += (focus.first - lastFocusX) * 0f
                values[Matrix.MTRANS_Y] += (focus.second - lastFocusY) * 0f
                contentMatrix.setValues(values)
                invalidate()
            }
            TouchMode.FrameMove -> {
                val totalDx = (ev.rawX - downRawX).toInt()
                val totalDy = (ev.rawY - downRawY).toInt()
                callbacks.let { _ ->
                    (layoutParams as? android.view.WindowManager.LayoutParams)?.let { lp ->
                        lp.x = downFrameX + totalDx
                        lp.y = downFrameY + totalDy
                        callbacks.onItemUpdated(this, currentItem.copy(frame = item.frame.copy(x = lp.x, y = lp.y)))
                        requestLayout()
                    }
                }
            }
            TouchMode.Resize -> {
                val totalDx = (ev.rawX - downRawX)
                val totalDy = (ev.rawY - downRawY)
                (layoutParams as? android.view.WindowManager.LayoutParams)?.let { lp ->
                    val origX = lp.x
                    val origY = lp.y
                    val origW = lp.width
                    val origH = lp.height
                    var newW = origW
                    var newH = origH
                    var newX = origX
                    var newY = origY
                    if (resizeEdge and EDGE_RIGHT != 0) newW = (downFrameW + totalDx).toInt().coerceAtLeast(80)
                    if (resizeEdge and EDGE_BOTTOM != 0) newH = (downFrameH + totalDy).toInt().coerceAtLeast(80)
                    if (resizeEdge and EDGE_LEFT != 0) {
                        newW = (downFrameW - totalDx).toInt().coerceAtLeast(80)
                        newX = downFrameX + (downFrameW - newW)
                    }
                    if (resizeEdge and EDGE_TOP != 0) {
                        newH = (downFrameH - totalDy).toInt().coerceAtLeast(80)
                        newY = downFrameY + (downFrameH - newH)
                    }
                    lp.x = newX; lp.y = newY; lp.width = newW; lp.height = newH
                    callbacks.onItemUpdated(
                        this,
                        currentItem.copy(frame = item.frame.copy(x = newX, y = newY, width = newW, height = newH)),
                    )
                    requestLayout()
                }
            }
            TouchMode.Idle -> Unit
        }
    }

    private fun handlePointerUp(ev: MotionEvent) {
        if (ev.pointerCount == 2) {
            mode = TouchMode.Idle
        }
    }

    private fun handleUp(ev: MotionEvent) {
        removeCallbacks(longPressRunnable)
        val wasIdleTap = mode == TouchMode.ContentPan && !didPanSinceDown && editMode == EditMode.View
        if (wasIdleTap && item.state != LockState.FullyLocked) {
            showToolbar()
        }
        if (mode == TouchMode.ContentPan || mode == TouchMode.ContentZoom) {
            callbacks.onItemUpdated(this, currentItem)
        }
        if (mode == TouchMode.FrameMove || mode == TouchMode.Resize) {
            callbacks.onItemUpdated(this, currentItem)
        }
        mode = TouchMode.Idle
        activePointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun span(ev: MotionEvent): Float {
        if (ev.pointerCount < 2) return 0f
        val dx = ev.getX(0) - ev.getX(1)
        val dy = ev.getY(0) - ev.getY(1)
        return kotlin.math.hypot(dx, dy)
    }

    private fun focus(ev: MotionEvent): Pair<Float, Float> {
        if (ev.pointerCount < 2) return ev.x to ev.y
        return ((ev.getX(0) + ev.getX(1)) / 2f) to ((ev.getY(0) + ev.getY(1)) / 2f)
    }
}
