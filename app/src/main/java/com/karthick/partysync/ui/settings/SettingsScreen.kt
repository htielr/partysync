package com.karthick.partysync.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karthick.partysync.data.local.prefs.SettingsRepository

private val INTERVAL_PRESETS_MINUTES = listOf(15, 30, 60, 120, 360)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageServers: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text("Servers", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageServers),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row {
                    Icon(Icons.Filled.Dns, contentDescription = null)
                    Text("Manage servers", modifier = Modifier.padding(start = 8.dp))
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
            }

            Text("Sync behavior", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Wi-Fi only")
                    Text(
                        "Applies to mappings that don't override it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.globalWifiOnly,
                    onCheckedChange = viewModel::onGlobalWifiOnlyChanged,
                )
            }

            Text("Auto-sync interval", style = MaterialTheme.typography.bodyLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(INTERVAL_PRESETS_MINUTES) { minutes ->
                    FilterChip(
                        selected = settings.syncIntervalMinutes == minutes,
                        onClick = { viewModel.onSyncIntervalChanged(minutes) },
                        label = { Text(formatInterval(minutes)) },
                    )
                }
            }

            Text(
                "Android limits automatic background sync to at most every " +
                    "${SettingsRepository.MIN_INTERVAL_MINUTES} minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatInterval(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "$minutes min"
}
