package app.pinimage.float

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import app.pinimage.util.BitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil

@SuppressLint("ViewConstructor")
class FloatingItemView(
    context: Context,
    initialItem: FloatingItem,
    private val scope: CoroutineScope,
    val callbacks: Callbacks,
    private val restoreContentTransform: Boolean = false,
) : FrameLayout(context) {

    private var item: FloatingItem = initialItem

    interface Callbacks {
        fun onItemUpdated(view: FloatingItemView, newItem: FloatingItem)
        fun onClose(view: FloatingItemView)
        fun onEdit(view: FloatingItemView)
        fun onReplace(view: FloatingItemView)
        fun onDuplicate(view: FloatingItemView)
        fun onHide(view: FloatingItemView)
        fun onSave(view: FloatingItemView)
        fun onBringToFront(view: FloatingItemView)
        fun onSendToBack(view: FloatingItemView)
        fun onRequestEditMode(view: FloatingItemView)
        fun onToolbarShown(view: FloatingItemView)
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
    private var contentRotation = item.content.rotation

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val pdfPagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pdfShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24000000 }
    private val pdfCounterBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
    private val pdfCounterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f.dp
        textAlign = Paint.Align.CENTER
    }
    private val bitmapRect = RectF()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp
    }
    private val pinBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 14f.dp
    }

    private var bitmap: Bitmap? = null
    private var epubBook: EpubBook? = null
    private var epubWebView: WebView? = null
    private var epubSaveJob: Job? = null
    private var pdfSource: PdfPageSource? = null
    private val pdfPageCache = object : LruCache<String, Bitmap>(PDF_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pendingPdfPages = mutableSetOf<String>()
    private val pdfRenderJobs = mutableMapOf<String, Job>()
    private val pdfPageRects = mutableListOf<RectF>()
    private var pdfDocumentWidth = 1f
    private var pdfCurrentPage = 1
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
        loadContent(item.imageUri, fitAfterLoad = !restoreContentTransform)
    }

    val currentItem: FloatingItem
        get() = item.copy(
            content = item.content.copy(
                zoom = if (item.mediaKind == MediaKind.Epub) (epubWebView?.settings?.textZoom ?: 100) / 100f else readScale(),
                offsetX = if (item.mediaKind == MediaKind.Epub) (epubWebView?.scrollX ?: 0).toFloat() else readTranslateX(),
                offsetY = if (item.mediaKind == MediaKind.Epub) (epubWebView?.scrollY ?: 0).toFloat() else readTranslateY(),
                rotation = contentRotation,
            ),
        )

    val originalItem: FloatingItem get() = item
    val isPdf: Boolean get() = item.mediaKind == MediaKind.Pdf
    val isEpub: Boolean get() = item.mediaKind == MediaKind.Epub

    fun currentPdfPage(): Int = pdfCurrentPage

    fun pdfPageCount(): Int = pdfSource?.pages?.size ?: 0

    fun jumpToPdfPage(page: Int) {
        if (pdfPageRects.isEmpty()) return
        val index = (page - 1).coerceIn(0, pdfPageRects.lastIndex)
        val target = pdfPageRects[index]
        val values = FloatArray(9).also { contentMatrix.getValues(it) }
        values[Matrix.MTRANS_Y] = 8f.dp - target.top * values[Matrix.MSCALE_Y]
        contentMatrix.setValues(values)
        pdfCurrentPage = index + 1
        callbacks.onItemUpdated(this, currentItem)
        invalidate()
    }

    fun epubProgressPercent(): Int {
        val web = epubWebView ?: return 0
        val range = (web.contentHeight * web.scale - web.height).coerceAtLeast(1f)
        return (web.scrollY / range * 100f).toInt().coerceIn(0, 100)
    }

    fun jumpToEpubProgress(percent: Int) {
        val web = epubWebView ?: return
        val range = (web.contentHeight * web.scale - web.height).coerceAtLeast(0f)
        web.scrollTo(0, (range * percent.coerceIn(0, 100) / 100f).toInt())
        persistEpubPosition()
    }

    fun epubTextZoom(): Int = epubWebView?.settings?.textZoom ?: 100

    fun setEpubTextZoom(percent: Int) {
        epubWebView?.settings?.textZoom = percent.coerceIn(70, 200)
        persistEpubPosition()
    }

    fun applyExternalUpdate(newItem: FloatingItem) {
        item = newItem
        contentRotation = newItem.content.rotation
        alpha = newItem.display.opacity
        refreshToolbarLockIcon()
        invalidate()
    }

    fun setOpacity(value: Float) {
        alpha = value
        callbacks.onItemUpdated(this, item.copy(display = item.display.copy(opacity = value)))
    }

    fun currentOpacity(): Float = alpha

    fun setBorderStyle(style: PinBorderStyle) {
        val updated = currentItem.copy(display = currentItem.display.copy(borderStyle = style))
        applyExternalUpdate(updated)
        callbacks.onItemUpdated(this, updated)
    }

    fun rotateContentClockwise() {
        if (isEpub) return
        contentRotation = (contentRotation + 90f) % 360f
        val updated = currentItem.copy(content = currentItem.content.copy(rotation = contentRotation))
        applyExternalUpdate(updated)
        callbacks.onItemUpdated(this, updated)
        invalidate()
    }

    /** Restores a contain-fit without changing the floating window itself. */
    fun fitContentToFrameAndPersist() {
        fitContentToFrame()
        callbacks.onItemUpdated(this, currentItem)
    }

    fun setEditMode(mode: EditMode) {
        this.editMode = mode
        epubWebView?.isEnabled = mode == EditMode.View
        if (mode == EditMode.View) {
            hideToolbar()
            callbacks.onExitEditMode()
        }
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

    fun showToolbar(autoHide: Boolean = true) {
        if (toolbar == null) {
            val tb = FloatingToolbar(context, this)
            toolbar = tb
            addView(tb, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 6f.dp.toInt()
            })
        }
        toolbar?.visibility = VISIBLE
        callbacks.onToolbarShown(this)
        updateOutsideTouchFlag(true)
        toolbarHideJob?.cancel()
        if (autoHide) {
            toolbarHideJob = scope.launch {
                delay(TOOLBAR_AUTO_HIDE_MS)
                toolbar?.collapse()
                toolbar?.visibility = GONE
                if (editMode == EditMode.View) updateOutsideTouchFlag(false)
            }
        }
    }

    fun hideToolbar() {
        toolbarHideJob?.cancel()
        toolbar?.collapse()
        toolbar?.visibility = GONE
        if (editMode == EditMode.View) updateOutsideTouchFlag(false)
    }

    private fun updateOutsideTouchFlag(enabled: Boolean) {
        val lp = layoutParams as? android.view.WindowManager.LayoutParams ?: return
        val newFlags = if (enabled) {
            lp.flags or android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            lp.flags and android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        }
        if (newFlags == lp.flags) return
        lp.flags = newFlags
        updateWindowLayout(lp)
    }

    fun refreshToolbarLockIcon() {
        toolbar?.bindLockIcon(item.state)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if ((bitmap != null || pdfSource != null || epubWebView != null) && oldw == 0 && oldh == 0 && !restoreContentTransform) {
            fitContentToFrame()
            callbacks.onItemUpdated(this, currentItem)
        }
    }

    private fun loadContent(uri: String, fitAfterLoad: Boolean) {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.Main) {
            pdfSource?.close()
            pdfSource = null
            epubWebView?.let { removeView(it); it.destroy() }
            epubWebView = null
            epubBook = null
            pdfPageCache.evictAll()
            pendingPdfPages.clear()
            pdfRenderJobs.values.forEach { it.cancel() }
            pdfRenderJobs.clear()
            if (item.mediaKind == MediaKind.Pdf) {
                pdfSource = withContext(Dispatchers.IO) { PdfPageSource.open(context, uri) }
                buildPdfLayout()
                bitmap = null
            } else if (item.mediaKind == MediaKind.Epub) {
                epubBook = withContext(Dispatchers.IO) { EpubBookSource.prepare(context, uri) }
                bitmap = null
                epubBook?.let { setupEpubView(it, fitAfterLoad) }
            } else {
                bitmap = withContext(Dispatchers.IO) { BitmapLoader.load(context, uri) }
            }
            if ((bitmap != null || pdfSource != null) && width > 0 && height > 0 && fitAfterLoad) {
                fitContentToFrame()
                callbacks.onItemUpdated(this@FloatingItemView, currentItem)
            }
            invalidate()
        }
    }

    fun replaceImage(uri: String) {
        BitmapLoader.evict(item.imageUri)
        val updated = item.copy(imageUri = uri, mediaKind = MediaKind.Image, content = ContentTransform())
        applyExternalUpdate(updated)
        callbacks.onItemUpdated(this, updated)
        loadContent(uri, fitAfterLoad = true)
    }

    private fun buildPdfLayout() {
        pdfPageRects.clear()
        val pages = pdfSource?.pages.orEmpty()
        pdfDocumentWidth = pages.maxOfOrNull { it.width }?.toFloat()?.coerceAtLeast(1f) ?: 1f
        var top = PDF_PAGE_GAP
        pages.forEach { page ->
            val left = (pdfDocumentWidth - page.width) / 2f
            pdfPageRects += RectF(left, top, left + page.width, top + page.height)
            top += page.height + PDF_PAGE_GAP
        }
    }

    private fun fitContentToFrame() {
        if (item.mediaKind == MediaKind.Epub) {
            epubWebView?.settings?.textZoom = 100
            epubWebView?.scrollTo(0, 0)
            invalidate()
            return
        }
        if (item.mediaKind == MediaKind.Pdf && pdfSource != null) {
            val scale = ((width - 16f.dp) / pdfDocumentWidth).coerceAtLeast(0.01f)
            contentMatrix.reset()
            contentMatrix.postScale(scale, scale)
            contentMatrix.postTranslate((width - pdfDocumentWidth * scale) / 2f, 0f)
            invalidate()
            return
        }
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val normalizedRotation = ((contentRotation % 360f) + 360f) % 360f
        val isQuarterTurn = normalizedRotation in 45f..134.999f || normalizedRotation in 225f..314.999f
        val visualWidth = if (isQuarterTurn) bmp.height.toFloat() else bmp.width.toFloat()
        val visualHeight = if (isQuarterTurn) bmp.width.toFloat() else bmp.height.toFloat()
        val scale = minOf(width / visualWidth, height / visualHeight)
        val w = bmp.width * scale
        val h = bmp.height * scale
        contentMatrix.reset()
        contentMatrix.postScale(scale, scale)
        contentMatrix.postTranslate((width - w) / 2f, (height - h) / 2f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (item.mediaKind == MediaKind.Epub) return
        if (item.mediaKind == MediaKind.Pdf && pdfSource != null) {
            drawPdf(canvas)
            drawPinBorder(canvas)
            if (editMode == EditMode.FrameEdit) drawEditFrame(canvas)
            return
        }
        val bmp = bitmap
        if (bmp != null) {
            val save = canvas.save()
            canvas.rotate(contentRotation, width / 2f, height / 2f)
            canvas.concat(contentMatrix)
            bitmapRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
            canvas.drawBitmap(bmp, null, bitmapRect, bitmapPaint)
            canvas.restoreToCount(save)
        } else {
            canvas.drawText(context.getString(app.pinimage.R.string.loading), 12f.dp, height / 2f, textPaint)
        }

        drawPinBorder(canvas)

        if (editMode == EditMode.FrameEdit) drawEditFrame(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (item.mediaKind == MediaKind.Epub) {
            drawPinBorder(canvas)
            if (editMode == EditMode.FrameEdit) drawEditFrame(canvas)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupEpubView(book: EpubBook, fitAfterLoad: Boolean) {
        val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                showToolbar()
                return false
            }
            override fun onLongPress(event: MotionEvent) {
                if (item.state == LockState.Unlocked) {
                    callbacks.onRequestEditMode(this@FloatingItemView)
                    showToolbar()
                }
            }
        })
        val web = WebView(context).apply {
            setBackgroundColor(Color.WHITE)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.javaScriptEnabled = false
            settings.allowContentAccess = false
            settings.allowFileAccess = true
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.blockNetworkLoads = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            settings.textZoom = if (fitAfterLoad) 100 else (item.content.zoom * 100).toInt().coerceIn(70, 200)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    request?.url?.scheme !in setOf("file", "data", "about")

                override fun onPageFinished(view: WebView, url: String?) {
                    val targetY = if (fitAfterLoad) 0 else item.content.offsetY.toInt().coerceAtLeast(0)
                    view.post { view.scrollTo(0, targetY); persistEpubPosition() }
                }
            }
            setOnTouchListener { _, event -> gesture.onTouchEvent(event); false }
            setOnScrollChangeListener { _, _, _, _, _ ->
                epubSaveJob?.cancel()
                epubSaveJob = scope.launch { delay(250); persistEpubPosition() }
            }
        }
        epubWebView = web
        addView(web, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            setMargins(1, 1, 1, 1)
        })
        web.loadUrl(book.readingFile.toURI().toString())
    }

    private fun persistEpubPosition() {
        if (item.mediaKind == MediaKind.Epub) callbacks.onItemUpdated(this, currentItem)
    }

    private fun drawEditFrame(canvas: Canvas) {
        canvas.drawRect(edgePadding, edgePadding, width - edgePadding, height - edgePadding, borderPaint)
        drawHandle(canvas, edgePadding, edgePadding)
        drawHandle(canvas, width - edgePadding, edgePadding)
        drawHandle(canvas, edgePadding, height - edgePadding)
        drawHandle(canvas, width - edgePadding, height - edgePadding)
    }

    private fun drawPdf(canvas: Canvas) {
        val source = pdfSource ?: return
        val save = canvas.save()
        canvas.rotate(contentRotation, width / 2f, height / 2f)
        canvas.concat(contentMatrix)
        val screenRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val mapped = RectF()
        val renderWidth = pdfRenderWidth()
        var currentPage = -1
        val visibleKeys = mutableSetOf<String>()
        val visiblePages = mutableListOf<Int>()
        pdfPageRects.forEachIndexed { index, pageRect ->
            mapped.set(pageRect)
            contentMatrix.mapRect(mapped)
            if (!RectF.intersects(mapped, screenRect)) return@forEachIndexed
            if (currentPage < 0) currentPage = index
            visiblePages += index
            canvas.drawRect(pageRect.left - 3f, pageRect.top + 3f, pageRect.right + 3f, pageRect.bottom + 6f, pdfShadowPaint)
            canvas.drawRect(pageRect, pdfPagePaint)
            val key = "$index:$renderWidth"
            visibleKeys += key
            val pageBitmap = pdfPageCache.get(key)
            if (pageBitmap != null) {
                canvas.drawBitmap(pageBitmap, null, pageRect, bitmapPaint)
            } else {
                requestPdfPage(source, index, renderWidth, key)
            }
        }
        pdfRenderJobs.filterKeys { it !in visibleKeys }.values.forEach { it.cancel() }
        canvas.restoreToCount(save)
        if (currentPage >= 0) {
            pdfCurrentPage = currentPage + 1
            drawPdfPageCounter(canvas, pdfCurrentPage, source.pages.size)
            // A single adjacent page makes normal scrolling feel immediate without building a
            // long render queue when the user quickly jumps through the document.
            val prefetchIndex = (visiblePages.maxOrNull() ?: currentPage) + 1
            if (prefetchIndex < source.pages.size) {
                val prefetchKey = "$prefetchIndex:$renderWidth"
                if (pdfPageCache.get(prefetchKey) == null) requestPdfPage(source, prefetchIndex, renderWidth, prefetchKey)
            }
        }
    }

    private fun drawPdfPageCounter(canvas: Canvas, page: Int, total: Int) {
        val label = "$page / $total"
        val boxWidth = maxOf(56f.dp, pdfCounterTextPaint.measureText(label) + 20f.dp)
        val boxHeight = 28f.dp
        val rect = RectF(width - boxWidth - 10f.dp, height - boxHeight - 10f.dp, width - 10f.dp, height - 10f.dp)
        canvas.drawRoundRect(rect, boxHeight / 2f, boxHeight / 2f, pdfCounterBackgroundPaint)
        val baseline = rect.centerY() - (pdfCounterTextPaint.ascent() + pdfCounterTextPaint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, pdfCounterTextPaint)
    }

    private fun pdfRenderWidth(): Int {
        val visibleWidth = ceil(pdfDocumentWidth * readScale()).toInt().coerceAtLeast(width)
        return (ceil(visibleWidth / 512f).toInt() * 512).coerceIn(512, 2048)
    }

    private fun requestPdfPage(source: PdfPageSource, index: Int, width: Int, key: String) {
        if (!pendingPdfPages.add(key)) return
        pdfRenderJobs[key] = scope.launch {
            val rendered = runCatching { withContext(Dispatchers.IO) { source.render(index, width) } }.getOrNull()
            pendingPdfPages.remove(key)
            pdfRenderJobs.remove(key)
            if (rendered != null && pdfSource === source) {
                pdfPageCache.put(key, rendered)
                invalidate()
            } else if (rendered != null) {
                rendered.recycle()
            }
        }
    }

    override fun onDetachedFromWindow() {
        loadJob?.cancel()
        epubSaveJob?.cancel()
        epubWebView?.destroy()
        epubWebView = null
        pdfRenderJobs.values.forEach { it.cancel() }
        pdfRenderJobs.clear()
        pdfSource?.close()
        pdfSource = null
        pdfPageCache.evictAll()
        super.onDetachedFromWindow()
    }

    private fun drawPinBorder(canvas: Canvas) {
        when (item.display.borderStyle) {
            PinBorderStyle.None -> Unit
            PinBorderStyle.Hairline -> {
                pinBorderPaint.color = 0x66000000
                pinBorderPaint.strokeWidth = 1f
                canvas.drawRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, pinBorderPaint)
            }
            PinBorderStyle.Outline -> {
                pinBorderPaint.color = 0xCCFFFFFF.toInt()
                pinBorderPaint.strokeWidth = 3f.dp
                val outer = 1.5f.dp
                canvas.drawRect(outer, outer, width - outer, height - outer, pinBorderPaint)
                pinBorderPaint.color = 0x66000000
                pinBorderPaint.strokeWidth = 1f.dp
                val inner = 3f.dp
                canvas.drawRect(inner, inner, width - inner, height - inner, pinBorderPaint)
            }
            PinBorderStyle.SoftShadow -> {
                listOf(
                    1f.dp to 0x55000000,
                    3f.dp to 0x35000000,
                    6f.dp to 0x1C000000,
                ).forEach { (inset, color) ->
                    pinBorderPaint.color = color
                    pinBorderPaint.strokeWidth = inset * 2f
                    canvas.drawRect(inset, inset, width - inset, height - inset, pinBorderPaint)
                }
            }
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
            hideToolbar()
            onTapOutside?.invoke()
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
        private const val TOOLBAR_AUTO_HIDE_MS = 5_000L
        private const val PDF_CACHE_BYTES = 64 * 1024 * 1024
        private const val PDF_PAGE_GAP = 18f
    }

    private fun handleDown(ev: MotionEvent) {
        downRawX = ev.rawX
        downRawY = ev.rawY
        activePointerId = ev.getPointerId(0)
        didPanSinceDown = false
        mode = TouchMode.Idle

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
        showToolbar()
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
                val effectiveFactor = if (baseScale == 0f) 1f else newScale / baseScale
                values[Matrix.MSCALE_X] = newScale
                values[Matrix.MSCALE_Y] = newScale
                values[Matrix.MTRANS_X] = focus.first - (focus.first - values[Matrix.MTRANS_X]) * effectiveFactor
                values[Matrix.MTRANS_Y] = focus.second - (focus.second - values[Matrix.MTRANS_Y]) * effectiveFactor
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
                        updateWindowLayout(lp)
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
                    updateWindowLayout(lp)
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
        val visibleToolbar = toolbar?.takeIf { it.visibility == VISIBLE }
        if (visibleToolbar != null &&
            ev.y >= visibleToolbar.top && ev.y <= visibleToolbar.bottom &&
            visibleToolbar.performActionAt(ev.x - visibleToolbar.left, ev.y - visibleToolbar.top)
        ) {
            mode = TouchMode.Idle
            activePointerId = MotionEvent.INVALID_POINTER_ID
            return
        }
        val wasIdleTap = mode == TouchMode.ContentPan && !didPanSinceDown && editMode == EditMode.View
        val wasFrameTap = mode == TouchMode.FrameMove &&
            abs(ev.rawX - downRawX) <= touchSlop && abs(ev.rawY - downRawY) <= touchSlop
        if (wasIdleTap || wasFrameTap) {
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

    private fun updateWindowLayout(params: android.view.WindowManager.LayoutParams) {
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            wm.updateViewLayout(this, params)
        }
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
