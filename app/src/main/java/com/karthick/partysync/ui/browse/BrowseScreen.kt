@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.karthick.partysync.ui.browse

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.karthick.partysync.data.remote.RemoteEntry
import com.karthick.partysync.ui.common.FolderBrowserDialog
import com.karthick.partysync.ui.navigation.PartySyncBottomBar
import com.karthick.partysync.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNavigateTab: (Screen) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var fabMenuExpanded by remember { mutableStateOf(false) }
    var showServerSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }
    BackHandler(enabled = !uiState.isSelectionMode && uiState.currentPath.isNotEmpty()) {
        viewModel.navigateUp()
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "file"
            viewModel.uploadFile(uri, name)
        }
    }

    LaunchedEffect(uiState.fileToOpen) {
        val request = uiState.fileToOpen ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(request.uri, request.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: ActivityNotFoundException) {
            // surfaced via a separate error path would need another state field; for now this
            // is rare enough (no app on device handles the mime type) to just drop silently
            // rather than crash.
        }
        viewModel.onFileOpened()
    }

    LaunchedEffect(uiState.shareRequest) {
        val request = uiState.shareRequest ?: return@LaunchedEffect
        val intent = Intent(if (request.uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = request.mimeType
            if (request.uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, request.uris.single())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(request.uris))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Share"))
        } catch (e: ActivityNotFoundException) {
            // no app can handle it; drop silently, matching the open-with effect above
        }
        viewModel.onShareHandled()
    }

    Scaffold(
        topBar = { BrowseTopBar(uiState = uiState, viewModel = viewModel, onOpenServerSheet = { showServerSheet = true }) },
        bottomBar = {
            androidx.compose.foundation.layout.Column {
                if (uiState.clipboard != null) {
                    ClipboardBar(uiState = uiState, viewModel = viewModel)
                }
                PartySyncBottomBar(selected = Screen.Browse, onNavigate = onNavigateTab)
            }
        },
        floatingActionButton = {
            if (uiState.selectedServerId != null && !uiState.isSelectionMode) {
                Box {
                    FloatingActionButton(onClick = { fabMenuExpanded = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                    DropdownMenu(expanded = fabMenuExpanded, onDismissRequest = { fabMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Upload file here") },
                            leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                            onClick = {
                                fabMenuExpanded = false
                                uploadLauncher.launch(arrayOf("*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("New folder") },
                            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                fabMenuExpanded = false
                                viewModel.openNewFolderDialog()
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                uiState.servers.isEmpty() -> Text(
                    "No servers configured — add one in the app first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                uiState.selectedServerId == null -> Text(
                    "Choose a server to browse.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                else -> BrowseContent(uiState = uiState, viewModel = viewModel)
            }
        }
    }

    if (showServerSheet) {
        ServerPickerSheet(uiState = uiState, viewModel = viewModel, onDismiss = { showServerSheet = false })
    }

    if (uiState.showDeleteConfirm) {
        val count = uiState.selectedEntries.size
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text(if (count == 1) "Delete this item?" else "Delete $count items?") },
            text = {
                Text(
                    "This will permanently delete " +
                        (if (count == 1) "it" else "them") +
                        ", including the contents of any folders.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmDeleteSelection) { Text("Delete") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteConfirm) { Text("Cancel") } },
        )
    }

    uiState.renameTarget?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissRenameDialog,
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = uiState.renameText,
                    onValueChange = viewModel::onRenameTextChanged,
                    singleLine = true,
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRename) { Text("Rename") } },
            dismissButton = { TextButton(onClick = viewModel::dismissRenameDialog) { Text("Cancel") } },
        )
    }

    if (uiState.showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNewFolderDialog,
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = uiState.newFolderName,
                    onValueChange = viewModel::onNewFolderNameChanged,
                    placeholder = { Text("Folder name") },
                    singleLine = true,
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmNewFolder) { Text("Create") } },
            dismissButton = { TextButton(onClick = viewModel::dismissNewFolderDialog) { Text("Cancel") } },
        )
    }

    if (uiState.showZipNameDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissZipNameDialog,
            title = { Text("Zip as…") },
            text = {
                OutlinedTextField(
                    value = uiState.zipName,
                    onValueChange = viewModel::onZipNameChanged,
                    placeholder = { Text("Archive name") },
                    singleLine = true,
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmZip) { Text("Zip") } },
            dismissButton = { TextButton(onClick = viewModel::dismissZipNameDialog) { Text("Cancel") } },
        )
    }

    uiState.archiveProgressMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(message) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp).size(24.dp))
                    Text("This may take a moment for large files or folders.")
                }
            },
        )
    }

    if (uiState.moveTargetBrowser.isOpen) {
        FolderBrowserDialog(
            state = uiState.moveTargetBrowser,
            onNavigateInto = viewModel::navigateMoveTargetInto,
            onNavigateUp = viewModel::navigateMoveTargetUp,
            onSelect = viewModel::confirmMoveTarget,
            onDismiss = viewModel::closeMoveTargetPicker,
        )
    }

    uiState.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }

    uiState.mediaViewer?.let { mediaViewer ->
        MediaViewerDialog(
            state = mediaViewer,
            urlForEntry = { entry -> viewModel.mediaFileUrl(mediaViewer.serverUrl, mediaViewer.remoteBasePath, entry.name) },
            onDismiss = viewModel::closeMediaViewer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseTopBar(uiState: BrowseUiState, viewModel: BrowseViewModel, onOpenServerSheet: () -> Unit) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (uiState.isSelectionMode) {
                IconButton(onClick = viewModel::clearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                }
                Text(
                    "${uiState.selectedEntries.size}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                )
                val singleSelected = uiState.entries.find { it.name in uiState.selectedEntries }
                    .takeIf { uiState.selectedEntries.size == 1 }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (singleSelected != null) {
                        IconButton(onClick = { viewModel.requestRename(singleSelected) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Rename")
                        }
                        if (viewModel.canUnzip(singleSelected)) {
                            IconButton(onClick = { viewModel.requestUnzip(singleSelected) }) {
                                Icon(Icons.Filled.FolderZip, contentDescription = "Unzip")
                            }
                        }
                    }
                    IconButton(onClick = viewModel::requestZipSelection) {
                        Icon(Icons.Filled.Archive, contentDescription = "Zip")
                    }
                    IconButton(onClick = viewModel::cutSelection) {
                        Icon(Icons.Filled.ContentCut, contentDescription = "Cut")
                    }
                    IconButton(onClick = viewModel::copySelection) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = viewModel::openMoveTargetPicker) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to")
                    }
                    IconButton(onClick = viewModel::shareSelection) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = viewModel::requestDeleteSelection) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            } else {
                val title = uiState.breadcrumbSegments.lastOrNull() ?: "Browse"
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
                if (uiState.servers.size > 1) {
                    IconButton(onClick = onOpenServerSheet) {
                        Icon(Icons.Filled.Dns, contentDescription = "Switch server")
                    }
                }
                if (uiState.selectedServerId != null) {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            SortMenuItem("Name", BrowseSortField.NAME, uiState, viewModel) { sortMenuExpanded = false }
                            SortMenuItem("Date modified", BrowseSortField.DATE, uiState, viewModel) { sortMenuExpanded = false }
                            SortMenuItem("Size", BrowseSortField.SIZE, uiState, viewModel) { sortMenuExpanded = false }
                        }
                    }
                    IconButton(onClick = viewModel::toggleViewMode) {
                        Icon(
                            if (uiState.viewMode == BrowseViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.List,
                            contentDescription = "Toggle list/grid view",
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerPickerSheet(uiState: BrowseUiState, viewModel: BrowseViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Servers",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            uiState.servers.forEach { server ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.onServerSelected(server.id)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(server.displayName, modifier = Modifier.weight(1f))
                    if (server.id == uiState.selectedServerId) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseContent(uiState: BrowseUiState, viewModel: BrowseViewModel) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(uiState.sortField, uiState.sortAscending, uiState.currentPath) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "root",
                    modifier = Modifier.clickable { viewModel.navigateToBreadcrumb(-1) },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (uiState.currentPath.isEmpty()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                uiState.breadcrumbSegments.forEachIndexed { index, segment ->
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(horizontal = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val isLast = index == uiState.breadcrumbSegments.lastIndex
                    Text(
                        segment,
                        modifier = Modifier.clickable { viewModel.navigateToBreadcrumb(index) },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                uiState.error != null && uiState.entries.isEmpty() && !uiState.isLoading -> {
                    // error is already shown via the AlertDialog above; leave the list area blank
                }
                uiState.entries.isEmpty() && !uiState.isLoading -> androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "This folder is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                uiState.viewMode == BrowseViewMode.LIST -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(uiState.sortedEntries, key = { it.name }) { entry ->
                        EntryRow(
                            entry = entry,
                            thumbnailRequest = viewModel.thumbnailRequest(entry),
                            isSelected = entry.name in uiState.selectedEntries,
                            onClick = {
                                when {
                                    uiState.isSelectionMode -> viewModel.toggleSelection(entry)
                                    entry.isDirectory -> viewModel.navigateInto(entry)
                                    entry.isThumbnailable() -> viewModel.openMediaViewer(entry)
                                    else -> viewModel.downloadAndOpen(entry)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(entry) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(uiState.sortedEntries, key = { it.name }) { entry ->
                        GridEntryCell(
                            entry = entry,
                            thumbnailRequest = viewModel.thumbnailRequest(entry),
                            isSelected = entry.name in uiState.selectedEntries,
                            onClick = {
                                when {
                                    uiState.isSelectionMode -> viewModel.toggleSelection(entry)
                                    entry.isDirectory -> viewModel.navigateInto(entry)
                                    entry.isThumbnailable() -> viewModel.openMediaViewer(entry)
                                    else -> viewModel.downloadAndOpen(entry)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(entry) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardBar(uiState: BrowseUiState, viewModel: BrowseViewModel) {
    val clip = uiState.clipboard ?: return
    val verb = if (clip.operation == ClipboardOperation.CUT) "cut" else "copied"
    val count = clip.entryNames.size
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$count item${if (count == 1) "" else "s"} $verb",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::clearClipboard) { Text("Cancel") }
            TextButton(onClick = viewModel::pasteClipboard, enabled = viewModel.canPaste()) { Text("Paste") }
        }
    }
}

@Composable
private fun SortMenuItem(
    label: String,
    field: BrowseSortField,
    uiState: BrowseUiState,
    viewModel: BrowseViewModel,
    onSelected: () -> Unit,
) {
    val isActive = uiState.sortField == field
    DropdownMenuItem(
        text = { Text(if (isActive) "$label ${if (uiState.sortAscending) "↑" else "↓"}" else label) },
        leadingIcon = if (isActive) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        },
        onClick = {
            viewModel.setSortField(field)
            onSelected()
        },
    )
}

@Composable
private fun SelectionOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun EntryRow(
    entry: RemoteEntry,
    thumbnailRequest: Pair<String, String>?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .let { if (isSelected) it.background(MaterialTheme.colorScheme.primaryContainer) else it }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fallbackIcon = if (entry.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailRequest != null && (entry.isDirectory || entry.isThumbnailable())) {
                val (url, password) = thumbnailRequest
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .setHeader("PW", password)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (painter.state is AsyncImagePainter.State.Error) {
                        Icon(
                            fallbackIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            } else {
                Icon(
                    fallbackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
            if (isSelected) SelectionOverlay()
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
    }
}

@Composable
private fun GridEntryCell(
    entry: RemoteEntry,
    thumbnailRequest: Pair<String, String>?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val fallbackIcon = if (entry.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (thumbnailRequest != null && (entry.isDirectory || entry.isThumbnailable())) {
                val (url, password) = thumbnailRequest
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .setHeader("PW", password)
                        .crossfade(true)
                        .build(),
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (painter.state is AsyncImagePainter.State.Error) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                fallbackIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxSize(0.4f),
                            )
                        }
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        fallbackIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize(0.4f),
                    )
                }
            }
            if (isSelected) SelectionOverlay()
        }
        Text(
            entry.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
