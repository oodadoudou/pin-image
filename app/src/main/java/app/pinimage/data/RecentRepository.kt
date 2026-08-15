package app.pinimage.data

import android.content.Context
import app.pinimage.float.FloatingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class RecentRepository(context: Context) {

    private val file: File = File(context.noBackupFilesDir, "recent_pins.json").apply {
        parentFile?.mkdirs()
    }

    private val _items = MutableStateFlow(load())
    private val writer = AtomicJsonWriter(file)
    val items: StateFlow<List<String>> = _items.asStateFlow()

    fun push(uri: String) {
        val next = (listOf(uri) + _items.value.filterNot { it == uri }).take(MAX)
        _items.value = next
        save(next)
    }

    fun clear() {
        _items.value = emptyList()
        save(emptyList())
    }

    fun remove(uri: String) {
        replaceAll(_items.value.filterNot { it == uri })
    }

    fun replaceAll(items: List<String>) {
        val next = items.distinct().take(MAX)
        _items.value = next
        save(next)
    }

    private fun load(): List<String> = try {
        JsonCodec.decodeRecent(readAtomicText(file))
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(value: List<String>) {
        writer.write(JsonCodec.encodeRecent(value))
    }

    companion object {
        const val MAX = 20
    }
}
