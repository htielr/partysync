package com.karthick.partysync.ui.mappingdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karthick.partysync.data.local.db.SyncFileStateEntity
import com.karthick.partysync.domain.model.FileSyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingDetailScreen(
    onBack: () -> Unit,
    viewModel: MappingDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.mapping?.displayName ?: "Sync log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.files.isEmpty()) {
            Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                Text("No files tracked yet — run a sync first.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                items(uiState.files, key = { it.relativePath }) { file ->
                    FileStateRow(file)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FileStateRow(file: SyncFileStateEntity) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(file.relativePath, style = MaterialTheme.typography.bodyLarge)
        Text(
            statusLabel(file.lastUploadStatus) + (file.lastErrorMessage?.let { " — $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = statusColor(file.lastUploadStatus),
        )
    }
}

private fun statusLabel(status: FileSyncStatus): String = when (status) {
    FileSyncStatus.PENDING -> "Pending"
    FileSyncStatus.UPLOADING -> "Uploading…"
    FileSyncStatus.DOWNLOADING -> "Downloading…"
    FileSyncStatus.SUCCESS -> "Synced"
    FileSyncStatus.CONFLICT_RESOLVED -> "Conflict (kept both copies)"
    FileSyncStatus.FAILED_RETRYABLE -> "Failed — will retry"
    FileSyncStatus.FAILED_AUTH -> "Failed — check password"
    FileSyncStatus.FAILED_OTHER -> "Failed"
}

@Composable
private fun statusColor(status: FileSyncStatus) = when (status) {
    FileSyncStatus.SUCCESS -> MaterialTheme.colorScheme.onSurfaceVariant
    FileSyncStatus.CONFLICT_RESOLVED -> MaterialTheme.colorScheme.tertiary
    FileSyncStatus.FAILED_RETRYABLE, FileSyncStatus.FAILED_AUTH, FileSyncStatus.FAILED_OTHER ->
        MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
