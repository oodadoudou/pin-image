package app.pinimage

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pinimage.a11y.ScreenshotAccessibilityService
import app.pinimage.data.AppContainer
import app.pinimage.data.AppSettings
import app.pinimage.float.FloatController
import app.pinimage.ui.BoardListScreen
import app.pinimage.ui.HomeScreen
import app.pinimage.ui.SettingsScreen
import app.pinimage.ui.theme.PinImageTheme
import app.pinimage.util.PermissionChecks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: android.app.Application) : AndroidViewModel(application) {
    val container: AppContainer = (application as PinImageApp).container
    val settings = container.settings.snapshot.stateIn(
        viewModelScope, SharingStarted.Eagerly, AppSettings.Snapshot(
            instantPin = true,
            rememberPosition = true,
            rememberSize = true,
            snapToEdge = true,
            autoSaveScreenshot = false,
            floatingButton = true,
            defaultOpacity = 1f,
            lastFrameWidth = 0,
            lastFrameHeight = 0,
        )
    )
    val boards = container.boards.boards.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recent = container.recent.items.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FloatController.init(applicationContext)
        handleShareIntent(intent)
        setContent {
            PinImageTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScaffold(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                }
                FloatController.pin(uri.toString())
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Board("Board", Icons.Outlined.Dashboard),
    Settings("Settings", Icons.Outlined.Settings),
}

@Composable
private fun MainScaffold(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val boards by vm.boards.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    val tabs = Tab.entries

    val overlayGranted = PermissionChecks.canDrawOverlays(context)
    val a11yGranted = PermissionChecks.isAccessibilityEnabled(context, ScreenshotAccessibilityService::class.java)

    val pickToPin = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            FloatController.pin(uri.toString())
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tabs[tab]) {
            Tab.Home -> HomeScreen(
                padding = padding,
                settings = settings,
                hasOverlayPermission = overlayGranted,
                hasAccessibility = a11yGranted,
                onRequestOverlay = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onOpenAccessibility = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onPickAndPin = {
                    pickToPin.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onStartFloatService = { FloatController.startControlPanel(context) },
            )

            Tab.Board -> BoardListScreen(
                padding = padding,
                boards = boards,
                onCreate = { /* board editor added in later commit */ },
                onOpen = { /* board editor added in later commit */ },
                onDelete = { b -> vm.launch { vm.container.boards.delete(b.id) } },
            )

            Tab.Settings -> SettingsScreen(
                padding = padding,
                settings = settings,
                onSetInstantPin = { v -> vm.launch { vm.container.settings.setInstantPin(v) } },
                onSetRememberPosition = { v -> vm.launch { vm.container.settings.setRememberPosition(v) } },
                onSetRememberSize = { v -> vm.launch { vm.container.settings.setRememberSize(v) } },
                onSetSnapToEdge = { v -> vm.launch { vm.container.settings.setSnapToEdge(v) } },
                onSetAutoSaveScreenshot = { v -> vm.launch { vm.container.settings.setAutoSaveScreenshot(v) } },
                onSetFloatingButton = { v -> vm.launch { vm.container.settings.setFloatingButton(v) } },
                onSetDefaultOpacity = { v -> vm.launch { vm.container.settings.setDefaultOpacity(v) } },
            )
        }
    }
}
