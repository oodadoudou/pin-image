package app.pinimage.data

import android.content.Context
import app.pinimage.float.FloatingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Stores the on-screen floating item state so it can be restored when the
 * float service is recreated. Plain JSON file in no-backup dir.
 */
class FloatingItemRepository(context: Context) {

    private val file: File = File(context.noBackupFilesDir, "floating_items.json").apply {
        parentFile?.mkdirs()
    }

    private val _items = MutableStateFlow(load())
    private val writer = AtomicJsonWriter(file)
    val items: StateFlow<List<FloatingItem>> = _items.asStateFlow()

    fun update(transform: (List<FloatingItem>) -> List<FloatingItem>) {
        val next = transform(_items.value)
        _items.value = next
        save(next)
    }

    fun replaceAll(items: List<FloatingItem>) {
        _items.value = items
        save(items)
    }

    private fun load(): List<FloatingItem> = try {
        JsonCodec.decodeFloatingItems(readAtomicText(file))
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(value: List<FloatingItem>) {
        writer.write(JsonCodec.encodeFloatingItems(value))
    }
}
