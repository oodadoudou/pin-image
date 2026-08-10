package app.pinimage.data

import android.content.Context

class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    val settings = AppSettings(applicationContext)
    val floatingItems = FloatingItemRepository(applicationContext)
    val recent = RecentRepository(applicationContext)
    val boards = BoardRepository(applicationContext)
}
