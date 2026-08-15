package app.pinimage.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pinimage.R
import app.pinimage.util.rememberBitmap
import app.pinimage.util.rememberPdfThumbnail
import app.pinimage.util.rememberEpubThumbnail

@Composable
fun HomeScreen(
    padding: PaddingValues,
    recent: List<String>,
    hasOverlayPermission: Boolean,
    hasAccessibility: Boolean,
    hasNotificationPermission: Boolean,
    onRequestOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    onPickAndPin: () -> Unit,
    onPickPdf: () -> Unit,
    onPickEpub: () -> Unit,
    onStartFloatService: () -> Unit,
    onPinRecent: (String) -> Unit,
    onDeleteRecents: (Set<String>) -> Unit,
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }

    LaunchedEffect(recent) {
        selected = selected.intersect(recent.toSet())
        if (selectionMode && selected.isEmpty()) selectionMode = false
    }

    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize().widthIn(max = 960.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionLabel(stringResource(R.string.quick_actions))
                    InsetCard {
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onStartFloatService,
                            enabled = hasOverlayPermission,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(13.dp),
                        ) { Text(stringResource(R.string.show_floating_control)) }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onPickAndPin,
                            enabled = hasOverlayPermission,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(13.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = hasOverlayPermission),
                        ) { Text(stringResource(R.string.pick_image_and_pin)) }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onPickPdf,
                            enabled = hasOverlayPermission,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(13.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = hasOverlayPermission),
                        ) { Text(stringResource(R.string.pick_pdf_and_pin)) }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onPickEpub,
                            enabled = hasOverlayPermission,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(13.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = hasOverlayPermission),
                        ) { Text(stringResource(R.string.pick_epub_and_pin)) }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionLabel(stringResource(R.string.permissions))
                    InsetCard {
                        PermissionRow(stringResource(R.string.permission_overlay), stringResource(R.string.permission_overlay_detail), hasOverlayPermission, stringResource(R.string.grant), onRequestOverlay)
                        InsetDivider()
                        PermissionRow(stringResource(R.string.permission_accessibility), stringResource(R.string.permission_accessibility_detail), hasAccessibility, stringResource(R.string.enable), onOpenAccessibility)
                        InsetDivider()
                        PermissionRow(stringResource(R.string.permission_notifications), stringResource(R.string.permission_notifications_detail), hasNotificationPermission, stringResource(R.string.grant), onRequestNotifications)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selectionMode) stringResource(R.string.selected_count, selected.size) else stringResource(R.string.recent_pins),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    if (selectionMode) {
                        TextButton(enabled = selected.isNotEmpty(), onClick = { pendingDelete = selected }) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { selectionMode = false; selected = emptySet() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
            if (recent.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    InsetCard {
                        Text(
                            stringResource(R.string.recent_empty),
                            modifier = Modifier.padding(vertical = 20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(recent, key = { it }) { uri ->
                    RecentThumb(
                        uri = uri,
                        selected = uri in selected,
                        selectionMode = selectionMode,
                        onClick = { if (selectionMode) selected = if (uri in selected) selected - uri else selected + uri },
                        onDoubleClick = {
                            if (selectionMode) selected = if (uri in selected) selected - uri else selected + uri else onPinRecent(uri)
                        },
                        onDeleteThis = { pendingDelete = setOf(uri) },
                        onSelectMultiple = { selectionMode = true; selected = selected + uri },
                    )
                }
            }
        }
    }

    pendingDelete?.let { targets ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    if (targets.size == 1) stringResource(R.string.delete_recent_one_title)
                    else stringResource(R.string.delete_recent_many_title, targets.size),
                )
            },
            text = { Text(stringResource(R.string.delete_recent_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecents(targets)
                    selected = selected - targets
                    selectionMode = false
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentThumb(
    uri: String,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onDeleteThis: () -> Unit,
    onSelectMultiple: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val metadata = remember { app.pinimage.data.LibraryMetadataStore(context) }
    val mediaKind = remember(uri) {
        metadata.mediaKind(uri) ?: when {
            app.pinimage.util.PersistentImageStore.isPdf(context, android.net.Uri.parse(uri)) -> app.pinimage.float.MediaKind.Pdf
            app.pinimage.util.PersistentImageStore.isEpub(context, android.net.Uri.parse(uri)) -> app.pinimage.float.MediaKind.Epub
            else -> app.pinimage.float.MediaKind.Image
        }
    }
    val isPdf = mediaKind == app.pinimage.float.MediaKind.Pdf
    val isEpub = mediaKind == app.pinimage.float.MediaKind.Epub
    var displayName by remember(uri) { mutableStateOf(metadata.displayName(uri)) }
    val bmp = when (mediaKind) {
        app.pinimage.float.MediaKind.Pdf -> rememberPdfThumbnail(uri)
        app.pinimage.float.MediaKind.Epub -> rememberEpubThumbnail(uri)
        app.pinimage.float.MediaKind.Image -> rememberBitmap(uri)
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameValue by remember(uri) { mutableStateOf(displayName) }
    val shape = RoundedCornerShape(14.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                .combinedClickable(
                    onClick = onClick,
                    onDoubleClick = onDoubleClick,
                    onLongClick = { if (selectionMode) onClick() else menuExpanded = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = displayName,
                    contentScale = if (isPdf || isEpub) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isPdf || isEpub) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) { Text(if (isPdf) "PDF" else "EPUB", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }
            } else {
                Text(when { isPdf -> "PDF"; isEpub -> "EPUB"; else -> "…" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) { Text("✓", color = Color.White, style = MaterialTheme.typography.labelLarge) }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = {
                        menuExpanded = false
                        renameValue = displayName
                        renameDialog = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_this_pin), color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDeleteThis() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select_multiple)) },
                    onClick = { menuExpanded = false; onSelectMultiple() },
                )
            }
        }
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            shape = RoundedCornerShape(22.dp),
            title = { Text(stringResource(R.string.rename_file)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.file_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameValue.isNotBlank(),
                    onClick = {
                        metadata.rename(uri, renameValue)
                        displayName = renameValue.trim()
                        renameDialog = false
                    },
                ) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Text(stringResource(R.string.ready), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
