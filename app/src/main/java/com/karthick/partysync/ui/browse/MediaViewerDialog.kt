package com.karthick.partysync.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.request.ImageRequest
import com.karthick.partysync.data.remote.RemoteEntry

@Composable
fun MediaViewerDialog(state: MediaViewerState, urlForEntry: (RemoteEntry) -> String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(enabled = true, onBack = onDismiss)

        val pagerState = rememberPagerState(initialPage = state.startIndex) { state.entries.size }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val entry = state.entries[page]
                val url = remember(entry) { urlForEntry(entry) }
                if (url != null) {
                    MediaPage(
                        entry = entry,
                        url = url,
                        password = state.password,
                        isActivePage = pagerState.settledPage == page,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    state.entries[pagerState.currentPage].name,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    "${pagerState.currentPage + 1} / ${state.entries.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MediaPage(entry: RemoteEntry, url: String, password: String, isActivePage: Boolean) {
    val context = LocalContext.current
    if (entry.isVideoFile()) {
        VideoPlayerPage(url = url, password = password, isActivePage = isActivePage, modifier = Modifier.fillMaxSize())
    } else {
        ZoomableAsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .setHeader("PW", password)
                .crossfade(true)
                .build(),
            contentDescription = entry.name,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
