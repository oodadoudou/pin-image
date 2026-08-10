package app.pinimage.float

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import app.pinimage.MainActivity
import app.pinimage.PinImageApp
import app.pinimage.R
import app.pinimage.util.BitmapLoader
import app.pinimage.util.saveBitmapToGallery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs

class FloatService : Service() {

    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val views = mutableMapOf<String, FloatingItemView>()
    private var nextZ = 0
    private var editModeViewId: String? = null

    private lateinit var container: app.pinimage.data.AppContainer

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as PinImageApp).container
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        FloatingDp.init(this)
        startForeground(NOTIF_ID, buildNotification("Reference Pin"))
        restoreFromRepository()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PIN_URI -> pinUri(intent.getStringExtra(EXTRA_URI) ?: return START_STICKY)
            ACTION_CLOSE_ALL -> closeAll()
            ACTION_HIDE_ALL -> setAllVisible(false)
            ACTION_SHOW_ALL -> setAllVisible(true)
            ACTION_SCREENSHOT -> Unit // handled by accessibility service directly
            ACTION_STOP -> {
                closeAll()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun restoreFromRepository() {
        scope.launch {
            val items = container.floatingItems.items.first()
            items.forEach { item -> addWindowFor(item) }
        }
    }

    private fun pinUri(uri: String) {
        scope.launch {
            val (w, h) = computeDefaultFrameSize(uri)
            val item = FloatingItem(
                id = UUID.randomUUID().toString(),
                imageUri = uri,
                frame = FrameTransform(
                    x = 40 + views.size * 24,
                    y = 200 + views.size * 24,
                    width = w,
                    height = h,
                ),
                display = DisplayProps(opacity = container.settings.snapshot.first().defaultOpacity),
            )
            addWindowFor(item)
            container.floatingItems.update { it + item }
            container.recent.push(uri)
        }
    }

    private suspend fun computeDefaultFrameSize(uri: String): Pair<Int, Int> {
        val metrics = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
        val settings = container.settings.snapshot.first()
        if (settings.rememberSize && settings.lastFrameWidth > 0 && settings.lastFrameHeight > 0) {
            return settings.lastFrameWidth to settings.lastFrameHeight
        }
        val bmp = withContext(Dispatchers.IO) { BitmapLoader.load(this@FloatService, uri) }
        val targetWidth = (metrics.widthPixels * 0.30f).toInt()
        val ratio = if (bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height else 1f
        val targetHeight = (targetWidth / ratio).toInt().coerceIn(200, metrics.heightPixels / 2)
        return targetWidth to targetHeight
    }

    private fun addWindowFor(item: FloatingItem) {
        val view = FloatingItemView(this, item, scope, callbacks)
        val params = WindowManager.LayoutParams(
            item.frame.width,
            item.frame.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = item.frame.x
            y = item.frame.y
        }
        wm.addView(view, params)
        views[item.id] = view
        ViewRegistry.register(item.id, view)
    }

    private fun updateItem(view: FloatingItemView, newItem: FloatingItem) {
        container.floatingItems.update { list ->
            list.map { if (it.id == newItem.id) newItem else it }
        }
    }

    private fun removeItem(id: String) {
        val view = views[id] ?: return
        try { wm.removeView(view) } catch (_: Exception) {}
        views.remove(id)
        ViewRegistry.unregister(id)
        container.floatingItems.update { list -> list.filterNot { it.id == id } }
        if (editModeViewId == id) editModeViewId = null
    }

    private fun closeAll() {
        views.keys.toList().forEach { removeItem(it) }
    }

    private fun setAllVisible(visible: Boolean) {
        views.values.forEach { it.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE }
    }

    private fun bringToFront(view: FloatingItemView) {
        val id = view.originalItem.id
        try { wm.removeView(view); wm.addView(view, view.layoutParams) } catch (_: Exception) {}
        views.remove(id); views[id] = view
        updateOrder()
    }

    private fun sendToBack(view: FloatingItemView) {
        val id = view.originalItem.id
        val others = views.filter { it.key != id }
        try { wm.removeView(view) } catch (_: Exception) {}
        views.remove(id)
        wm.addView(view, view.layoutParams)
        views[id] = view
        others.forEach { (_, v) ->
            try { wm.removeView(v); wm.addView(v, v.layoutParams) } catch (_: Exception) {}
        }
    }

    private fun updateOrder() {
        val items = container.floatingItems.items.value
        views.keys.forEach { id ->
            val v = views[id] ?: return@forEach
            val item = items.firstOrNull { it.id == id } ?: return@forEach
            // z handled by re-add order; no extra layout change needed.
            v.tag = item
        }
    }

    private fun duplicate(view: FloatingItemView) {
        val src = view.originalItem
        scope.launch {
            val copy = src.copy(
                id = UUID.randomUUID().toString(),
                frame = src.frame.copy(x = src.frame.x + 24, y = src.frame.y + 24),
            )
            addWindowFor(copy)
            container.floatingItems.update { it + copy }
            container.recent.push(copy.imageUri)
        }
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reference Pin", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(act: String, label: String): NotificationCompat.Action {
            val pi = PendingIntent.getService(
                this, act.hashCode(),
                Intent(this, FloatService::class.java).setAction(act),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Action.Builder(0, label, pi).build()
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Reference Pin")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(action(ACTION_HIDE_ALL, "Hide"))
            .addAction(action(ACTION_SHOW_ALL, "Show"))
            .addAction(action(ACTION_CLOSE_ALL, "Close All"))
            .build()
    }

    private val callbacks = object : FloatingItemView.Callbacks {
        override fun onItemUpdated(view: FloatingItemView, newItem: FloatingItem) {
            updateItem(view, newItem)
        }

        override fun onClose(view: FloatingItemView) = removeItem(view.originalItem.id)

        override fun onEdit(view: FloatingItemView) {
            // Launch the basic editor activity when it exists.
            val intent = Intent(this@FloatService, app.pinimage.edit.EditActivity::class.java).apply {
                putExtra(app.pinimage.edit.EditActivity.EXTRA_URI, view.originalItem.imageUri)
                putExtra(app.pinimage.edit.EditActivity.EXTRA_ITEM_ID, view.originalItem.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        override fun onReplace(view: FloatingItemView) {
            val intent = Intent(this@FloatService, app.pinimage.MainActivity::class.java).apply {
                action = ACTION_PICK_REPLACE
                putExtra(EXTRA_TARGET_ITEM_ID, view.originalItem.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        override fun onDuplicate(view: FloatingItemView) = duplicate(view)
        override fun onSave(view: FloatingItemView) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { BitmapLoader.load(this@FloatService, view.originalItem.imageUri) }
                if (bmp != null) saveBitmapToGallery(this@FloatService, bmp, "PinImage")
            }
        }
        override fun onBringToFront(view: FloatingItemView) = bringToFront(view)
        override fun onSendToBack(view: FloatingItemView) = sendToBack(view)

        override fun onRequestEditMode(view: FloatingItemView) {
            // Exit any prior edit mode first.
            views[editModeViewId]?.setEditMode(FloatingItemView.EditMode.View)
            editModeViewId = view.originalItem.id
            view.setEditMode(FloatingItemView.EditMode.FrameEdit)
        }

        override fun onExitEditMode() {
            val id = editModeViewId ?: return
            editModeViewId = null
            // Persist last frame size as the preferred size for future pins.
            val v = views[id] ?: return
            val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
            scope.launch {
                container.settings.setLastFrameSize(lp.width, lp.height)
                if (container.settings.snapshot.first().snapToEdge) {
                    val metrics = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
                    val snapThreshold = (24 * resources.displayMetrics.density).toInt()
                    var nx = lp.x; var ny = lp.y
                    if (abs(lp.x) < snapThreshold) nx = 0
                    if (abs(lp.x + lp.width - metrics.widthPixels) < snapThreshold) nx = metrics.widthPixels - lp.width
                    if (abs(lp.y) < snapThreshold) ny = 0
                    if (abs(lp.y + lp.height - metrics.heightPixels) < snapThreshold) ny = metrics.heightPixels - lp.height
                    if (nx != lp.x || ny != lp.y) {
                        lp.x = nx; lp.y = ny
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                        updateItem(v, v.currentItem.copy(frame = v.currentItem.frame.copy(x = nx, y = ny)))
                    }
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "pin_image_floating"
        const val NOTIF_ID = 1001
        const val ACTION_PIN_URI = "app.pinimage.action.PIN_URI"
        const val EXTRA_URI = "extra_uri"
        const val ACTION_PICK_REPLACE = "app.pinimage.action.PICK_REPLACE"
        const val EXTRA_TARGET_ITEM_ID = "extra_target_item_id"
        const val ACTION_CLOSE_ALL = "app.pinimage.action.CLOSE_ALL"
        const val ACTION_HIDE_ALL = "app.pinimage.action.HIDE_ALL"
        const val ACTION_SHOW_ALL = "app.pinimage.action.SHOW_ALL"
        const val ACTION_SCREENSHOT = "app.pinimage.action.SCREENSHOT"
        const val ACTION_STOP = "app.pinimage.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, FloatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun pinUri(context: Context, uri: String) {
            val intent = Intent(context, FloatService::class.java).apply {
                action = ACTION_PIN_URI
                putExtra(EXTRA_URI, uri)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun send(context: Context, action: String) {
            val intent = Intent(context, FloatService::class.java).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
