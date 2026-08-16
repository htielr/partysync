package com.karthick.partysync.ui.addmapping

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.karthick.partysync.domain.model.SyncMode
import com.karthick.partysync.ui.common.FolderBrowserDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMappingScreen(
    onDone: () -> Unit,
    onAddServer: () -> Unit,
    viewModel: AddEditMappingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
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

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val docFile = DocumentFile.fromTreeUri(context, uri)
            viewModel.onFolderPicked(uri, docFile)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isEditing) "Edit Folder Mapping" else "Add Folder Mapping",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete mapping",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Target Server Card
            FormSectionCard(
                title = "Target Server",
                icon = Icons.Filled.Dns,
            ) {
                if (servers.isEmpty()) {
                    Text(
                        "No servers configured yet. Add a server to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onAddServer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Server Profile")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        servers.forEach { server ->
                            SelectableOptionCard(
                                label = server.displayName,
                                subtitle = server.serverUrl,
                                selected = uiState.selectedServerId == server.id,
                                onSelect = { viewModel.onServerSelected(server.id) },
                            )
                        }
                    }
                }
            }

            // Local & Remote Folder Paths Card
            FormSectionCard(
                title = "Folder Configuration",
                icon = Icons.Filled.FolderOpen,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Phone Folder",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.folderDisplayName.ifBlank { "Select folder on this phone" },
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "Remote Destination Path",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    OutlinedTextField(
                        value = uiState.remoteBasePath,
                        onValueChange = viewModel::onRemoteBasePathChanged,
                        label = { Text("Remote path on server") },
                        placeholder = { Text("/backups/phone") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Filled.PhonelinkSetup, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedButton(
                        onClick = viewModel::openFolderBrowser,
                        enabled = uiState.selectedServerId != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse server folders…")
                    }
                }
            }

            // Sync Mode Card
            FormSectionCard(
                title = "Sync Behavior",
                icon = Icons.Filled.Sync,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectableOptionCard(
                        label = "One-Way Upload",
                        subtitle = "Push changes from your phone to the server.",
                        icon = Icons.Filled.CloudUpload,
                        selected = uiState.syncMode == SyncMode.ONE_WAY_UPLOAD,
                        onSelect = { viewModel.onSyncModeChanged(SyncMode.ONE_WAY_UPLOAD) },
                    )

                    SelectableOptionCard(
                        label = "Two-Way Sync",
                        subtitle = "Keep both phone and server updated bi-directionally.",
                        icon = Icons.Filled.Sync,
                        selected = uiState.syncMode == SyncMode.TWO_WAY,
                        onSelect = { viewModel.onSyncModeChanged(SyncMode.TWO_WAY) },
                    )
                }
            }

            // Network Rules Card
            FormSectionCard(
                title = "Network & Enablement",
                icon = Icons.Filled.Wifi,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Wi-Fi Preferences",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    SelectableOptionCard(
                        label = "Inherit App Setting",
                        subtitle = "Follow global Wi-Fi rule in settings",
                        selected = uiState.wifiOverride == WifiOverrideChoice.INHERIT,
                        onSelect = { viewModel.onWifiOverrideChanged(WifiOverrideChoice.INHERIT) },
                    )

                    SelectableOptionCard(
                        label = "Wi-Fi Only",
                        subtitle = "Never sync on cellular data",
                        selected = uiState.wifiOverride == WifiOverrideChoice.WIFI_ONLY,
                        onSelect = { viewModel.onWifiOverrideChanged(WifiOverrideChoice.WIFI_ONLY) },
                    )

                    SelectableOptionCard(
                        label = "Allow Cellular",
                        subtitle = "Sync on both Wi-Fi and mobile data",
                        selected = uiState.wifiOverride == WifiOverrideChoice.ALLOW_CELLULAR,
                        onSelect = { viewModel.onWifiOverrideChanged(WifiOverrideChoice.ALLOW_CELLULAR) },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "Enable Mapping",
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "Active in background sync cycles",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = uiState.enabled,
                                onCheckedChange = viewModel::onEnabledChanged,
                            )
                        }
                    }
                }
            }

            // Primary Save Action
            Button(
                onClick = viewModel::save,
                enabled = uiState.canSave,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    if (uiState.isEditing) "Save Changes" else "Create Mapping",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            content()
        }
    }
}

@Composable
private fun SelectableOptionCard(
    label: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(modifier = Modifier.width(8.dp))
            icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyLarge,
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
