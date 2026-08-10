package app.pinimage.float

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView

object OpacityPopup {
    fun show(context: Context, anchor: View, current: Float, onValue: (Float) -> Unit) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.WHITE)
        }
        val label = TextView(context).apply {
            text = "Opacity ${(current * 100).toInt()}%"
            setTextColor(Color.BLACK)
        }
        val bar = SeekBar(context).apply {
            max = 80
            progress = ((current - 0.2f) * 100).toInt().coerceIn(0, 80)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = 0.2f + progress / 100f
                    label.text = "Opacity ${(value * 100).toInt()}%"
                    onValue(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        container.addView(label)
        container.addView(bar)
        val popup = PopupWindow(container, 480, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true
        popup.showAtLocation(anchor, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 80)
    }
}
