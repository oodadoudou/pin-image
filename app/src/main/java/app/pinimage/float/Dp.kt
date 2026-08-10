package app.pinimage.float

import android.content.Context
import android.util.TypedValue

fun Float.dpToPx(context: Context): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, context.resources.displayMetrics)

val Float.dp: Float
    get() = FloatingDp.dp(this)

object FloatingDp {
    private var density: Float = 1f
    fun init(context: Context) {
        density = context.resources.displayMetrics.density
    }
    fun dp(value: Float): Float = value * density
}
