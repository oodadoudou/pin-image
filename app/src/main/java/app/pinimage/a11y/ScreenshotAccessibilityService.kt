package app.pinimage.a11y

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.util.TypedValue
import app.pinimage.R
import app.pinimage.data.AppContainer
import app.pinimage.edit.EditActivity
import app.pinimage.float.FloatController
import app.pinimage.float.FloatService
import app.pinimage.util.saveBitmapToGallery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ScreenshotAccessibilityService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer

    private var bubble: View? = null
    private var quickBar: View? = null
    private var pendingScreenshotFile: File? = null

    private val downRaw = FloatArray(2)
    private var moved = false
    private var longPressFired = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        container = (applicationContext as app.pinimage.PinImageApp).container
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubbleIfEnabled()
        scope.launch {
            container.settings.snapshot.collect { settings ->
                if (settings.floatingButton) showBubbleIfEnabled() else removeBubble()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeBubble()
        removeQuickBar()
        scope.cancel()
        super.onDestroy()
    }

    fun triggerScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    scope.launch {
                        val hwBuffer = screenshot.hardwareBuffer
                        val bitmap = hwBuffer.use { buffer ->
                            android.graphics.Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        }?.copy(Bitmap.Config.ARGB_8888, false)
                        if (bitmap == null) return@launch
                        val file = File(cacheDir, "shot_${System.currentTimeMillis()}.png")
                        withContext(Dispatchers.IO) {
                            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                        pendingScreenshotFile = file
                        val settings = container.settings.snapshot.first()
                        if (settings.autoSaveScreenshot) {
                            withContext(Dispatchers.IO) {
                                saveBitmapToGallery(this@ScreenshotAccessibilityService, bitmap, "PinImage_${System.currentTimeMillis()}")
                            }
                        }
                        if (settings.instantPin) {
                            FloatController.pin(Uri.fromFile(file).toString())
                            removeQuickBar()
                        } else {
                            showQuickBar(Uri.fromFile(file).toString())
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {}
            },
        )
    }

    private fun showQuickBar(uri: String) {
        removeQuickBar()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xF21C1C1E.toInt())
            }
            elevation = dp(16).toFloat()
        }
        fun label(text: String, onClick: () -> Unit) {
            val btn = Button(this).apply {
                this.text = text
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(14), dp(7), dp(14), dp(7))
                background = GradientDrawable().apply {
                    cornerRadius = dp(11).toFloat()
                    setColor(Color.TRANSPARENT)
                }
                setOnClickListener { onClick() }
            }
            row.addView(btn)
        }
        label(getString(R.string.pin)) {
            FloatController.pin(uri)
            removeQuickBar()
        }
        label(getString(R.string.edit)) {
            startActivity(
                Intent(this, EditActivity::class.java)
                    .putExtra(EditActivity.EXTRA_URI, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            removeQuickBar()
        }
        label(getString(R.string.save)) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    android.graphics.BitmapFactory.decodeFile(uri.removePrefix("file://"))
                }
                if (bmp != null) saveBitmapToGallery(this@ScreenshotAccessibilityService, bmp, "PinImage_${System.currentTimeMillis()}")
            }
            removeQuickBar()
        }
        label(getString(R.string.close)) { removeQuickBar() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80
        }
        wm.addView(row, params)
        quickBar = row
    }

    private fun removeQuickBar() {
        quickBar?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        quickBar = null
    }

    private val longPressRunnable = Runnable {
        if (!moved) {
            longPressFired = true
            bubble?.let { showBubbleMenu(it.layoutParams as WindowManager.LayoutParams) }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    private fun showBubbleIfEnabled() {
        if (bubble != null) return
        val size = (48 * resources.displayMetrics.density).toInt()
        val bubbleView = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xF21C1C1E.toInt())
            }
            elevation = dp(12).toFloat()
            val pad = (14 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            val dot = View(this@ScreenshotAccessibilityService).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
            addView(dot)
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 20
            y = 240
        }
        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = params.x
            private var initialY = params.y
            private val touchSlop = (8 * resources.displayMetrics.density).toInt()

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRaw[0] = event.rawX; downRaw[1] = event.rawY
                        initialX = params.x; initialY = params.y
                        moved = false; longPressFired = false
                        v.postDelayed(longPressRunnable, 500)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downRaw[0]).toInt()
                        val dy = (event.rawY - downRaw[1]).toInt()
                        if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                            moved = true
                            v.removeCallbacks(longPressRunnable)
                            params.x = initialX + dx
                            params.y = initialY + dy
                            try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        v.removeCallbacks(longPressRunnable)
                        if (!moved && !longPressFired) triggerScreenshot()
                        if (moved) snapToEdge(bubbleView, params)
                    }
                }
                return true
            }
        })
        wm.addView(bubbleView, params)
        bubble = bubbleView
    }

    @SuppressLint("RtlHardcoded")
    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val screenW = resources.displayMetrics.widthPixels
        val centerX = params.x + view.width / 2
        val targetX = if (centerX < screenW / 2) 0 else screenW - view.width
        params.x = targetX
        try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
    }

    @SuppressLint("RtlHardcoded")
    private fun showBubbleMenu(bubbleParams: WindowManager.LayoutParams) {
        lateinit var menu: LinearLayout
        fun closeMenu() {
            try { wm.removeView(menu) } catch (_: Exception) {}
        }
        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xF7FFFFFF.toInt())
            }
            elevation = dp(18).toFloat()
        }
        fun add(text: String, onClick: () -> Unit) {
            val btn = Button(this).apply {
                this.text = text
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(0xFF1C1C1E.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(14), 0, dp(14), 0)
                background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                setOnClickListener { onClick() }
            }
            menu.addView(btn, LinearLayout.LayoutParams(dp(220), dp(46)))
        }
        add(getString(R.string.screenshot)) { triggerScreenshot(); closeMenu() }
        add(getString(R.string.open_board)) {
            startActivity(
                Intent(this, app.pinimage.board.BoardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            closeMenu()
        }
        add(getString(R.string.hide_all_pins)) { FloatController.hideAll(); closeMenu() }
        add(getString(R.string.open_app)) {
            startActivity(packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            closeMenu()
        }
        add(getString(R.string.close_menu)) { closeMenu() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = bubbleParams.x.coerceAtLeast(20)
            y = (bubbleParams.y + 60).coerceAtLeast(80)
        }
        wm.addView(menu, params)
        menu.tag = params
    }

    private fun removeBubble() {
        bubble?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        bubble = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
