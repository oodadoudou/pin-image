package app.pinimage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pinimage.data.AppSettings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    settings: AppSettings.Snapshot,
    onSetInstantPin: (Boolean) -> Unit,
    onSetRememberPosition: (Boolean) -> Unit,
    onSetRememberSize: (Boolean) -> Unit,
    onSetSnapToEdge: (Boolean) -> Unit,
    onSetAutoSaveScreenshot: (Boolean) -> Unit,
    onSetFloatingButton: (Boolean) -> Unit,
    onSetDefaultOpacity: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        SwitchRow("Instant Pin", "Skip the confirmation bar after screenshot", settings.instantPin, onSetInstantPin)
        SwitchRow("Floating button", "Show the accessibility floating button", settings.floatingButton, onSetFloatingButton)
        SwitchRow("Remember position", "Restore pinned window positions", settings.rememberPosition, onSetRememberPosition)
        SwitchRow("Remember size", "Reuse last frame size for new pins", settings.rememberSize, onSetRememberSize)
        SwitchRow("Snap to edge", "Light snapping when moving a window near an edge", settings.snapToEdge, onSetSnapToEdge)
        SwitchRow("Auto save screenshots", "Save each screenshot to gallery automatically", settings.autoSaveScreenshot, onSetAutoSaveScreenshot)

        Text(
            "Default opacity: ${(settings.defaultOpacity * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = settings.defaultOpacity,
            onValueChange = onSetDefaultOpacity,
            valueRange = 0.2f..1f,
            steps = 15,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
