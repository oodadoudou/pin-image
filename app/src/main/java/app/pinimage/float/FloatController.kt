package app.pinimage.float

import android.content.Context
import android.content.Intent

/**
 * Thin static facade so non-service code (UI, accessibility service) can ask
 * the floating layer to do things without holding a service reference.
 */
object FloatController {

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun startControlPanel(context: Context) {
        appContext = context.applicationContext
        try {
            val intent = Intent(context, FloatService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    fun pin(uri: String) {
        val ctx = appContext ?: return
        FloatService.pinUri(ctx, uri)
    }

    fun closeAll() {
        appContext?.let { FloatService.send(it, FloatService.ACTION_CLOSE_ALL) }
    }

    fun hideAll() {
        appContext?.let { FloatService.send(it, FloatService.ACTION_HIDE_ALL) }
    }

    fun showAll() {
        appContext?.let { FloatService.send(it, FloatService.ACTION_SHOW_ALL) }
    }

    fun replaceItemImage(itemId: String, newUri: String) {
        appContext?.let {
            it.sendBroadcast(
                Intent(ACTION_REPLACE).setPackage(it.packageName)
                    .putExtra(EXTRA_ITEM_ID, itemId)
                    .putExtra(EXTRA_URI, newUri),
            )
        }
    }

    const val ACTION_REPLACE = "app.pinimage.action.REPLACE"
    const val EXTRA_ITEM_ID = "extra_item_id"
    const val EXTRA_URI = "extra_uri"
}
