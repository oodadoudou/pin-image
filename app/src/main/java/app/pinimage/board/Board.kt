package app.pinimage.board

import kotlinx.serialization.Serializable

@Serializable
data class Board(
    val id: String,
    val name: String,
    val canvasWidth: Int = 1080,
    val canvasHeight: Int = 1080,
    val background: BoardBackground = BoardBackground.White,
    val objects: List<BoardObject> = emptyList(),
    val createdAt: Long = 0L,
)

@Serializable
enum class BoardBackground {
    White,
    Black,
    Transparent,
}

@Serializable
data class BoardObject(
    val id: String,
    val imageUri: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val zIndex: Int = 0,
)
