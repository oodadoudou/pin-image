package app.pinimage.board

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class BoardContentBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

/** The complete visible Board area, including rotated objects beyond the nominal canvas. */
fun Board.contentBounds(): BoardContentBounds {
    var left = 0f
    var top = 0f
    var right = canvasWidth.coerceAtLeast(1).toFloat()
    var bottom = canvasHeight.coerceAtLeast(1).toFloat()
    objects.forEach { obj ->
        val radians = Math.toRadians(obj.rotation.toDouble())
        val cos = abs(cos(radians)).toFloat()
        val sin = abs(sin(radians)).toFloat()
        val halfWidth = obj.width / 2f
        val halfHeight = obj.height / 2f
        val extentX = cos * halfWidth + sin * halfHeight
        val extentY = sin * halfWidth + cos * halfHeight
        val centerX = obj.x + halfWidth
        val centerY = obj.y + halfHeight
        left = minOf(left, centerX - extentX)
        top = minOf(top, centerY - extentY)
        right = maxOf(right, centerX + extentX)
        bottom = maxOf(bottom, centerY + extentY)
    }
    return BoardContentBounds(left, top, right, bottom)
}
