package app.pinimage.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pinimage.R
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
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 28.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.settings_capture))
        InsetCard {
            SwitchRow(R.string.instant_pin, R.string.instant_pin_detail, settings.instantPin, onSetInstantPin)
            InsetDivider()
            SwitchRow(R.string.floating_button, R.string.floating_button_detail, settings.floatingButton, onSetFloatingButton)
            InsetDivider()
            SwitchRow(R.string.auto_save, R.string.auto_save_detail, settings.autoSaveScreenshot, onSetAutoSaveScreenshot)
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(stringResource(R.string.settings_floating))
        InsetCard {
            SwitchRow(R.string.remember_position, R.string.remember_position_detail, settings.rememberPosition, onSetRememberPosition)
            InsetDivider()
            SwitchRow(R.string.remember_size, R.string.remember_size_detail, settings.rememberSize, onSetRememberSize)
            InsetDivider()
            SwitchRow(R.string.snap_to_edge, R.string.snap_to_edge_detail, settings.snapToEdge, onSetSnapToEdge)
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(stringResource(R.string.settings_appearance))
        InsetCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.default_opacity), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.percent_value, (settings.defaultOpacity * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = settings.defaultOpacity,
                onValueChange = onSetDefaultOpacity,
                valueRange = 0.2f..1f,
                steps = 15,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
    }
}

@Composable
private fun SwitchRow(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
