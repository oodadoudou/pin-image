package app.pinimage.float

import java.lang.ref.WeakReference

/**
 * Allows the UI/activity layer to look up a currently-shown FloatingItemView
 * without binding to the service. Held as weak refs so views can be GC'd when
 * the service removes them.
 */
object ViewRegistry {
    private val views = mutableMapOf<String, WeakReference<FloatingItemView>>()

    fun register(id: String, view: FloatingItemView) {
        views[id] = WeakReference(view)
    }

    fun unregister(id: String) {
        views.remove(id)
    }

    fun get(id: String): FloatingItemView? = views[id]?.get()
}
