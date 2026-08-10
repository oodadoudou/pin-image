package app.pinimage

import android.app.Application
import app.pinimage.data.AppContainer

class PinImageApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
