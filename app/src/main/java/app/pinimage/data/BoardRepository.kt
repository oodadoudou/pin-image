package app.pinimage.data

import android.content.Context
import app.pinimage.board.Board
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class BoardRepository(context: Context) {

    private val file: File = File(context.noBackupFilesDir, "boards.json").apply {
        parentFile?.mkdirs()
    }

    private val _boards = MutableStateFlow(load())
    val boards: StateFlow<List<Board>> = _boards.asStateFlow()

    fun upsert(board: Board) {
        val list = _boards.value.toMutableList()
        val index = list.indexOfFirst { it.id == board.id }
        if (index >= 0) list[index] = board else list.add(0, board)
        _boards.value = list
        save(list)
    }

    fun delete(id: String) {
        val list = _boards.value.filterNot { it.id == id }
        _boards.value = list
        save(list)
    }

    fun get(id: String): Board? = _boards.value.firstOrNull { it.id == id }

    private fun load(): List<Board> = try {
        if (file.exists()) JsonCodec.decodeBoards(file.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(value: List<Board>) {
        try {
            file.writeText(JsonCodec.encodeBoards(value))
        } catch (_: Exception) {
        }
    }
}
