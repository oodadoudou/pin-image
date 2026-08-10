package app.pinimage.float

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Stub floating window service. It is expanded in subsequent commits to host
 * actual floating image windows via WindowManager.
 */
class FloatService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PIN_URI -> {
                // Pinning is wired up when the floating item view is added.
            }
        }
        return START_STICKY
    }

    companion object {
        const val ACTION_PIN_URI = "app.pinimage.action.PIN_URI"
        const val EXTRA_URI = "extra_uri"
    }
}
