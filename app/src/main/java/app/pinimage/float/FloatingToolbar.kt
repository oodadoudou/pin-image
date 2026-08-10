package app.pinimage.float

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import app.pinimage.float.FloatingItemView.EditMode
import app.pinimage.float.LockState

/**
 * Minimal horizontal toolbar shown briefly after tapping a floating item in
 * view mode. Icons are text/emoji to avoid bundling drawable assets.
 */
class FloatingToolbar(
    context: Context,
    private val view: FloatingItemView,
) : HorizontalScrollView(context) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(4.dp, 4.dp, 4.dp, 4.dp)
    }

    private var lockButton: ImageButton? = null

    init {
        setBackgroundColor(0xCC111111.toInt())
        isHorizontalScrollBarEnabled = false
        addView(row)
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        layoutParams = lp

        lockButton = addButton("Lock") {
            val next = when (view.originalItem.state) {
                LockState.Unlocked -> LockState.FrameLocked
                LockState.FrameLocked -> LockState.FullyLocked
                LockState.FullyLocked -> LockState.Unlocked
            }
            notifyItemChanged(view.originalItem.copy(state = next))
            bindLockIcon(next)
        }
        bindLockIcon(view.originalItem.state)
        addButton("Edit") { view.callbacks.onEdit(view) }
        addButton("Opacity") { OpacityPopup.show(context, this, view.currentOpacity()) { view.setOpacity(it) } }
        addButton("Replace") { view.callbacks.onReplace(view) }
        addButton("Duplicate") { view.callbacks.onDuplicate(view) }
        addButton("Front") { view.callbacks.onBringToFront(view) }
        addButton("Back") { view.callbacks.onSendToBack(view) }
        addButton("Save") { view.callbacks.onSave(view) }
        addButton("Close") { view.callbacks.onClose(view) }
    }

    private fun addButton(label: String, onClick: () -> Unit): ImageButton {
        val btn = ImageButton(context).apply {
            setImageDrawable(TextDrawable(label))
            background = null
            setPadding(10.dp, 6.dp, 10.dp, 6.dp)
            setOnClickListener { onClick() }
        }
        row.addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return btn
    }

    private fun notifyItemChanged(newItem: FloatingItem) {
        view.callbacks.onItemUpdated(view, newItem)
    }

    fun bindLockIcon(state: LockState) {
        val label = when (state) {
            LockState.Unlocked -> "Unlocked"
            LockState.FrameLocked -> "Frame Lock"
            LockState.FullyLocked -> "Full Lock"
        }
        lockButton?.setImageDrawable(TextDrawable(label))
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
