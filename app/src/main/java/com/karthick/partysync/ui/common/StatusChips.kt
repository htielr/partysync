package com.karthick.partysync.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthick.partysync.domain.model.FileSyncStatus
import com.karthick.partysync.domain.model.MappingSyncStatus
import com.karthick.partysync.domain.model.SyncMode
import com.karthick.partysync.ui.theme.StatusError
import com.karthick.partysync.ui.theme.StatusErrorContainer
import com.karthick.partysync.ui.theme.StatusInfo
import com.karthick.partysync.ui.theme.StatusInfoContainer
import com.karthick.partysync.ui.theme.StatusSuccess
import com.karthick.partysync.ui.theme.StatusSuccessContainer
import com.karthick.partysync.ui.theme.StatusWarning
import com.karthick.partysync.ui.theme.StatusWarningContainer

@Composable
fun SyncStatusChip(
    status: MappingSyncStatus,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (!enabled) {
        StatusBadge(
            text = "Paused",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                Icon(
                    Icons.Filled.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = modifier,
        )
        return
    }

    val (label, containerColor, contentColor, icon) = when (status) {
        MappingSyncStatus.IDLE -> StatusChipConfig(
            label = "Idle",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = { DotIndicator(MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        MappingSyncStatus.RUNNING -> StatusChipConfig(
            label = "Syncing…",
            containerColor = StatusInfoContainer,
            contentColor = StatusInfo,
            icon = {
                Icon(
                    Icons.Filled.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = StatusInfo,
                )
            },
        )
        MappingSyncStatus.SUCCESS -> StatusChipConfig(
            label = "Up to date",
            containerColor = StatusSuccessContainer,
            contentColor = StatusSuccess,
            icon = {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = StatusSuccess,
                )
            },
        )
        MappingSyncStatus.PARTIAL_FAILURE -> StatusChipConfig(
            label = "Partial issues",
            containerColor = StatusWarningContainer,
            contentColor = StatusWarning,
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = StatusWarning,
                )
            },
        )
        MappingSyncStatus.AUTH_ERROR -> StatusChipConfig(
            label = "Auth error",
            containerColor = StatusErrorContainer,
            contentColor = StatusError,
            icon = {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = StatusError,
                )
            },
        )
        MappingSyncStatus.PERMISSION_LOST -> StatusChipConfig(
            label = "Permission lost",
            containerColor = StatusErrorContainer,
            contentColor = StatusError,
            icon = {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = StatusError,
                )
            },
        )
        MappingSyncStatus.SERVER_MISSING -> StatusChipConfig(
            label = "Server missing",
            containerColor = StatusErrorContainer,
            contentColor = StatusError,
            icon = {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = StatusError,
                )
            },
        )
    }

    StatusBadge(
        text = label,
        containerColor = containerColor,
        contentColor = contentColor,
        icon = icon,
        modifier = modifier,
    )
}

@Composable
fun SyncModeChip(
    mode: SyncMode,
    modifier: Modifier = Modifier,
) {
    val isTwoWay = mode == SyncMode.TWO_WAY
    val label = if (isTwoWay) "Two-Way" else "Upload Only"
    val icon = if (isTwoWay) Icons.Filled.SyncAlt else Icons.Filled.CloudUpload
    val container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    val content = MaterialTheme.colorScheme.onPrimaryContainer

    StatusBadge(
        text = label,
        containerColor = container,
        contentColor = content,
        icon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = content,
            )
        },
        modifier = modifier,
    )
}

@Composable
fun FileStatusChip(
    status: FileSyncStatus,
    modifier: Modifier = Modifier,
) {
    val (label, containerColor, contentColor) = when (status) {
        FileSyncStatus.PENDING -> Triple("Pending", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        FileSyncStatus.UPLOADING -> Triple("Uploading…", StatusInfoContainer, StatusInfo)
        FileSyncStatus.DOWNLOADING -> Triple("Downloading…", StatusInfoContainer, StatusInfo)
        FileSyncStatus.SUCCESS -> Triple("Synced", StatusSuccessContainer, StatusSuccess)
        FileSyncStatus.CONFLICT_RESOLVED -> Triple("Conflict Resolved", StatusWarningContainer, StatusWarning)
        FileSyncStatus.FAILED_RETRYABLE -> Triple("Failed (Retry)", StatusWarningContainer, StatusWarning)
        FileSyncStatus.FAILED_AUTH -> Triple("Auth Failed", StatusErrorContainer, StatusError)
        FileSyncStatus.FAILED_OTHER -> Triple("Failed", StatusErrorContainer, StatusError)
    }

    StatusBadge(
        text = label,
        containerColor = containerColor,
        contentColor = contentColor,
        icon = null,
        modifier = modifier,
    )
}

@Composable
private fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                it()
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun DotIndicator(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(color, CircleShape),
    )
}

private data class StatusChipConfig(
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
    val icon: @Composable () -> Unit,
)
