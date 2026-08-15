package app.pinimage.float

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import app.pinimage.R

object PageJumpPopup {
    fun show(context: Context, anchor: View, current: Int, total: Int, onPage: (Int) -> Unit) {
        if (total <= 0) return
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xF7FFFFFF.toInt())
            }
            elevation = dp(18).toFloat()
        }
        val label = TextView(context).apply {
            text = context.getString(R.string.page_value, current, total)
            setTextColor(0xFF1C1C1E.toInt())
            textSize = 16f
        }
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            selectAll()
            hint = "1–$total"
            setTextColor(0xFF1C1C1E.toInt())
            setSingleLine(true)
        }
        val seek = SeekBar(context).apply {
            max = (total - 1).coerceAtLeast(0)
            progress = (current - 1).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val page = progress + 1
                    label.text = context.getString(R.string.page_value, page, total)
                    input.setText(page.toString())
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    onPage((seekBar?.progress ?: 0) + 1)
                }
            })
        }
        lateinit var popup: PopupWindow
        val go = Button(context).apply {
            text = context.getString(R.string.go_to_page)
            isAllCaps = false
            setOnClickListener {
                val page = input.text.toString().toIntOrNull()?.coerceIn(1, total) ?: current
                onPage(page)
                popup.dismiss()
            }
        }
        container.addView(label)
        container.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(go, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        popup = PopupWindow(container, dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(18).toFloat()
        }
        popup.showAtLocation(anchor, Gravity.CENTER, 0, 0)
    }
}
