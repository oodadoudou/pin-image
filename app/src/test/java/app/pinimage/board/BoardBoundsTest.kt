package app.pinimage.board

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardBoundsTest {
    @Test
    fun includesObjectsOutsideEveryCanvasEdge() {
        val board = Board(
            id = "board",
            name = "Board",
            canvasWidth = 100,
            canvasHeight = 100,
            objects = listOf(BoardObject("image", "uri", -20f, -10f, 150f, 140f)),
        )
        assertEquals(BoardContentBounds(-20f, -10f, 130f, 130f), board.contentBounds())
    }

    @Test
    fun includesRotatedObjectCorners() {
        val board = Board(
            id = "board",
            name = "Board",
            canvasWidth = 1,
            canvasHeight = 1,
            objects = listOf(BoardObject("image", "uri", -50f, -20f, 100f, 40f, rotation = 90f)),
        )
        val bounds = board.contentBounds()
        assertEquals(-20f, bounds.left, 0.001f)
        assertEquals(-50f, bounds.top, 0.001f)
        assertEquals(20f, bounds.right, 0.001f)
        assertEquals(50f, bounds.bottom, 0.001f)
    }
}
