package com.karthick.partysync.ui.addmapping

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
                title = { Text(if (uiState.isEditing) "Edit folder" else "Add folder") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete mapping")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            if (servers.isEmpty()) {
                Text(
                    "No servers configured yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onAddServer, modifier = Modifier.fillMaxWidth()) {
                    Text("Add a server")
                }
            } else {
                servers.forEach { server ->
                    ServerOption(
                        label = server.displayName,
                        selected = uiState.selectedServerId == server.id,
                        onSelect = { viewModel.onServerSelected(server.id) },
                    )
                }
            }

            OutlinedButton(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Text(
                    text = uiState.folderDisplayName.ifBlank { "Choose folder on this phone" },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            OutlinedTextField(
                value = uiState.remoteBasePath,
                onValueChange = viewModel::onRemoteBasePathChanged,
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

            Text("Sync mode", style = MaterialTheme.typography.titleMedium)
            SyncModeOption(
                label = "One-way upload",
                description = "New/changed files on this phone are pushed to the server.",
                selected = uiState.syncMode == SyncMode.ONE_WAY_UPLOAD,
                onSelect = { viewModel.onSyncModeChanged(SyncMode.ONE_WAY_UPLOAD) },
            )
            SyncModeOption(
                label = "Two-way sync",
                description = "Changes on either side are reconciled; conflicts are kept as both copies.",
                selected = uiState.syncMode == SyncMode.TWO_WAY,
                onSelect = { viewModel.onSyncModeChanged(SyncMode.TWO_WAY) },
            )

            Text("Wi-Fi", style = MaterialTheme.typography.titleMedium)
            WifiOverrideOption(
                label = "Inherit app setting",
                selected = uiState.wifiOverride == WifiOverrideChoice.INHERIT,
                onSelect = { viewModel.onWifiOverrideChanged(WifiOverrideChoice.INHERIT) },
            )
            WifiOverrideOption(
                label = "Always Wi-Fi only",
                selected = uiState.wifiOverride == WifiOverrideChoice.WIFI_ONLY,
                onSelect = { viewModel.onWifiOverrideChanged(WifiOverrideChoice.WIFI_ONLY) },
            )
            WifiOverrideOption(
                label = "Allow cellular",
                selected = uiState.wifiOverride == WifiOverrideChoice.ALLOW_CELLULAR,
                onSelect = { viewModel.onWifiOverrideChanged(WifiOverrideChoice.ALLOW_CELLULAR) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Enabled")
                Switch(checked = uiState.enabled, onCheckedChange = viewModel::onEnabledChanged)
            }

            Button(
                onClick = viewModel::save,
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun SyncModeOption(label: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(label)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun WifiOverrideOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
