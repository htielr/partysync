package com.karthick.partysync.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Bottom tab bar shown only on the two top-level destinations: [Screen.Home] and [Screen.Browse]. */
@Composable
fun PartySyncBottomBar(selected: Screen, onNavigate: (Screen) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 8.dp,
    ) {
        val isHome = selected == Screen.Home
        val isBrowse = selected == Screen.Browse

        NavigationBarItem(
            selected = isHome,
            onClick = { onNavigate(Screen.Home) },
            icon = {
                Icon(
                    imageVector = if (isHome) Icons.Filled.CloudSync else Icons.Outlined.CloudSync,
                    contentDescription = "Sync",
                )
            },
            label = {
                Text(
                    text = "Sync",
                    fontWeight = if (isHome) FontWeight.Bold else FontWeight.Normal,
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        NavigationBarItem(
            selected = isBrowse,
            onClick = { onNavigate(Screen.Browse) },
            icon = {
                Icon(
                    imageVector = if (isBrowse) Icons.Filled.FolderSpecial else Icons.Outlined.Folder,
                    contentDescription = "Browse",
                )
            },
            label = {
                Text(
                    text = "Browse",
                    fontWeight = if (isBrowse) FontWeight.Bold else FontWeight.Normal,
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
