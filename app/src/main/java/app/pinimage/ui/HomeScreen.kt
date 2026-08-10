package app.pinimage.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pinimage.data.AppSettings
import app.pinimage.util.rememberBitmap

@Composable
fun HomeScreen(
    padding: PaddingValues,
    settings: AppSettings.Snapshot,
    recent: List<String>,
    hasOverlayPermission: Boolean,
    hasAccessibility: Boolean,
    onRequestOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onPickAndPin: () -> Unit,
    onStartFloatService: () -> Unit,
    onPinRecent: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Quick Screenshot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Enable Accessibility and the floating button to capture with one tap. Or import an image from the gallery below.",
            style = MaterialTheme.typography.bodyMedium,
        )

        PermissionRow(
            label = "Display over other apps",
            granted = hasOverlayPermission,
            actionLabel = if (hasOverlayPermission) "Granted" else "Grant",
            onAction = onRequestOverlay,
        )
        PermissionRow(
            label = "Accessibility service (screenshot)",
            granted = hasAccessibility,
            actionLabel = if (hasAccessibility) "Granted" else "Enable",
            onAction = onOpenAccessibility,
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onStartFloatService,
            enabled = hasOverlayPermission,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Show floating control")
        }

        OutlinedButton(
            onClick = onPickAndPin,
            enabled = hasOverlayPermission,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pick image and pin")
        }

        Spacer(Modifier.height(8.dp))
        Text("Recent Pins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (recent.isEmpty()) {
            Text(
                "Pinned screenshots and picked images will show up here. Tap one to pin it again.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(recent) { uri ->
                    RecentThumb(uri = uri, onClick = { onPinRecent(uri) })
                }
            }
        }
    }
}

@Composable
private fun RecentThumb(uri: String, onClick: () -> Unit) {
    val bmp = rememberBitmap(uri)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF2F2F2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("...", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (granted) "Ready" else "Required",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (!granted) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
