package com.karthick.partysync.ui.browse

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.karthick.partysync.data.remote.RemoteEntry
import com.karthick.partysync.ui.common.formatSize
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
    var serverMenuExpanded by remember { mutableStateOf(false) }
    var fabMenuExpanded by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val currentName = uiState.servers.find { it.id == uiState.selectedServerId }?.displayName
                        ?: "Browse"
                    if (uiState.servers.size > 1) {
                        Box {
                            Row(
                                modifier = Modifier.clickable { serverMenuExpanded = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(currentName)
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Switch server")
                            }
                            DropdownMenu(expanded = serverMenuExpanded, onDismissRequest = { serverMenuExpanded = false }) {
                                uiState.servers.forEach { server ->
                                    DropdownMenuItem(
                                        text = { Text(server.displayName) },
                                        onClick = {
                                            serverMenuExpanded = false
                                            viewModel.onServerSelected(server.id)
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        Text(currentName)
                    }
                },
            )
        },
        bottomBar = { PartySyncBottomBar(selected = Screen.Browse, onNavigate = onNavigateTab) },
        floatingActionButton = {
            if (uiState.selectedServerId != null) {
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

    uiState.pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text("Delete ${entry.name}?") },
            text = {
                Text(
                    if (entry.isDirectory) {
                        "This will permanently delete this folder and everything inside it."
                    } else {
                        "This will permanently delete this file."
                    },
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Delete") } },
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

    uiState.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseContent(uiState: BrowseUiState, viewModel: BrowseViewModel) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "root",
                modifier = Modifier.clickable { viewModel.navigateToBreadcrumb(-1) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.currentPath.isEmpty()) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            uiState.breadcrumbSegments.forEachIndexed { index, segment ->
                Text(" / ", style = MaterialTheme.typography.bodyMedium)
                val isLast = index == uiState.breadcrumbSegments.lastIndex
                Text(
                    segment,
                    modifier = Modifier.clickable { viewModel.navigateToBreadcrumb(index) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
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
                uiState.entries.isEmpty() && !uiState.isLoading -> Text(
                    "This folder is empty",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.entries, key = { it.name }) { entry ->
                        EntryRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory) viewModel.navigateInto(entry) else viewModel.downloadAndOpen(entry)
                            },
                            onRename = { viewModel.requestRename(entry) },
                            onDelete = { viewModel.requestDelete(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: RemoteEntry, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (entry.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
        )
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        ) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
            if (!entry.isDirectory) {
                Text(
                    formatSize(entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}
