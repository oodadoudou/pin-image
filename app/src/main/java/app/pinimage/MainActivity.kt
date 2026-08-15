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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.core.content.IntentCompat
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
    private var pendingReplaceItemId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FloatController.init(applicationContext)
        if (intent?.action == ACTION_PICK_REPLACE) {
            pendingReplaceItemId = intent.getStringExtra(EXTRA_TARGET_ITEM_ID)
        }
        handleShareIntent(intent)
        setContent {
            PinImageTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScaffold(
                        vm = vm,
                        pendingReplaceItemId = pendingReplaceItemId,
                        onReplaceConsumed = { pendingReplaceItemId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_PICK_REPLACE) {
            pendingReplaceItemId = intent.getStringExtra(EXTRA_TARGET_ITEM_ID)
        }
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (uri?.scheme == "content") {
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

const val ACTION_PICK_REPLACE = "app.pinimage.action.PICK_REPLACE"
const val EXTRA_TARGET_ITEM_ID = "extra_target_item_id"

private enum class Tab(@StringRes val labelRes: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Outlined.Home),
    Board(R.string.tab_board, Icons.Outlined.Dashboard),
    Settings(R.string.tab_settings, Icons.Outlined.Settings),
}

@Composable
private fun MainScaffold(
    vm: MainViewModel,
    pendingReplaceItemId: String?,
    onReplaceConsumed: () -> Unit,
) {
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
            if (pendingReplaceItemId != null) {
                app.pinimage.float.ViewRegistry.get(pendingReplaceItemId)?.replaceImage(uri.toString())
                onReplaceConsumed()
            } else {
                FloatController.pin(uri.toString())
            }
        } else {
            onReplaceConsumed()
        }
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            FloatController.pin(uri.toString())
        }
    }
    val pickEpub = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            FloatController.pin(uri.toString())
        }
    }
    var notificationGranted by remember { mutableStateOf(PermissionChecks.canPostNotifications(context)) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationGranted = granted
    }

    LaunchedEffect(pendingReplaceItemId) {
        if (pendingReplaceItemId != null) {
            pickToPin.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                ),
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = androidx.compose.ui.unit.Dp(0f),
            ) {
                tabs.forEachIndexed { index, item ->
                    val label = stringResource(item.labelRes)
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tabs[tab]) {
            Tab.Home -> HomeScreen(
                padding = padding,
                recent = recent,
                hasOverlayPermission = overlayGranted,
                hasAccessibility = a11yGranted,
                hasNotificationPermission = notificationGranted,
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
                onRequestNotifications = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onPickAndPin = {
                    pickToPin.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onPickPdf = { pickPdf.launch(arrayOf("application/pdf")) },
                onPickEpub = { pickEpub.launch(arrayOf("application/epub+zip")) },
                onStartFloatService = { FloatController.startControlPanel(context) },
                onPinRecent = { uri -> FloatController.pin(uri) },
                onDeleteRecents = { uris ->
                    vm.container.recent.replaceAll(recent.filterNot { it in uris })
                },
            )

            Tab.Board -> BoardListScreen(
                padding = padding,
                boards = boards,
                onCreate = {
                    context.startActivity(
                        Intent(context, app.pinimage.board.BoardActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onOpen = { b ->
                    context.startActivity(
                        Intent(context, app.pinimage.board.BoardActivity::class.java)
                            .putExtra(app.pinimage.board.BoardActivity.EXTRA_BOARD_ID, b.id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
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
