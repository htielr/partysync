package com.karthick.partysync.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karthick.partysync.domain.model.CHAT_ROOM_GENERAL
import com.karthick.partysync.domain.model.CHAT_ROOM_VAULT
import com.karthick.partysync.domain.model.ChatMessage
import com.karthick.partysync.ui.navigation.PartySyncBottomBar
import com.karthick.partysync.ui.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateTab: (Screen) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showConfigDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Relay") },
                actions = {
                    if (uiState.config != null) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            enabled = uiState.messages.isNotEmpty() && !uiState.isClearing,
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear chat history")
                        }
                        IconButton(onClick = { showConfigDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Configure Chat Relay")
                        }
                    }
                },
            )
        },
        bottomBar = { PartySyncBottomBar(selected = Screen.Chat, onNavigate = onNavigateTab) },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (uiState.config == null) {
                ChatEmptyState(onConfigure = { showConfigDialog = true })
            } else {
                TabRow(selectedTabIndex = if (uiState.selectedRoom == CHAT_ROOM_VAULT) 0 else 1) {
                    Tab(
                        selected = uiState.selectedRoom == CHAT_ROOM_VAULT,
                        onClick = { viewModel.selectRoom(CHAT_ROOM_VAULT) },
                        text = { Text("Vault") },
                    )
                    Tab(
                        selected = uiState.selectedRoom == CHAT_ROOM_GENERAL,
                        onClick = { viewModel.selectRoom(CHAT_ROOM_GENERAL) },
                        text = { Text("General") },
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageRow(message)
                    }
                }

                ChatInputRow(
                    room = uiState.selectedRoom,
                    isSending = uiState.isSending,
                    onSend = { text -> scope.launch { viewModel.sendText(text) } },
                    onPickFile = viewModel::uploadFile,
                )
            }
        }
    }

    if (showConfigDialog) {
        ChatConfigDialog(
            initialBaseUrl = uiState.config?.baseUrl.orEmpty(),
            initialApiKey = uiState.config?.apiKey.orEmpty(),
            onDismiss = { showConfigDialog = false },
            onSave = { baseUrl, apiKey ->
                viewModel.saveConfig(baseUrl, apiKey)
                showConfigDialog = false
            },
        )
    }

    if (showClearConfirmDialog) {
        val roomLabel = if (uiState.selectedRoom == CHAT_ROOM_VAULT) "Vault" else "General"
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear $roomLabel history?") },
            text = {
                Text(
                    "This deletes all messages in this room from the server for everyone " +
                        "viewing it. Files already saved to your server are not affected - " +
                        "only this message log. This can't be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChatEmptyState(onConfigure: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Forum,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Chat Relay isn't configured yet", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add your chat-relay server URL and API key to send links and files to Vault or General.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onConfigure) { Text("Configure Chat Relay") }
        }
    }
}

@Composable
private fun ChatConfigDialog(
    initialBaseUrl: String,
    initialApiKey: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Chat Relay") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("e.g. chat.karthickcloud.app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("Webhook API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (baseUrl.isNotBlank() && apiKey.isNotBlank()) onSave(baseUrl, apiKey) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChatMessageRow(message: ChatMessage) {
    when (message.kind) {
        "status" -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = message.content.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (message.kind == "file") Icons.Filled.AttachFile else Icons.Filled.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = message.filename ?: message.content.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ChatInputRow(
    room: String,
    isSending: Boolean,
    onSend: (String) -> Unit,
    onPickFile: (Uri) -> Unit,
) {
    var textInput by remember(room) { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPickFile)
    }
    val textEnabled = room == CHAT_ROOM_VAULT

    Surface(tonalElevation = 2.dp) {
        Column {
            if (!textEnabled) {
                Text(
                    text = "General room only accepts file uploads",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Attach file")
                }
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    enabled = textEnabled,
                    placeholder = { Text(if (textEnabled) "Paste a twitter/x/instagram link" else "Use the attach button") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onSend(textInput); textInput = "" },
                    enabled = textEnabled && !isSending && textInput.isNotBlank(),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
