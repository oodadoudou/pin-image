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

object EpubReadingPopup {
    fun show(context: Context, anchor: View, view: FloatingItemView) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xFAFFFFFF.toInt())
                setStroke(dp(1), 0x22000000)
            }
            elevation = dp(18).toFloat()
        }
        val fontLabel = TextView(context).apply {
            text = context.getString(R.string.font_size_value, view.epubTextZoom())
            setTextColor(0xFF1C1C1E.toInt())
            textSize = 15f
        }
        val fontBar = SeekBar(context).apply {
            max = 130
            progress = (view.epubTextZoom() - 70).coerceIn(0, 130)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 70
                    fontLabel.text = context.getString(R.string.font_size_value, value)
                    if (fromUser) view.setEpubTextZoom(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val progressLabel = TextView(context).apply {
            text = context.getString(R.string.reading_progress_value, view.epubProgressPercent())
            setTextColor(0xFF1C1C1E.toInt())
            textSize = 15f
        }
        val progressBar = SeekBar(context).apply {
            max = 100
            progress = view.epubProgressPercent()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    progressLabel.text = context.getString(R.string.reading_progress_value, progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    view.jumpToEpubProgress(seekBar?.progress ?: 0)
                }
            })
        }
        container.addView(fontLabel)
        container.addView(fontBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(progressLabel)
        container.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        PopupWindow(container, dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(18).toFloat()
        }.showAtLocation(anchor, Gravity.CENTER, 0, 0)
    }
}
