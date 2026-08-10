package app.pinimage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pinimage.board.Board

@Composable
fun BoardListScreen(
    padding: PaddingValues,
    boards: List<Board>,
    onCreate: () -> Unit,
    onOpen: (Board) -> Unit,
    onDelete: (Board) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Boards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onCreate) { Text("New") }
        }
        if (boards.isEmpty()) {
            Text(
                "No boards yet. Create one to drop multiple reference images onto a canvas.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(boards, key = { it.id }) { board ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(board.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${board.objects.size} images · ${board.canvasWidth}×${board.canvasHeight}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { onOpen(board) }) { Text("Open") }
                            TextButton(onClick = { onDelete(board) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}
