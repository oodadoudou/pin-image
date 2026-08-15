package app.pinimage.board

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import app.pinimage.util.BitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

class BoardCanvas(context: Context) : View(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var board: Board = Board(id = "empty", name = "")
    private var bitmaps: Map<String, android.graphics.Bitmap> = emptyMap()
    private var loadJob: Job? = null

    var onSelect: ((BoardObject?) -> Unit)? = null
    var onBoardChanged: ((Board) -> Unit)? = null
    var onTransformStarted: (() -> Unit)? = null

    private var freeTransformEnabled = false

    private val viewMatrix = Matrix()
    private val inverse = Matrix()
    private val tmpRect = RectF()
    private val tmpPt = FloatArray(2)

    private val checkeredPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1976D2.toInt()
        style = Paint.Style.FILL
    }
    private val transformLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1976D2.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val handleRadius = 14f
    private val rotateHandleOffset = 46f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var selectedId: String? = null
    private var mode = TouchMode.Idle
    private var downX = 0f
    private var downY = 0f
    private var downViewMatrix = FloatArray(9)
    private var panAccumX = 0f
    private var panAccumY = 0f
    private var lastSpan = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var editDownX = 0f
    private var editDownY = 0f
    private var editObjStart: BoardObject? = null
    private var editHandle = 0
    private var editStartAngle = 0f

    private enum class TouchMode { Idle, Pan, Zoom, ObjectMove, ObjectResize, ObjectRotate, AwaitLongPress }

    init {
        setBackgroundColor(0xFFE5E5EA.toInt())
        val initValues = FloatArray(9)
        viewMatrix.getValues(initValues)
    }

    fun setBoard(newBoard: Board) {
        board = newBoard
        loadBitmaps()
        invalidate()
    }

    fun setFreeTransformEnabled(enabled: Boolean) {
        freeTransformEnabled = enabled
        invalidate()
    }

    private fun loadBitmaps() {
        loadJob?.cancel()
        val uris = board.objects.map { it.imageUri }.distinct()
        loadJob = scope.launch {
            val map = mutableMapOf<String, android.graphics.Bitmap>()
            uris.forEach { uri ->
                val bmp = withContext(Dispatchers.IO) { BitmapLoader.load(context, uri) }
                if (bmp != null) map[uri] = bmp
            }
            bitmaps = map
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeInitialFit()
    }

    private fun computeInitialFit() {
        if (width == 0 || height == 0 || board.canvasWidth == 0) return
        val bounds = board.contentBounds()
        val scale = minOf(width.toFloat() / bounds.width, height.toFloat() / bounds.height) * 0.9f
        val dx = (width - bounds.width * scale) / 2f - bounds.left * scale
        val dy = (height - bounds.height * scale) / 2f - bounds.top * scale
        viewMatrix.reset()
        viewMatrix.postScale(scale, scale)
        viewMatrix.postTranslate(dx, dy)
        viewMatrix.invert(inverse)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(viewMatrix)

        val bounds = board.contentBounds()
        when (board.background) {
            BoardBackground.White -> {
                backgroundPaint.color = Color.WHITE
                canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, backgroundPaint)
            }
            BoardBackground.Black -> {
                backgroundPaint.color = Color.BLACK
                canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, backgroundPaint)
            }
            BoardBackground.Transparent -> drawTransparencyPattern(canvas, bounds)
        }

        board.objects.sortedBy { it.zIndex }.forEach { obj ->
            val bmp = bitmaps[obj.imageUri] ?: return@forEach
            val save = canvas.save()
            canvas.translate(obj.x + obj.width / 2f, obj.y + obj.height / 2f)
            canvas.rotate(obj.rotation)
            canvas.scale(if (obj.flipH) -1f else 1f, if (obj.flipV) -1f else 1f)
            tmpRect.set(-obj.width / 2f, -obj.height / 2f, obj.width / 2f, obj.height / 2f)
            canvas.drawBitmap(bmp, null, tmpRect, imagePaint)
            canvas.restoreToCount(save)
        }

        selectedId?.let { id ->
            val obj = board.objects.firstOrNull { it.id == id } ?: return@let
            val save = canvas.save()
            canvas.translate(obj.x + obj.width / 2f, obj.y + obj.height / 2f)
            canvas.rotate(obj.rotation)
            val w = obj.width
            val h = obj.height
            canvas.drawRect(-w / 2f, -h / 2f, w / 2f, h / 2f, selectionPaint)
            canvas.drawCircle(-w / 2f, -h / 2f, handleRadius, handlePaint)
            canvas.drawCircle(w / 2f, -h / 2f, handleRadius, handlePaint)
            canvas.drawCircle(-w / 2f, h / 2f, handleRadius, handlePaint)
            canvas.drawCircle(w / 2f, h / 2f, handleRadius, handlePaint)
            canvas.drawLine(0f, -h / 2f, 0f, -h / 2f - rotateHandleOffset, transformLinePaint)
            canvas.drawCircle(0f, -h / 2f - rotateHandleOffset, handleRadius * 1.15f, handlePaint)
            canvas.restoreToCount(save)
        }

        canvas.restore()
    }

    private fun drawTransparencyPattern(canvas: Canvas, bounds: BoardContentBounds) {
        val cell = 20f
        val startX = floor(bounds.left / cell).toInt()
        val startY = floor(bounds.top / cell).toInt()
        val endX = floor(bounds.right / cell).toInt()
        val endY = floor(bounds.bottom / cell).toInt()
        val light = Color.LTGRAY
        val white = Color.WHITE
        for (y in startY..endY) {
            for (x in startX..endX) {
                checkeredPaint.color = if ((x + y) % 2 == 0) white else light
                canvas.drawRect(
                    maxOf(bounds.left, x * cell),
                    maxOf(bounds.top, y * cell),
                    minOf(bounds.right, (x + 1) * cell),
                    minOf(bounds.bottom, (y + 1) * cell),
                    checkeredPaint,
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUp(event)
        }
        return true
    }

    private fun toCanvasCoords(x: Float, y: Float): Pair<Float, Float> {
        tmpPt[0] = x; tmpPt[1] = y
        inverse.mapPoints(tmpPt)
        return tmpPt[0] to tmpPt[1]
    }

    private fun hitTestObject(cx: Float, cy: Float): BoardObject? {
        return board.objects.sortedByDescending { it.zIndex }.firstOrNull { obj ->
            val localX = cx - (obj.x + obj.width / 2f)
            val localY = cy - (obj.y + obj.height / 2f)
            val rad = Math.toRadians(-obj.rotation.toDouble())
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()
            val rx = localX * cos - localY * sin
            val ry = localX * sin + localY * cos
            abs(rx) <= obj.width / 2f + 8 && abs(ry) <= obj.height / 2f + 8
        }
    }

    private fun hitTestHandle(obj: BoardObject, cx: Float, cy: Float): Int {
        val localX = cx - (obj.x + obj.width / 2f)
        val localY = cy - (obj.y + obj.height / 2f)
        val rad = Math.toRadians(-obj.rotation.toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        val rx = localX * cos - localY * sin
        val ry = localX * sin + localY * cos
        val range = handleRadius * 2.5f
        var mask = 0
        if (abs(rx + obj.width / 2f) <= range) mask = mask or 1
        if (abs(rx - obj.width / 2f) <= range) mask = mask or 2
        if (abs(ry + obj.height / 2f) <= range) mask = mask or 4
        if (abs(ry - obj.height / 2f) <= range) mask = mask or 8
        return mask
    }

    private fun hitTestRotateHandle(obj: BoardObject, cx: Float, cy: Float): Boolean {
        val localX = cx - (obj.x + obj.width / 2f)
        val localY = cy - (obj.y + obj.height / 2f)
        val rad = Math.toRadians(-obj.rotation.toDouble())
        val rx = localX * Math.cos(rad).toFloat() - localY * Math.sin(rad).toFloat()
        val ry = localX * Math.sin(rad).toFloat() + localY * Math.cos(rad).toFloat()
        return hypot(rx, ry + obj.height / 2f + rotateHandleOffset) <= handleRadius * 2.5f
    }

    private fun handleDown(ev: MotionEvent) {
        downX = ev.x; downY = ev.y
        panAccumX = 0f; panAccumY = 0f
        downViewMatrix = FloatArray(9).also { viewMatrix.getValues(it) }
        val (cx, cy) = toCanvasCoords(ev.x, ev.y)
        if (selectedId != null) {
            val obj = board.objects.firstOrNull { it.id == selectedId }
            if (obj != null) {
                if (hitTestRotateHandle(obj, cx, cy)) {
                    onTransformStarted?.invoke()
                    mode = TouchMode.ObjectRotate
                    editObjStart = obj
                    editStartAngle = angleFromCenter(obj, cx, cy)
                    return
                }
                val handle = hitTestHandle(obj, cx, cy)
                if (handle != 0) {
                    onTransformStarted?.invoke()
                    mode = TouchMode.ObjectResize
                    editHandle = handle
                    editDownX = cx; editDownY = cy
                    editObjStart = obj
                    return
                }
            }
        }
        val hit = hitTestObject(cx, cy)
        if (hit != null) {
            if (hit.id != selectedId) {
                selectedId = hit.id
                onSelect?.invoke(hit)
                invalidate()
            }
            mode = TouchMode.AwaitLongPress
            postDelayed(longPressRunnable, longPressTimeout)
            return
        }
        selectedId = null
        onSelect?.invoke(null)
        mode = TouchMode.Pan
        invalidate()
    }

    private val longPressRunnable = Runnable {
        if (mode != TouchMode.AwaitLongPress) return@Runnable
        onTransformStarted?.invoke()
        mode = TouchMode.ObjectMove
        val (cx, cy) = toCanvasCoords(downX, downY)
        editDownX = cx; editDownY = cy
        editObjStart = board.objects.firstOrNull { it.id == selectedId }
    }

    private fun handlePointerDown(ev: MotionEvent) {
        removeCallbacks(longPressRunnable)
        if (ev.pointerCount == 2) {
            mode = TouchMode.Zoom
            lastSpan = span(ev)
            val f = focus(ev)
            lastFocusX = f.first
            lastFocusY = f.second
            downViewMatrix = FloatArray(9).also { viewMatrix.getValues(it) }
        }
    }

    private fun handleMove(ev: MotionEvent) {
        when (mode) {
            TouchMode.Pan -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                val values = downViewMatrix.copyOf()
                values[Matrix.MTRANS_X] += dx
                values[Matrix.MTRANS_Y] += dy
                viewMatrix.setValues(values)
                viewMatrix.invert(inverse)
                invalidate()
            }
            TouchMode.Zoom -> {
                val span = span(ev)
                if (lastSpan == 0f) return
                val factor = span / lastSpan
                val f = focus(ev)
                val values = downViewMatrix.copyOf()
                val sx = values[Matrix.MSCALE_X] * factor
                val sy = values[Matrix.MSCALE_Y] * factor
                values[Matrix.MSCALE_X] = sx
                values[Matrix.MSCALE_Y] = sy
                values[Matrix.MTRANS_X] = f.first - (f.first - values[Matrix.MTRANS_X]) * factor
                values[Matrix.MTRANS_Y] = f.second - (f.second - values[Matrix.MTRANS_Y]) * factor
                values[Matrix.MTRANS_X] += f.first - lastFocusX
                values[Matrix.MTRANS_Y] += f.second - lastFocusY
                viewMatrix.setValues(values)
                viewMatrix.invert(inverse)
                invalidate()
            }
            TouchMode.AwaitLongPress -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    removeCallbacks(longPressRunnable)
                    mode = TouchMode.Pan
                }
            }
            TouchMode.ObjectMove -> {
                val (cx, cy) = toCanvasCoords(ev.x, ev.y)
                val start = editObjStart ?: return
                val dx = cx - editDownX
                val dy = cy - editDownY
                val updated = start.copy(x = start.x + dx, y = start.y + dy)
                updateObject(updated)
            }
            TouchMode.ObjectResize -> {
                val (cx, cy) = toCanvasCoords(ev.x, ev.y)
                val start = editObjStart ?: return
                updateObject(resizedObject(start, cx - editDownX, cy - editDownY))
            }
            TouchMode.ObjectRotate -> {
                val (cx, cy) = toCanvasCoords(ev.x, ev.y)
                val start = editObjStart ?: return
                val delta = angleFromCenter(start, cx, cy) - editStartAngle
                updateObject(start.copy(rotation = start.rotation + delta))
            }
            TouchMode.Idle -> Unit
        }
    }

    private fun handlePointerUp(ev: MotionEvent) {
        if (ev.pointerCount == 2) mode = TouchMode.Idle
    }

    private fun handleUp(ev: MotionEvent) {
        removeCallbacks(longPressRunnable)
        if (mode == TouchMode.AwaitLongPress) performClick()
        mode = TouchMode.Idle
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateObject(obj: BoardObject) {
        board = board.copy(objects = board.objects.map { if (it.id == obj.id) obj else it })
        onBoardChanged?.invoke(board)
        invalidate()
    }

    private fun resizedObject(start: BoardObject, canvasDx: Float, canvasDy: Float): BoardObject {
        val radians = Math.toRadians(-start.rotation.toDouble())
        val cos = Math.cos(radians).toFloat()
        val sin = Math.sin(radians).toFloat()
        val dx = canvasDx * cos - canvasDy * sin
        val dy = canvasDx * sin + canvasDy * cos
        val signX = when {
            editHandle and 2 != 0 -> 1f
            editHandle and 1 != 0 -> -1f
            else -> 0f
        }
        val signY = when {
            editHandle and 8 != 0 -> 1f
            editHandle and 4 != 0 -> -1f
            else -> 0f
        }
        val targetWidth = if (signX == 0f) start.width else start.width + dx * signX
        val targetHeight = if (signY == 0f) start.height else start.height + dy * signY
        val (newWidth, newHeight) = if (freeTransformEnabled) {
            targetWidth.coerceAtLeast(20f) to targetHeight.coerceAtLeast(20f)
        } else {
            val widthScale = targetWidth / start.width
            val heightScale = targetHeight / start.height
            val requestedScale = when {
                signX == 0f -> heightScale
                signY == 0f -> widthScale
                abs(widthScale - 1f) >= abs(heightScale - 1f) -> widthScale
                else -> heightScale
            }
            val minScale = maxOf(20f / start.width, 20f / start.height)
            val scale = requestedScale.coerceAtLeast(minScale)
            start.width * scale to start.height * scale
        }

        val oldCenterX = start.x + start.width / 2f
        val oldCenterY = start.y + start.height / 2f
        val fixedOldX = -signX * start.width / 2f
        val fixedOldY = -signY * start.height / 2f
        val fixedNewX = -signX * newWidth / 2f
        val fixedNewY = -signY * newHeight / 2f
        val rotation = Math.toRadians(start.rotation.toDouble())
        val rCos = Math.cos(rotation).toFloat()
        val rSin = Math.sin(rotation).toFloat()
        val fixedGlobalX = oldCenterX + fixedOldX * rCos - fixedOldY * rSin
        val fixedGlobalY = oldCenterY + fixedOldX * rSin + fixedOldY * rCos
        val newCenterX = fixedGlobalX - (fixedNewX * rCos - fixedNewY * rSin)
        val newCenterY = fixedGlobalY - (fixedNewX * rSin + fixedNewY * rCos)
        return start.copy(
            x = newCenterX - newWidth / 2f,
            y = newCenterY - newHeight / 2f,
            width = newWidth,
            height = newHeight,
        )
    }

    private fun angleFromCenter(obj: BoardObject, x: Float, y: Float): Float =
        Math.toDegrees(kotlin.math.atan2(y - (obj.y + obj.height / 2f), x - (obj.x + obj.width / 2f)).toDouble()).toFloat()

    private fun span(ev: MotionEvent): Float {
        if (ev.pointerCount < 2) return 0f
        val dx = ev.getX(0) - ev.getX(1)
        val dy = ev.getY(0) - ev.getY(1)
        return hypot(dx, dy)
    }

    private fun focus(ev: MotionEvent): Pair<Float, Float> {
        if (ev.pointerCount < 2) return ev.x to ev.y
        return ((ev.getX(0) + ev.getX(1)) / 2f) to ((ev.getY(0) + ev.getY(1)) / 2f)
    }

    override fun onDetachedFromWindow() {
        loadJob?.cancel()
        scope.cancel()
        super.onDetachedFromWindow()
    }
}
