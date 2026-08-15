package app.pinimage.data

import app.pinimage.board.Board
import app.pinimage.board.BoardBackground
import app.pinimage.board.BoardObject
import app.pinimage.float.ContentTransform
import app.pinimage.float.DisplayProps
import app.pinimage.float.FloatingItem
import app.pinimage.float.FrameTransform
import app.pinimage.float.LockState
import app.pinimage.float.MediaKind
import app.pinimage.float.PinBorderStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonCodecTest {
    @Test
    fun floatingItemRoundTripPreservesInteractiveState() {
        val expected = listOf(
            FloatingItem(
                id = "pin-1",
                imageUri = "file:///persistent/pin.png",
                frame = FrameTransform(12, 34, 456, 789),
                content = ContentTransform(2.5f, -40f, 83f, 90f),
                display = DisplayProps(0.42f, 7, PinBorderStyle.SoftShadow),
                state = LockState.FullyLocked,
            ),
        )

        assertEquals(expected, JsonCodec.decodeFloatingItems(JsonCodec.encodeFloatingItems(expected)))
    }

    @Test
    fun legacyFloatingItemWithoutBorderDefaultsToHairline() {
        val raw = """[{"id":"old","imageUri":"file:///old.png","frame":{"x":0,"y":0,"width":100,"height":100},"display":{"opacity":1.0,"zIndex":0}}]"""
        assertEquals(PinBorderStyle.Hairline, JsonCodec.decodeFloatingItems(raw).single().display.borderStyle)
        assertEquals(MediaKind.Image, JsonCodec.decodeFloatingItems(raw).single().mediaKind)
    }

    @Test
    fun pdfKindSurvivesRoundTrip() {
        val item = FloatingItem(
            id = "pdf",
            imageUri = "file:///document.pdf",
            frame = FrameTransform(0, 0, 300, 500),
            mediaKind = MediaKind.Pdf,
            content = ContentTransform(1.5f, 8f, -900f, 0f),
        )
        assertEquals(item, JsonCodec.decodeFloatingItems(JsonCodec.encodeFloatingItems(listOf(item))).single())
    }

    @Test
    fun epubKindAndReadingPositionSurviveRoundTrip() {
        val item = FloatingItem(
            id = "epub",
            imageUri = "file:///book.epub",
            frame = FrameTransform(10, 20, 320, 520),
            mediaKind = MediaKind.Epub,
            content = ContentTransform(1.25f, 0f, 1840f, 0f),
        )
        assertEquals(item, JsonCodec.decodeFloatingItems(JsonCodec.encodeFloatingItems(listOf(item))).single())
    }

    @Test
    fun boardRoundTripPreservesObjectEditingState() {
        val expected = listOf(
            Board(
                id = "board-1",
                name = "References",
                canvasWidth = 1200,
                canvasHeight = 900,
                background = BoardBackground.Transparent,
                objects = listOf(
                    BoardObject("object-1", "content://image", 10f, -20f, 300f, 200f, 90f, true, false, 3),
                ),
                createdAt = 1234L,
            ),
        )

        assertEquals(expected, JsonCodec.decodeBoards(JsonCodec.encodeBoards(expected)))
    }

    @Test
    fun recentRoundTripKeepsOrder() {
        val expected = listOf("first", "second", "third")
        assertEquals(expected, JsonCodec.decodeRecent(JsonCodec.encodeRecent(expected)))
    }
}
