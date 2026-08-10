package app.pinimage.board

import androidx.activity.ComponentActivity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import app.pinimage.float.FloatController
import app.pinimage.util.BitmapLoader
import app.pinimage.util.saveBitmapToGallery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BoardActivity : ComponentActivity() {

    private lateinit var boardView: BoardCanvas
    private val scope = CoroutineScope(Dispatchers.Main)
    private var board: Board = Board(id = UUID.randomUUID().toString(), name = "New Board", createdAt = System.currentTimeMillis())
    private var selectedId: String? = null

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { BitmapLoader.load(this@BoardActivity, uri.toString()) }
                if (bmp != null) {
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
        val boardId = intent.getStringExtra(EXTRA_BOARD_ID)
        if (boardId != null) {
            val restored = (application as app.pinimage.PinImageApp).container.boards.get(boardId)
            if (restored != null) board = restored
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        boardView = BoardCanvas(this).apply {
            setBoard(board)
            onSelect = { selected ->
                selectedId = selected?.id
                updateToolbarState()
            }
            onBoardChanged = { updated ->
                board = updated
            }
        }
        root.addView(
            boardView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val toolbar = HorizontalScrollView(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(8, 8, 8, 8) }
        toolbar.addView(row)
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addButton(row, "Add") {
            pickImages.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                ),
            )
        }
        addButton(row, "Flip H") { modifySelected { it.copy(flipH = !it.flipH) } }
        addButton(row, "Flip V") { modifySelected { it.copy(flipV = !it.flipV) } }
        addButton(row, "Rotate L") { modifySelected { it.copy(rotation = it.rotation - 90f) } }
        addButton(row, "Rotate R") { modifySelected { it.copy(rotation = it.rotation + 90f) } }
        addButton(row, "Crop") { /* basic editor per object is outside the MVP board flow; user can pre-crop in editor */ }
        addButton(row, "Duplicate") { duplicateSelected() }
        addButton(row, "Delete") { deleteSelected() }
        addButton(row, "Forward") { reorderSelected(+1) }
        addButton(row, "Backward") { reorderSelected(-1) }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(12, 8, 12, 16) }
        addButton(bottom, "White") { setBackground(BoardBackground.White) }
        addButton(bottom, "Black") { setBackground(BoardBackground.Black) }
        addButton(bottom, "Transparent") { setBackground(BoardBackground.Transparent) }
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
        bottom.addView(spacer)
        addButton(bottom, "Fit") { fitCanvasToContent() }
        addButton(bottom, "Save") { saveBoard() }
        addButton(bottom, "Pin") { pinBoard() }
        root.addView(bottom)

        setContentView(root)
    }

    override fun onPause() {
        super.onPause()
        (application as app.pinimage.PinImageApp).container.boards.upsert(board)
    }

    private fun addButton(row: LinearLayout, label: String, action: () -> Unit) {
        val btn = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        row.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun updateToolbarState() {}

    private fun modifySelected(transform: (BoardObject) -> BoardObject) {
        val id = selectedId ?: return
        board = board.copy(objects = board.objects.map { if (it.id == id) transform(it) else it })
        boardView.setBoard(board)
    }

    private fun duplicateSelected() {
        val obj = board.objects.firstOrNull { it.id == selectedId } ?: return
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
        board = board.copy(objects = board.objects.filterNot { it.id == id })
        selectedId = null
        boardView.setBoard(board)
    }

    private fun reorderSelected(delta: Int) {
        val list = board.objects.sortedBy { it.zIndex }.toMutableList()
        val idx = list.indexOfFirst { it.id == selectedId }
        if (idx < 0) return
        val swap = (idx + delta).coerceIn(0, list.size - 1)
        if (swap == idx) return
        val a = list[idx]
        val b = list[swap]
        list[idx] = b.copy(zIndex = a.zIndex)
        list[swap] = a.copy(zIndex = b.zIndex)
        board = board.copy(objects = list)
        boardView.setBoard(board)
    }

    private fun setBackground(bg: BoardBackground) {
        board = board.copy(background = bg)
        boardView.setBoard(board)
    }

    private fun fitCanvasToContent() {
        if (board.objects.isEmpty()) return
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
        board.objects.forEach {
            minX = minOf(minX, it.x)
            minY = minOf(minY, it.y)
            maxX = maxOf(maxX, it.x + it.width)
            maxY = maxOf(maxY, it.y + it.height)
        }
        val shiftX = -minX; val shiftY = -minY
        board = board.copy(
            canvasWidth = (maxX - minX).toInt().coerceAtLeast(1),
            canvasHeight = (maxY - minY).toInt().coerceAtLeast(1),
            objects = board.objects.map { it.copy(x = it.x + shiftX, y = it.y + shiftY) },
        )
        boardView.setBoard(board)
    }

    private fun setCanvasSize(width: Int, height: Int) {
        board = board.copy(canvasWidth = width, canvasHeight = height)
        boardView.setBoard(board)
    }

    private suspend fun renderBoard(): Bitmap {
        val bmp = Bitmap.createBitmap(board.canvasWidth, board.canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        when (board.background) {
            BoardBackground.White -> canvas.drawColor(Color.WHITE)
            BoardBackground.Black -> canvas.drawColor(Color.BLACK)
            BoardBackground.Transparent -> canvas.drawColor(Color.TRANSPARENT)
        }
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
            val file = File(cacheDir, "board_${System.currentTimeMillis()}.png")
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            FloatController.pin(Uri.fromFile(file).toString())
            finish()
        }
    }

    companion object {
        const val EXTRA_BOARD_ID = "extra_board_id"
    }
}
