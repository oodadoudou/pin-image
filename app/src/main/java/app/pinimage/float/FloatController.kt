package app.pinimage.float

import android.content.Context
import android.content.Intent

/**
 * Static entry point for floating window operations. The real implementation
 * is added incrementally in later commits; this stub keeps the main UI
 * compilable and forwards pin requests once FloatService exists.
 */
object FloatController {

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun startControlPanel(context: Context) {
        appContext = context.applicationContext
        try {
            context.startService(Intent(context, FloatService::class.java))
        } catch (_: Exception) {
        }
    }

    fun pin(uri: String) {
        appContext?.let { ctx ->
            try {
                ctx.startService(
                    Intent(ctx, FloatService::class.java).apply {
                        action = FloatService.ACTION_PIN_URI
                        putExtra(FloatService.EXTRA_URI, uri)
                    },
                )
            } catch (_: Exception) {
            }
        }
    }
}
