package app.pinimage.float

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.annotation.StringRes
import app.pinimage.R

/** Compact two-level menu for a selected floating image. */
class FloatingToolbar(
    context: Context,
    private val view: FloatingItemView,
) : LinearLayout(context) {

    private val primary = actionGrid()
    private val secondary = actionGrid().apply { visibility = GONE }
    private var lockButton: Button? = null

    init {
        orientation = VERTICAL
        setPadding(7.dp, 7.dp, 7.dp, 7.dp)
        background = GradientDrawable().apply {
            cornerRadius = 22.dp.toFloat()
            setColor(0xF7FFFFFF.toInt())
            setStroke(1.dp, 0x24000000)
        }
        elevation = 18.dp.toFloat()
        addView(primary)
        addView(secondary)

        lockButton = addAction(primary, R.string.lock) {
            val next = when (view.originalItem.state) {
                LockState.Unlocked -> LockState.FrameLocked
                LockState.FrameLocked -> LockState.FullyLocked
                LockState.FullyLocked -> LockState.Unlocked
            }
            view.callbacks.onItemUpdated(view, view.currentItem.copy(state = next))
            bindLockIcon(next)
        }
        if (!view.isPdf && !view.isEpub) addAction(primary, R.string.edit) { view.callbacks.onEdit(view) }
        addAction(primary, R.string.opacity) {
            OpacityPopup.show(context, this, view.currentOpacity()) { view.setOpacity(it) }
        }
        if (!view.isEpub) addAction(primary, R.string.rotate) { view.rotateContentClockwise() }
        if (view.isEpub) addAction(primary, R.string.reading) { EpubReadingPopup.show(context, this, view) }
        addAction(primary, R.string.border) {
            BorderPopup.show(context, this, view.currentItem.display.borderStyle) { view.setBorderStyle(it) }
        }
        addAction(primary, R.string.more) {
            val expanding = secondary.visibility != VISIBLE
            secondary.visibility = if (expanding) VISIBLE else GONE
        }

        addAction(secondary, R.string.fit_content) { view.fitContentToFrameAndPersist() }
        if (view.isPdf) {
            addAction(secondary, R.string.pages) {
                PageJumpPopup.show(context, this, view.currentPdfPage(), view.pdfPageCount()) { page ->
                    view.jumpToPdfPage(page)
                }
            }
        }
        addAction(secondary, R.string.hide) { view.callbacks.onHide(view) }
        addAction(secondary, R.string.replace) { view.callbacks.onReplace(view) }
        addAction(secondary, R.string.copy) { view.callbacks.onDuplicate(view) }
        addAction(secondary, R.string.front) { view.callbacks.onBringToFront(view) }
        addAction(secondary, R.string.back) { view.callbacks.onSendToBack(view) }
        if (!view.isPdf && !view.isEpub) addAction(secondary, R.string.save) { view.callbacks.onSave(view) }
        addAction(secondary, R.string.close, destructive = true) { view.callbacks.onClose(view) }
        bindLockIcon(view.originalItem.state)
    }

    private fun actionGrid() = GridLayout(context).apply {
        columnCount = 3
        alignmentMode = GridLayout.ALIGN_BOUNDS
        useDefaultMargins = false
    }

    private fun addAction(grid: GridLayout, @StringRes labelRes: Int, destructive: Boolean = false, action: () -> Unit): Button {
        val button = Button(context).apply {
            text = context.getString(labelRes)
            setTextColor(if (destructive) 0xFFFF3B30.toInt() else 0xFF007AFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(5.dp, 2.dp, 5.dp, 2.dp)
            background = GradientDrawable().apply {
                cornerRadius = 12.dp.toFloat()
                setColor(0xFFF2F2F7.toInt())
            }
            setOnClickListener {
                view.showToolbar()
                action()
            }
        }
        grid.addView(button, GridLayout.LayoutParams().apply {
            width = 76.dp
            height = 36.dp
            setMargins(2.dp, 2.dp, 2.dp, 2.dp)
        })
        return button
    }

    fun bindLockIcon(state: LockState) {
        lockButton?.text = context.getString(
            when (state) {
                LockState.Unlocked -> R.string.lock
                LockState.FrameLocked -> R.string.frame_lock
                LockState.FullyLocked -> R.string.full_lock
            },
        )
    }

    fun collapse() {
        secondary.visibility = GONE
    }

    /** Fallback hit dispatch for overlay parents that consume the touch stream. */
    fun performActionAt(localX: Float, localY: Float): Boolean {
        listOf(primary, secondary).filter { it.visibility == View.VISIBLE }.forEach { grid ->
            val y = localY - grid.top
            if (y < 0 || y > grid.height) return@forEach
            for (index in 0 until grid.childCount) {
                val child = grid.getChildAt(index)
                if (localX >= child.left && localX <= child.right && y >= child.top && y <= child.bottom) {
                    return child.performClick()
                }
            }
        }
        return false
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
