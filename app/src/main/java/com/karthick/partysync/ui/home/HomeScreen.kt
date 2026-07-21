package com.karthick.partysync.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karthick.partysync.data.local.db.FolderMappingEntity
import com.karthick.partysync.domain.model.MappingSyncStatus
import com.karthick.partysync.domain.model.SyncMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddMapping: () -> Unit,
    onEditMapping: (Long) -> Unit,
    onViewMappingDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val mappings by viewModel.mappings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PartySync") },
                actions = {
                    IconButton(onClick = viewModel::syncNow) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "Sync now")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMapping) {
                Icon(Icons.Filled.Add, contentDescription = "Add folder")
            }
        },
    ) { innerPadding ->
        if (mappings.isEmpty()) {
            EmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(mappings, key = { it.id }) { mapping ->
                    MappingCard(
                        mapping = mapping,
                        onClick = { onEditMapping(mapping.id) },
                        onViewDetail = { onViewMappingDetail(mapping.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "No folders yet",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                "Tap + to choose a folder to sync to your copyparty server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun MappingCard(mapping: FolderMappingEntity, onClick: () -> Unit, onViewDetail: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mapping.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    mapping.remoteBasePath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildString {
                        append(if (mapping.syncMode == SyncMode.TWO_WAY) "⇅ Two-way" else "↑ Upload-only")
                        append(" · ")
                        append(statusLabel(mapping.lastSyncStatus))
                        if (!mapping.enabled) append(" · Paused")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onViewDetail) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "View sync log",
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

private fun statusLabel(status: MappingSyncStatus): String = when (status) {
    MappingSyncStatus.IDLE -> "Not synced yet"
    MappingSyncStatus.RUNNING -> "Syncing…"
    MappingSyncStatus.SUCCESS -> "Up to date"
    MappingSyncStatus.PARTIAL_FAILURE -> "Some files failed"
    MappingSyncStatus.AUTH_ERROR -> "Check server password"
    MappingSyncStatus.PERMISSION_LOST -> "Folder access lost"
    MappingSyncStatus.SERVER_MISSING -> "Server was deleted"
}
