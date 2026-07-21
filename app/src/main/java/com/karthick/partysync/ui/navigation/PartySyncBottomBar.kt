package com.karthick.partysync.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Bottom tab bar shown only on the two top-level destinations: [Screen.Home] and [Screen.Browse]. */
@Composable
fun PartySyncBottomBar(selected: Screen, onNavigate: (Screen) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == Screen.Home,
            onClick = { onNavigate(Screen.Home) },
            icon = { Icon(Icons.Filled.CloudSync, contentDescription = null) },
            label = { Text("Sync") },
        )
        NavigationBarItem(
            selected = selected == Screen.Browse,
            onClick = { onNavigate(Screen.Browse) },
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            label = { Text("Browse") },
        )
    }
}
