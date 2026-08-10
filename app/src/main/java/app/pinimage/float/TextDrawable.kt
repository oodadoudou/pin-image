package app.pinimage.float

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class TextDrawable(private val text: String) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas) {
        val y = bounds.exactCenterY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, bounds.exactCenterX(), y, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = (paint.measureText(text) + 24f).toInt()
    override fun getIntrinsicHeight(): Int = (paint.textSize + 16f).toInt()
}
