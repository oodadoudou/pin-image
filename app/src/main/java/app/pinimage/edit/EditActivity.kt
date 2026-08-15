package app.pinimage.edit

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.view.Gravity
import android.util.TypedValue
import android.graphics.Typeface
import android.view.WindowInsetsController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.pinimage.util.BitmapLoader
import app.pinimage.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class EditActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var cropView: CropView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var source: Bitmap? = null
    private var current: Bitmap? = null // transformed, unfiltered base
    private var preview: Bitmap? = null
    private var adjustments = FilterAdjustments()
    private var renderJob: Job? = null
    private var renderVersion = 0
    private val retiredBitmaps = mutableListOf<Bitmap>()
    private val history = ArrayDeque<EditorState>()
    private var pendingRotation = 0
    private var flipH = false
    private var flipV = false
    private var sourceUri: String? = null
    private var itemId: String? = null

    private data class EditorState(val rotation: Int, val flipH: Boolean, val flipV: Boolean, val filter: FilterAdjustments)
    private enum class Parameter(val label: Int, val positiveOnly: Boolean = false) {
        Lightness(R.string.filter_lightness), Brightness(R.string.filter_brightness), Highlights(R.string.filter_highlights),
        Shadows(R.string.filter_shadows), Contrast(R.string.filter_contrast), Saturation(R.string.filter_saturation),
        Vibrance(R.string.filter_vibrance), Warmth(R.string.filter_warmth), Tint(R.string.filter_tint),
        Sharpness(R.string.filter_sharpness, true), Clarity(R.string.filter_clarity), Glow(R.string.filter_glow, true),
        LoFi(R.string.filter_lofi, true),
    }
    private var selectedParameter = Parameter.Lightness
    private lateinit var parameterLabel: TextView
    private lateinit var parameterBar: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        sourceUri = intent.getStringExtra(EXTRA_URI)
        itemId = intent.getStringExtra(EXTRA_ITEM_ID)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 14.dp, 20.dp, 10.dp)
            addView(TextView(this@EditActivity).apply {
                text = getString(R.string.crop_title)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@EditActivity).apply {
                text = getString(R.string.crop_instruction)
                setTextColor(0xFF8E8E93.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        }
        root.addView(header)

        cropView = CropView(this)
        root.addView(
            cropView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val toolbar = HorizontalScrollView(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 16.dp.toFloat()
                setColor(0xFF1C1C1E.toInt())
            }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(8.dp, 6.dp, 8.dp, 6.dp) }
        toolbar.addView(row)
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(12.dp, 6.dp, 12.dp, 6.dp)
        })

        addButton(row, getString(R.string.reset)) { reset() }
        addButton(row, getString(R.string.undo)) { undo() }
        addButton(row, getString(R.string.free)) { cropView.setAspect(0f) }
        addButton(row, "1:1") { cropView.setAspect(1f) }
        addButton(row, "4:3") { cropView.setAspect(4f / 3f) }
        addButton(row, "3:4") { cropView.setAspect(3f / 4f) }
        addButton(row, "16:9") { cropView.setAspect(16f / 9f) }
        addButton(row, "↶ 90°") { pushHistory(); rotate(-90f) }
        addButton(row, "↷ 90°") { pushHistory(); rotate(90f) }
        addButton(row, getString(R.string.flip_horizontal)) { pushHistory(); flipH = !flipH; applyTransform() }
        addButton(row, getString(R.string.flip_vertical)) { pushHistory(); flipV = !flipV; applyTransform() }

        root.addView(TextView(this).apply {
            text = getString(R.string.filter_presets)
            setTextColor(0xFF8E8E93.toInt()); textSize = 12f; setPadding(20.dp, 5.dp, 20.dp, 2.dp)
        })
        root.addView(scrollRow { presetRow ->
            PhotoFilters.presets.forEach { preset ->
                addButton(presetRow, getString(preset.name)) { pushHistory(); adjustments = preset.adjustments; syncParameter(); renderFilter() }
            }
        })
        root.addView(scrollRow { parameterRow ->
            Parameter.entries.forEach { parameter ->
                addButton(parameterRow, getString(parameter.label)) { selectedParameter = parameter; syncParameter() }
            }
        })
        parameterLabel = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; setPadding(20.dp, 4.dp, 20.dp, 0)
        }
        root.addView(parameterLabel)
        parameterBar = SeekBar(this).apply {
            max = 200
            setPadding(20.dp, 0, 20.dp, 2.dp)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) { pushHistory() }
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val raw = progress - 100
                    val value = if (selectedParameter.positiveOnly) raw.coerceAtLeast(0) else raw
                    adjustments = setParameter(adjustments, selectedParameter, value)
                    parameterLabel.text = getString(R.string.filter_parameter_value, getString(selectedParameter.label), value)
                    renderFilter(debounce = true)
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        root.addView(parameterBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 34.dp))
        syncParameter()

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 10.dp, 16.dp, 14.dp)
            setBackgroundColor(Color.BLACK)
        }
        val cancel = styledButton(getString(R.string.cancel), primary = false).apply { setOnClickListener { finish() } }
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
        val done = styledButton(getString(R.string.apply_crop), primary = true).apply { setOnClickListener { applyAndFinish() } }
        bottom.addView(cancel)
        bottom.addView(spacer)
        bottom.addView(done)
        root.addView(bottom)

        setContentView(root)
        window.insetsController?.setSystemBarsAppearance(
            0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )

        load()
    }

    private fun addButton(row: LinearLayout, label: String, action: () -> Unit) {
        val btn = styledButton(label, primary = false).apply { setOnClickListener { action() } }
        row.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun scrollRow(build: (LinearLayout) -> Unit): HorizontalScrollView = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@EditActivity).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(12.dp, 2.dp, 12.dp, 2.dp); build(this)
        })
    }

    private fun styledButton(label: String, primary: Boolean): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(if (primary) Color.WHITE else 0xFF0A84FF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        minHeight = 0
        minimumHeight = 0
        setPadding(14.dp, 7.dp, 14.dp, 7.dp)
        background = GradientDrawable().apply {
            cornerRadius = 10.dp.toFloat()
            setColor(if (primary) 0xFF0A84FF.toInt() else 0xFF2C2C2E.toInt())
        }
    }

    private fun load() {
        val uri = sourceUri ?: return finish()
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { BitmapLoader.load(this@EditActivity, uri) }
            if (bmp == null) {
                finish(); return@launch
            }
            source = bmp
            current = bmp
            renderFilter()
        }
    }

    private fun reset() {
        pushHistory()
        pendingRotation = 0
        flipH = false
        flipV = false
        adjustments = FilterAdjustments()
        val src = source ?: return
        current?.takeIf { it !== src }?.let(retiredBitmaps::add)
        current = src
        syncParameter()
        renderFilter()
    }

    private fun rotate(degrees: Float) {
        pendingRotation += degrees.toInt()
        applyTransform()
    }

    private fun applyTransform() {
        val src = source ?: return
        val matrix = Matrix()
        matrix.postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
        matrix.postRotate(pendingRotation.toFloat())
        val transformed = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        current?.takeIf { it !== src }?.let(retiredBitmaps::add)
        current = transformed
        renderFilter(resetCrop = true)
    }

    private fun pushHistory() {
        val state = EditorState(pendingRotation, flipH, flipV, adjustments)
        if (history.lastOrNull() != state) history.addLast(state)
        while (history.size > 30) history.removeFirst()
    }

    private fun undo() {
        val state = history.removeLastOrNull() ?: return
        pendingRotation = state.rotation; flipH = state.flipH; flipV = state.flipV; adjustments = state.filter
        syncParameter(); applyTransform()
    }

    private fun renderFilter(debounce: Boolean = false, resetCrop: Boolean = false) {
        val base = current ?: return
        val values = adjustments
        val version = ++renderVersion
        renderJob?.cancel()
        renderJob = scope.launch {
            if (debounce) delay(90)
            val rendered = withContext(Dispatchers.Default) { PhotoFilterRenderer.apply(base, values) }
            if (version != renderVersion) { rendered.recycle(); return@launch }
            preview?.takeIf { it !== base && it !== rendered }?.recycle()
            preview = rendered
            cropView.setImage(rendered, resetCrop)
        }
    }

    private fun syncParameter() {
        if (!::parameterBar.isInitialized) return
        val value = getParameter(adjustments, selectedParameter)
        parameterBar.progress = value + 100
        parameterLabel.text = getString(R.string.filter_parameter_value, getString(selectedParameter.label), value)
    }

    private fun getParameter(a: FilterAdjustments, p: Parameter) = when (p) {
        Parameter.Lightness -> a.lightness; Parameter.Brightness -> a.brightness; Parameter.Highlights -> a.highlights
        Parameter.Shadows -> a.shadows; Parameter.Contrast -> a.contrast; Parameter.Saturation -> a.saturation
        Parameter.Vibrance -> a.vibrance; Parameter.Warmth -> a.warmth; Parameter.Tint -> a.tint
        Parameter.Sharpness -> a.sharpness; Parameter.Clarity -> a.clarity; Parameter.Glow -> a.glow; Parameter.LoFi -> a.loFi
    }

    private fun setParameter(a: FilterAdjustments, p: Parameter, v: Int) = when (p) {
        Parameter.Lightness -> a.copy(lightness = v); Parameter.Brightness -> a.copy(brightness = v)
        Parameter.Highlights -> a.copy(highlights = v); Parameter.Shadows -> a.copy(shadows = v)
        Parameter.Contrast -> a.copy(contrast = v); Parameter.Saturation -> a.copy(saturation = v)
        Parameter.Vibrance -> a.copy(vibrance = v); Parameter.Warmth -> a.copy(warmth = v)
        Parameter.Tint -> a.copy(tint = v); Parameter.Sharpness -> a.copy(sharpness = v)
        Parameter.Clarity -> a.copy(clarity = v); Parameter.Glow -> a.copy(glow = v); Parameter.LoFi -> a.copy(loFi = v)
    }

    private fun applyAndFinish() {
        val base = current ?: return finish()
        val cropRect = cropView.currentCropRect() ?: return finish()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val bmp = PhotoFilterRenderer.apply(base, adjustments)
                val left = cropRect.left.coerceIn(0f, (bmp.width - 1).toFloat()).toInt()
                val top = cropRect.top.coerceIn(0f, (bmp.height - 1).toFloat()).toInt()
                val right = cropRect.right.coerceIn((left + 1).toFloat(), bmp.width.toFloat()).toInt()
                val bottom = cropRect.bottom.coerceIn((top + 1).toFloat(), bmp.height.toFloat()).toInt()
                val cropped = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
                val outFile = app.pinimage.util.PersistentImageStore.createFile(this@EditActivity, "edit")
                FileOutputStream(outFile).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (cropped != bmp) cropped.recycle()
                if (!bmp.isRecycled) bmp.recycle()
                Uri.fromFile(outFile).toString()
            }
            if (itemId != null) {
                app.pinimage.float.ViewRegistry.get(itemId!!)?.replaceImage(result)
            }
            val data = Intent().apply {
                putExtra(EXTRA_RESULT_URI, result)
                putExtra(EXTRA_ITEM_ID, itemId)
            }
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    override fun onDestroy() {
        renderJob?.cancel()
        preview?.takeIf { it !== current && it !== source }?.recycle()
        current?.takeIf { it !== source }?.recycle()
        retiredBitmaps.distinct().filterNot { it.isRecycled }.forEach { it.recycle() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_RESULT_URI = "extra_result_uri"
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
