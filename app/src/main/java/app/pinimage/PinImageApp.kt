package app.pinimage

import android.app.Application
import app.pinimage.data.AppContainer
import app.pinimage.float.FloatController
import app.pinimage.util.PersistentImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PinImageApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        FloatController.init(this)
        migrateLegacyImageUris()
    }

    private fun migrateLegacyImageUris() {
        appScope.launch {
            val recent = container.recent.items.value.map { uri ->
                PersistentImageStore.ensurePersistent(this@PinImageApp, uri)
            }
            container.recent.replaceAll(recent)

            val boards = container.boards.boards.value.map { board ->
                board.copy(objects = board.objects.map { obj ->
                    obj.copy(imageUri = PersistentImageStore.ensurePersistent(this@PinImageApp, obj.imageUri))
                })
            }
            container.boards.replaceAll(boards)
        }
    }
}
