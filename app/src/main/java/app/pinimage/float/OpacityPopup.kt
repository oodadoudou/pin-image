package app.pinimage.float

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import app.pinimage.R

object OpacityPopup {
    fun show(context: Context, anchor: View, current: Float, onValue: (Float) -> Unit) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xF7FFFFFF.toInt())
            }
            elevation = dp(18).toFloat()
        }
        val label = TextView(context).apply {
            text = context.getString(R.string.opacity_value, (current * 100).toInt())
            setTextColor(0xFF1C1C1E.toInt())
            textSize = 16f
        }
        val bar = SeekBar(context).apply {
            max = 80
            progress = ((current - 0.2f) * 100).toInt().coerceIn(0, 80)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = 0.2f + progress / 100f
                    label.text = context.getString(R.string.opacity_value, (value * 100).toInt())
                    onValue(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        container.addView(label)
        container.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val popup = PopupWindow(container, dp(280), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(18).toFloat()
        }
        popup.showAtLocation(anchor, Gravity.CENTER, 0, 0)
    }
}
