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

    private fun load(): List<String> = try {
        if (file.exists()) JsonCodec.decodeRecent(file.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(value: List<String>) {
        try {
            file.writeText(JsonCodec.encodeRecent(value))
        } catch (_: Exception) {
        }
    }

    companion object {
        const val MAX = 20
    }
}
