package app.pinimage.data

import app.pinimage.board.Board
import app.pinimage.float.FloatingItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object JsonCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeFloatingItems(items: List<FloatingItem>): String = json.encodeToString(items)
    fun decodeFloatingItems(raw: String): List<FloatingItem> =
        if (raw.isBlank()) emptyList() else json.decodeFromString(raw)

    fun encodeBoards(items: List<Board>): String = json.encodeToString(items)
    fun decodeBoards(raw: String): List<Board> =
        if (raw.isBlank()) emptyList() else json.decodeFromString(raw)

    fun encodeRecent(items: List<String>): String = json.encodeToString(items)
    fun decodeRecent(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else json.decodeFromString(raw)
}
