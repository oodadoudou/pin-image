package app.pinimage.board

import androidx.activity.ComponentActivity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import android.view.WindowInsetsController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.pinimage.float.FloatController
import app.pinimage.util.BitmapLoader
import app.pinimage.util.saveBitmapToGallery
import app.pinimage.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

class BoardActivity : ComponentActivity() {

    private lateinit var boardView: BoardCanvas
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var board: Board = Board(id = UUID.randomUUID().toString(), name = "New Board", createdAt = System.currentTimeMillis())
    private var selectedId: String? = null
    private var originalBoard: Board? = null
    private var discardRequested = false
    private val undoHistory = ArrayDeque<Board>()
    private lateinit var undoButton: Button
    private lateinit var freeTransformButton: Button
    private var freeTransformEnabled = false

    private val editObject = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val id = data.getStringExtra(app.pinimage.edit.EditActivity.EXTRA_ITEM_ID) ?: return@registerForActivityResult
        val uri = data.getStringExtra(app.pinimage.edit.EditActivity.EXTRA_RESULT_URI) ?: return@registerForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { BitmapLoader.load(this@BoardActivity, uri) }
            if (bmp != null) recordUndoState()
            board = board.copy(objects = board.objects.map { obj ->
                if (obj.id != id || bmp == null || bmp.width == 0) obj else {
                    val centerY = obj.y + obj.height / 2f
                    val newHeight = obj.width * bmp.height.toFloat() / bmp.width
                    obj.copy(imageUri = uri, y = centerY - newHeight / 2f, height = newHeight)
                }
            })
            boardView.setBoard(board)
        }
    }

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { BitmapLoader.load(this@BoardActivity, uri.toString()) }
                if (bmp != null) {
                    recordUndoState()
                    val maxW = board.canvasWidth * 0.4f
                    val scale = if (bmp.width > maxW) maxW / bmp.width else 1f
                    val w = bmp.width * scale
                    val h = bmp.height * scale
                    val obj = BoardObject(
                        id = UUID.randomUUID().toString(),
                        imageUri = uri.toString(),
                        x = (board.canvasWidth - w) / 2f + (kotlin.random.Random.nextInt(-50, 50)),
                        y = (board.canvasHeight - h) / 2f + (kotlin.random.Random.nextInt(-50, 50)),
                        width = w,
                        height = h,
                        zIndex = board.objects.size,
                    )
                    board = board.copy(objects = board.objects + obj)
                    boardView.setBoard(board)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        val boardId = intent.getStringExtra(EXTRA_BOARD_ID)
        if (boardId != null) {
            val restored = (application as app.pinimage.PinImageApp).container.boards.get(boardId)
            if (restored != null) {
                board = restored
                originalBoard = restored
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF2F2F7.toInt())
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val primaryActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(10.dp, 8.dp, 10.dp, 8.dp)
            setBackgroundColor(Color.WHITE)
        }
        addButton(primaryActions, getString(R.string.add_images), filled = true) { launchImagePicker() }
        undoButton = addButton(primaryActions, getString(R.string.undo)) { undo() }
        primaryActions.addView(TextView(this).apply {
            text = if (board.name == "New Board") getString(R.string.new_board) else board.name
            setTextColor(0xFF1C1C1E.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addButton(primaryActions, getString(R.string.discard), destructive = true) { discardBoard() }
        addButton(primaryActions, getString(R.string.done), filled = true) { finishBoard() }
        root.addView(primaryActions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        boardView = BoardCanvas(this).apply {
            setBoard(board)
            onSelect = { selected ->
                selectedId = selected?.id
                updateToolbarState()
            }
            onBoardChanged = { updated ->
                board = updated
            }
            onTransformStarted = { recordUndoState() }
        }
        root.addView(
            boardView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val toolbar = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xFFF2F2F7.toInt())
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(8.dp, 7.dp, 8.dp, 7.dp) }
        toolbar.addView(row)
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addButton(row, getString(R.string.flip_horizontal)) { modifySelected { it.copy(flipH = !it.flipH) } }
        addButton(row, getString(R.string.flip_vertical)) { modifySelected { it.copy(flipV = !it.flipV) } }
        addButton(row, getString(R.string.rotate_left)) { modifySelected { it.copy(rotation = it.rotation - 90f) } }
        addButton(row, getString(R.string.rotate_right)) { modifySelected { it.copy(rotation = it.rotation + 90f) } }
        addButton(row, getString(R.string.crop)) { cropSelected() }
        addButton(row, getString(R.string.duplicate)) { duplicateSelected() }
        addButton(row, getString(R.string.delete), destructive = true) { deleteSelected() }
        addButton(row, getString(R.string.forward)) { reorderSelected(+1) }
        addButton(row, getString(R.string.backward)) { reorderSelected(-1) }
        freeTransformButton = addButton(row, getString(R.string.free_transform_off)) {
            freeTransformEnabled = !freeTransformEnabled
            boardView.setFreeTransformEnabled(freeTransformEnabled)
            freeTransformButton.text = getString(if (freeTransformEnabled) R.string.free_transform_on else R.string.free_transform_off)
        }
        updateToolbarState()

        val bottomScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.WHITE)
        }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(8.dp, 7.dp, 8.dp, 10.dp) }
        addButton(bottom, getString(R.string.background_white)) { setBackground(BoardBackground.White) }
        addButton(bottom, getString(R.string.background_black)) { setBackground(BoardBackground.Black) }
        addButton(bottom, getString(R.string.background_transparent)) { setBackground(BoardBackground.Transparent) }
        addButton(bottom, getString(R.string.fit_canvas)) { fitCanvasToContent() }
        addButton(bottom, getString(R.string.export_png)) { saveBoard() }
        addButton(bottom, getString(R.string.pin), filled = true) { pinBoard() }
        bottomScroller.addView(bottom)
        root.addView(bottomScroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    }

    override fun onPause() {
        super.onPause()
        if (!discardRequested) (application as app.pinimage.PinImageApp).container.boards.upsert(board)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun addButton(
        row: LinearLayout,
        label: String,
        filled: Boolean = false,
        destructive: Boolean = false,
        action: () -> Unit,
    ): Button {
        val btn = Button(this).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(
                when {
                    filled -> Color.WHITE
                    destructive -> 0xFFFF3B30.toInt()
                    else -> 0xFF007AFF.toInt()
                },
            )
            minHeight = 0
            minimumHeight = 0
            setPadding(13.dp, 8.dp, 13.dp, 8.dp)
            background = GradientDrawable().apply {
                cornerRadius = 12.dp.toFloat()
                setColor(if (filled) 0xFF007AFF.toInt() else Color.WHITE)
            }
            setOnClickListener { action() }
        }
        row.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 42.dp).apply {
            setMargins(3.dp, 0, 3.dp, 0)
        })
        return btn
    }

    private fun launchImagePicker() {
        pickImages.launch(
            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    private fun finishBoard() {
        (application as app.pinimage.PinImageApp).container.boards.upsert(board)
        finish()
    }

    private fun discardBoard() {
        discardRequested = true
        val repository = (application as app.pinimage.PinImageApp).container.boards
        originalBoard?.let(repository::upsert) ?: repository.delete(board.id)
        finish()
    }

    private fun updateToolbarState() {
        if (::undoButton.isInitialized) undoButton.isEnabled = undoHistory.isNotEmpty()
    }

    private fun recordUndoState() {
        if (undoHistory.lastOrNull() == board) return
        undoHistory.addLast(board)
        while (undoHistory.size > MAX_UNDO_STEPS) undoHistory.removeFirst()
        updateToolbarState()
    }

    private fun undo() {
        var previous: Board? = null
        while (undoHistory.isNotEmpty() && previous == null) {
            undoHistory.removeLast().takeIf { it != board }?.let { previous = it }
        }
        previous?.let {
            board = it
            if (selectedId != null && board.objects.none { obj -> obj.id == selectedId }) selectedId = null
            boardView.setBoard(board)
        }
        updateToolbarState()
    }

    private fun modifySelected(transform: (BoardObject) -> BoardObject) {
        val id = selectedId ?: return
        recordUndoState()
        board = board.copy(objects = board.objects.map { if (it.id == id) transform(it) else it })
        boardView.setBoard(board)
    }

    private fun duplicateSelected() {
        val obj = board.objects.firstOrNull { it.id == selectedId } ?: return
        recordUndoState()
        val copy = obj.copy(
            id = UUID.randomUUID().toString(),
            x = obj.x + 24f,
            y = obj.y + 24f,
            zIndex = board.objects.size,
        )
        board = board.copy(objects = board.objects + copy)
        boardView.setBoard(board)
    }

    private fun deleteSelected() {
        val id = selectedId ?: return
        recordUndoState()
        board = board.copy(objects = board.objects.filterNot { it.id == id })
        selectedId = null
        boardView.setBoard(board)
    }

    private fun cropSelected() {
        val obj = board.objects.firstOrNull { it.id == selectedId } ?: return
        editObject.launch(
            Intent(this, app.pinimage.edit.EditActivity::class.java)
                .putExtra(app.pinimage.edit.EditActivity.EXTRA_URI, obj.imageUri)
                .putExtra(app.pinimage.edit.EditActivity.EXTRA_ITEM_ID, obj.id),
        )
    }

    private fun reorderSelected(delta: Int) {
        val list = board.objects.sortedBy { it.zIndex }.toMutableList()
        val idx = list.indexOfFirst { it.id == selectedId }
        if (idx < 0) return
        val swap = (idx + delta).coerceIn(0, list.size - 1)
        if (swap == idx) return
        recordUndoState()
        val a = list[idx]
        val b = list[swap]
        list[idx] = b.copy(zIndex = a.zIndex)
        list[swap] = a.copy(zIndex = b.zIndex)
        board = board.copy(objects = list)
        boardView.setBoard(board)
    }

    private fun setBackground(bg: BoardBackground) {
        if (board.background == bg) return
        recordUndoState()
        board = board.copy(background = bg)
        boardView.setBoard(board)
    }

    private fun fitCanvasToContent() {
        if (board.objects.isEmpty()) return
        recordUndoState()
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        board.objects.forEach { obj ->
            val rect = RectF(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height)
            if (obj.rotation % 360f != 0f) {
                Matrix().apply {
                    postRotate(obj.rotation, rect.centerX(), rect.centerY())
                    mapRect(rect)
                }
            }
            minX = minOf(minX, rect.left)
            minY = minOf(minY, rect.top)
            maxX = maxOf(maxX, rect.right)
            maxY = maxOf(maxY, rect.bottom)
        }
        val shiftX = -minX; val shiftY = -minY
        board = board.copy(
            canvasWidth = ceil(maxX - minX).toInt().coerceAtLeast(1),
            canvasHeight = ceil(maxY - minY).toInt().coerceAtLeast(1),
            objects = board.objects.map { it.copy(x = it.x + shiftX, y = it.y + shiftY) },
        )
        boardView.setBoard(board)
    }

    private fun setCanvasSize(width: Int, height: Int) {
        board = board.copy(canvasWidth = width, canvasHeight = height)
        boardView.setBoard(board)
    }

    private suspend fun renderBoard(): Bitmap {
        // The editor intentionally allows objects outside the original canvas. Include the full
        // visible union when exporting or pinning so those objects are never silently cropped.
        val bounds = board.contentBounds()
        val renderScale = minOf(1f, MAX_RENDER_DIMENSION / max(bounds.width, bounds.height))
        val outputWidth = ceil(bounds.width * renderScale).toInt().coerceAtLeast(1)
        val outputHeight = ceil(bounds.height * renderScale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        when (board.background) {
            BoardBackground.White -> canvas.drawColor(Color.WHITE)
            BoardBackground.Black -> canvas.drawColor(Color.BLACK)
            BoardBackground.Transparent -> canvas.drawColor(Color.TRANSPARENT)
        }
        canvas.scale(renderScale, renderScale)
        canvas.translate(-bounds.left, -bounds.top)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        board.objects.sortedBy { it.zIndex }.forEach { obj ->
            val src = withContext(Dispatchers.IO) { BitmapLoader.load(this@BoardActivity, obj.imageUri) } ?: return@forEach
            val save = canvas.save()
            canvas.translate(obj.x + obj.width / 2f, obj.y + obj.height / 2f)
            canvas.rotate(obj.rotation)
            canvas.scale(if (obj.flipH) -1f else 1f, if (obj.flipV) -1f else 1f)
            val rect = RectF(-obj.width / 2f, -obj.height / 2f, obj.width / 2f, obj.height / 2f)
            canvas.drawBitmap(src, null, rect, paint)
            canvas.restoreToCount(save)
        }
        return bmp
    }

    private fun saveBoard() {
        scope.launch {
            val bmp = renderBoard()
            saveBitmapToGallery(this@BoardActivity, bmp, "board_${System.currentTimeMillis()}", Bitmap.CompressFormat.PNG)
        }
    }

    private fun pinBoard() {
        scope.launch {
            val bmp = renderBoard()
            val file = app.pinimage.util.PersistentImageStore.createFile(this@BoardActivity, "board")
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            FloatController.pin(Uri.fromFile(file).toString())
            finish()
        }
    }

    companion object {
        const val EXTRA_BOARD_ID = "extra_board_id"
        private const val MAX_UNDO_STEPS = 50
        private const val MAX_RENDER_DIMENSION = 4096f
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
