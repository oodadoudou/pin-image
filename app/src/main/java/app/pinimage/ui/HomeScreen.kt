package app.pinimage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pinimage.data.AppSettings

@Composable
fun HomeScreen(
    padding: PaddingValues,
    settings: AppSettings.Snapshot,
    hasOverlayPermission: Boolean,
    hasAccessibility: Boolean,
    onRequestOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onPickAndPin: () -> Unit,
    onStartFloatService: () -> Unit,
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
        Text(
            "Recent pins appear here once you start pinning.",
            style = MaterialTheme.typography.bodySmall,
        )
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
