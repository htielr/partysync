package com.karthick.partysync.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karthick.partysync.ui.common.FolderBrowserDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareUploadScreen(
    onCancel: () -> Unit,
    onDone: () -> Unit,
    viewModel: ShareUploadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onDone()
    }

    if (uiState.folderBrowser.isOpen) {
        FolderBrowserDialog(
            state = uiState.folderBrowser,
            onNavigateInto = viewModel::navigateFolderBrowserInto,
            onNavigateUp = viewModel::navigateFolderBrowserUp,
            onSelect = viewModel::selectCurrentFolderBrowserPath,
            onDismiss = viewModel::closeFolderBrowser,
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Share to copyparty") }) },
        bottomBar = {
            // Pinned outside the scrollable content and padded above the IME so it's never
            // pushed off-screen or hidden behind the keyboard while the path field is focused.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = viewModel::upload,
                    enabled = uiState.canUpload,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (uiState.isUploading) "Starting…" else "Upload")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Files", style = MaterialTheme.typography.titleMedium)
            uiState.files.forEach { file ->
                Text(
                    "${file.displayName}${if (file.size >= 0) " (${formatSize(file.size)})" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text("Server", style = MaterialTheme.typography.titleMedium)
            if (uiState.servers.isEmpty()) {
                Text(
                    "No servers configured — add one in the app first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                uiState.servers.forEach { server ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                            .selectable(
                                selected = uiState.selectedServerId == server.id,
                                onClick = { viewModel.onServerSelected(server.id) },
                                role = Role.RadioButton,
                            ),
                    ) {
                        RadioButton(
                            selected = uiState.selectedServerId == server.id,
                            onClick = { viewModel.onServerSelected(server.id) },
                        )
                        Text(server.displayName, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            OutlinedTextField(
                value = uiState.remotePath,
                onValueChange = viewModel::onRemotePathChanged,
                label = { Text("Remote path on server") },
                placeholder = { Text("/backups/phone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = viewModel::openFolderBrowser,
                enabled = uiState.selectedServerId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Text("Browse server folders…", modifier = Modifier.padding(start = 8.dp))
            }

            if (uiState.pathSuggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.pathSuggestions) { suggestion ->
                        FilterChip(
                            selected = uiState.remotePath == suggestion,
                            onClick = { viewModel.onRemotePathChanged(suggestion) },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}
