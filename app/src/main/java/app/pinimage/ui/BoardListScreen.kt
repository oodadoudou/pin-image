package app.pinimage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pinimage.R
import app.pinimage.board.Board
import app.pinimage.board.BoardBackground
import app.pinimage.board.contentBounds
import app.pinimage.util.rememberBitmap

@Composable
fun BoardListScreen(
    padding: PaddingValues,
    boards: List<Board>,
    onCreate: () -> Unit,
    onOpen: (Board) -> Unit,
    onDelete: (Board) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Board?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.boards_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.weight(1f))
            Button(onClick = onCreate, shape = RoundedCornerShape(13.dp), modifier = Modifier.height(44.dp)) {
                Text("＋  ${stringResource(R.string.new_board)}")
            }
        }
        if (boards.isEmpty()) {
            InsetCard {
                Text(stringResource(R.string.boards_empty_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
                Text(
                    stringResource(R.string.boards_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(boards, key = { it.id }) { board ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(board) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column {
                            BoardPreview(board, Modifier.aspectRatio(1.15f))
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (board.name == "New Board") stringResource(R.string.new_board) else board.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        pluralStringResource(R.plurals.board_image_count, board.objects.size, board.objects.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { pendingDelete = board }) {
                                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { board ->
        val displayName = if (board.name == "New Board") stringResource(R.string.new_board) else board.name
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(22.dp),
            title = { Text(stringResource(R.string.delete_board_title, displayName)) },
            text = { Text(stringResource(R.string.delete_board_message)) },
            confirmButton = {
                TextButton(onClick = { onDelete(board); pendingDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun BoardPreview(board: Board, modifier: Modifier = Modifier) {
    val previewObjects = board.objects.sortedBy { it.zIndex }.takeLast(12)
    val images = previewObjects.associate { it.id to rememberBitmap(it.imageUri)?.asImageBitmap() }
    Canvas(modifier = modifier.fillMaxWidth()) {
        val bounds = board.contentBounds()
        val scale = minOf(size.width / bounds.width, size.height / bounds.height)
        val originX = (size.width - bounds.width * scale) / 2f - bounds.left * scale
        val originY = (size.height - bounds.height * scale) / 2f - bounds.top * scale
        when (board.background) {
            BoardBackground.White -> drawRect(Color.White)
            BoardBackground.Black -> drawRect(Color.Black)
            BoardBackground.Transparent -> {
                drawRect(Color(0xFFE7E7E7))
                val cell = 12.dp.toPx()
                var y = 0f
                var row = 0
                while (y < size.height) {
                    var x = 0f
                    var col = 0
                    while (x < size.width) {
                        if ((row + col) % 2 == 0) {
                            drawRect(
                                Color.White,
                                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(cell, cell),
                            )
                        }
                        x += cell
                        col++
                    }
                    y += cell
                    row++
                }
            }
        }
        previewObjects.forEach { obj ->
            val image = images[obj.id] ?: return@forEach
            val w = (obj.width * scale).toInt().coerceAtLeast(1)
            val h = (obj.height * scale).toInt().coerceAtLeast(1)
            val centerX = originX + (obj.x + obj.width / 2f) * scale
            val centerY = originY + (obj.y + obj.height / 2f) * scale
            withTransform({
                translate(centerX, centerY)
                rotate(obj.rotation)
                scale(if (obj.flipH) -1f else 1f, if (obj.flipV) -1f else 1f)
            }) {
                drawImage(image = image, dstOffset = IntOffset(-w / 2, -h / 2), dstSize = IntSize(w, h))
            }
        }
    }
}
