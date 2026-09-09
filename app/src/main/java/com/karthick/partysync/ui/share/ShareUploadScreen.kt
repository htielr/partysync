package com.karthick.partysync.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karthick.partysync.data.local.prefs.ServerProfile
import com.karthick.partysync.domain.model.CHAT_ROOM_GENERAL
import com.karthick.partysync.domain.model.CHAT_ROOM_VAULT
import com.karthick.partysync.ui.common.FolderBrowserDialog
import com.karthick.partysync.ui.common.formatSize

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

    val isChatRelay = uiState.destination == ShareDestination.CHAT_RELAY

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isChatRelay) "Send to Chat Relay" else "Upload to Copyparty",
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = viewModel::upload,
                    enabled = uiState.canUpload,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(if (isChatRelay) Icons.Filled.Forum else Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    val label = if (isChatRelay) "Send" else "Start Upload"
                    val busyLabel = if (isChatRelay) "Sending…" else "Uploading…"
                    Text(if (uiState.isUploading) busyLabel else label, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val sharedText = uiState.sharedText

            SectionCard {
                if (sharedText != null) {
                    Text(
                        "Shared Link",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(sharedText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Text(
                        "Shared Files (${uiState.files.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    uiState.files.forEach { file ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        file.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (file.size >= 0) {
                                        Text(
                                            formatSize(file.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bare text has nowhere to go in a sync folder - only Chat Relay's Vault room
            // accepts it, so the destination toggle only makes sense for a shared file.
            if (sharedText == null) {
                SectionCard {
                    Text(
                        "Send To",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.destination == ShareDestination.SYNC_FOLDER,
                            onClick = { viewModel.onDestinationSelected(ShareDestination.SYNC_FOLDER) },
                            leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Sync Folder") },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                        FilterChip(
                            selected = isChatRelay,
                            onClick = { viewModel.onDestinationSelected(ShareDestination.CHAT_RELAY) },
                            leadingIcon = { Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Chat Relay") },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }

            if (isChatRelay) {
                ChatRelayDestinationFields(
                    configured = uiState.chatRelayConfigured,
                    selectedRoom = uiState.chatRoom,
                    generalEnabled = sharedText == null,
                    onRoomSelected = viewModel::onChatRoomSelected,
                )
            } else {
                SyncFolderDestinationFields(
                    servers = uiState.servers,
                    selectedServerId = uiState.selectedServerId,
                    remotePath = uiState.remotePath,
                    pathSuggestions = uiState.pathSuggestions,
                    onServerSelected = viewModel::onServerSelected,
                    onRemotePathChanged = viewModel::onRemotePathChanged,
                    onOpenFolderBrowser = viewModel::openFolderBrowser,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScopeContent) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

private typealias ColumnScopeContent = androidx.compose.foundation.layout.ColumnScope.() -> Unit

@Composable
private fun ChatRelayDestinationFields(
    configured: Boolean,
    selectedRoom: String,
    generalEnabled: Boolean,
    onRoomSelected: (String) -> Unit,
) {
    SectionCard {
        Text(
            "Room",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (!configured) {
            Text(
                "Chat Relay isn't configured — open the Chat tab to add your server URL and API key first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return@SectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedCard(
                onClick = { onRoomSelected(CHAT_ROOM_VAULT) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (selectedRoom == CHAT_ROOM_VAULT) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(
                        if (selectedRoom == CHAT_ROOM_VAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Vault", fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedCard(
                onClick = { if (generalEnabled) onRoomSelected(CHAT_ROOM_GENERAL) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (selectedRoom == CHAT_ROOM_GENERAL) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(
                        if (selectedRoom == CHAT_ROOM_GENERAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Public,
                        contentDescription = null,
                        tint = if (generalEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "General",
                        fontWeight = FontWeight.SemiBold,
                        color = if (generalEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncFolderDestinationFields(
    servers: List<ServerProfile>,
    selectedServerId: Long?,
    remotePath: String,
    pathSuggestions: List<String>,
    onServerSelected: (Long) -> Unit,
    onRemotePathChanged: (String) -> Unit,
    onOpenFolderBrowser: () -> Unit,
) {
    SectionCard {
        Text(
            "Target Server",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        if (servers.isEmpty()) {
            Text(
                "No servers configured — add one in the main app first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { server ->
                    val isSelected = selectedServerId == server.id
                    OutlinedCard(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = SolidColor(
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onServerSelected(server.id) },
                                role = Role.RadioButton,
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = { onServerSelected(server.id) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.Dns,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(server.displayName, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }

    SectionCard {
        Text(
            "Remote Destination Path",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        OutlinedTextField(
            value = remotePath,
            onValueChange = onRemotePathChanged,
            label = { Text("Remote path on server") },
            placeholder = { Text("/uploads") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onOpenFolderBrowser,
            enabled = selectedServerId != null,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Browse server folders…")
        }

        if (pathSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Recent Paths",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pathSuggestions) { suggestion ->
                    FilterChip(
                        selected = remotePath == suggestion,
                        onClick = { onRemotePathChanged(suggestion) },
                        label = { Text(suggestion) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        }
    }
}
