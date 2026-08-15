package app.pinimage.float

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import app.pinimage.R

object BorderPopup {
    fun show(context: Context, anchor: View, current: PinBorderStyle, onValue: (PinBorderStyle) -> Unit) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xF7FFFFFF.toInt())
            }
            elevation = dp(18).toFloat()
        }
        val options = listOf(
            PinBorderStyle.None to R.string.border_none,
            PinBorderStyle.Hairline to R.string.border_hairline,
            PinBorderStyle.Outline to R.string.border_outline,
            PinBorderStyle.SoftShadow to R.string.border_shadow,
        )
        lateinit var popup: PopupWindow
        options.forEach { (style, labelRes) ->
            container.addView(Button(context).apply {
                text = if (style == current) "✓  ${context.getString(labelRes)}" else context.getString(labelRes)
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(if (style == current) 0xFF007AFF.toInt() else 0xFF1C1C1E.toInt())
                setTextSize(15f)
                setPadding(dp(14), 0, dp(14), 0)
                minHeight = 0
                minimumHeight = 0
                background = ColorDrawable(Color.TRANSPARENT)
                setOnClickListener {
                    onValue(style)
                    popup.dismiss()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        }
        popup = PopupWindow(container, dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(18).toFloat()
        }
        popup.showAtLocation(anchor, Gravity.CENTER, 0, 0)
    }
}
