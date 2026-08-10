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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

class BoardCanvas(context: Context) : View(context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var board: Board = Board(id = "empty", name = "")
    private var bitmaps: Map<String, android.graphics.Bitmap> = emptyMap()
    private var loadJob: Job? = null

    var onSelect: ((BoardObject?) -> Unit)? = null
    var onBoardChanged: ((Board) -> Unit)? = null

    private val viewMatrix = Matrix()
    private val inverse = Matrix()
    private val tmpRect = RectF()
    private val tmpPt = FloatArray(2)

    private val checkeredPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val handleRadius = 14f

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

    private enum class TouchMode { Idle, Pan, Zoom, ObjectMove, ObjectResize, AwaitLongPress }

    init {
        setBackgroundColor(Color.WHITE)
        val initValues = FloatArray(9)
        viewMatrix.getValues(initValues)
    }

    fun setBoard(newBoard: Board) {
        board = newBoard
        loadBitmaps()
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
        val scale = minOf(width.toFloat() / board.canvasWidth, height.toFloat() / board.canvasHeight) * 0.9f
        val dx = (width - board.canvasWidth * scale) / 2f
        val dy = (height - board.canvasHeight * scale) / 2f
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

        when (board.background) {
            BoardBackground.White -> canvas.drawColor(Color.WHITE)
            BoardBackground.Black -> canvas.drawColor(Color.BLACK)
            BoardBackground.Transparent -> drawTransparencyPattern(canvas)
        }

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        board.objects.sortedBy { it.zIndex }.forEach { obj ->
            val bmp = bitmaps[obj.imageUri] ?: return@forEach
            val save = canvas.save()
            canvas.translate(obj.x + obj.width / 2f, obj.y + obj.height / 2f)
            canvas.rotate(obj.rotation)
            canvas.scale(if (obj.flipH) -1f else 1f, if (obj.flipV) -1f else 1f)
            tmpRect.set(-obj.width / 2f, -obj.height / 2f, obj.width / 2f, obj.height / 2f)
            canvas.drawBitmap(bmp, null, tmpRect, paint)
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
            canvas.restoreToCount(save)
        }

        canvas.restore()
    }

    private fun drawTransparencyPattern(canvas: Canvas) {
        val cell = 20f
        val cols = (board.canvasWidth / cell).toInt() + 1
        val rows = (board.canvasHeight / cell).toInt() + 1
        val light = Color.LTGRAY
        val white = Color.WHITE
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                checkeredPaint.color = if ((x + y) % 2 == 0) white else light
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, checkeredPaint)
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

    private fun handleDown(ev: MotionEvent) {
        downX = ev.x; downY = ev.y
        panAccumX = 0f; panAccumY = 0f
        downViewMatrix = FloatArray(9).also { viewMatrix.getValues(it) }
        val (cx, cy) = toCanvasCoords(ev.x, ev.y)
        if (selectedId != null) {
            val obj = board.objects.firstOrNull { it.id == selectedId }
            if (obj != null) {
                val handle = hitTestHandle(obj, cx, cy)
                if (handle != 0) {
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
                var w = start.width
                var h = start.height
                var x = start.x
                var y = start.y
                val dx = cx - editDownX
                val dy = cy - editDownY
                if (editHandle and 2 != 0) w = (start.width + dx).coerceAtLeast(20f)
                if (editHandle and 8 != 0) h = (start.height + dy).coerceAtLeast(20f)
                if (editHandle and 1 != 0) {
                    w = (start.width - dx).coerceAtLeast(20f)
                    x = start.x + (start.width - w)
                }
                if (editHandle and 4 != 0) {
                    h = (start.height - dy).coerceAtLeast(20f)
                    y = start.y + (start.height - h)
                }
                updateObject(start.copy(x = x, y = y, width = w, height = h))
            }
            TouchMode.Idle -> Unit
        }
    }

    private fun handlePointerUp(ev: MotionEvent) {
        if (ev.pointerCount == 2) mode = TouchMode.Idle
    }

    private fun handleUp(ev: MotionEvent) {
        removeCallbacks(longPressRunnable)
        mode = TouchMode.Idle
    }

    private fun updateObject(obj: BoardObject) {
        board = board.copy(objects = board.objects.map { if (it.id == obj.id) obj else it })
        onBoardChanged?.invoke(board)
        invalidate()
    }

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
}
