package com.karthick.partysync.ui.browse

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Streams a video directly from the server (auth via the same `PW` header used everywhere else
 * in the app — verified against media3's [DefaultHttpDataSource.Factory] source that
 * `setDefaultRequestProperties` applies to every request the data source makes). Only plays
 * while [isActivePage] is true, so swiping to another page in the pager stops playback rather
 * than continuing in the background.
 */
@Composable
fun VideoPlayerPage(url: String, password: String, isActivePage: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val exoPlayer = remember(url) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("PW" to password))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(isActivePage) {
        exoPlayer.playWhenReady = isActivePage
        if (!isActivePage) exoPlayer.pause()
    }

    AndroidView(
        factory = { PlayerView(it).apply { player = exoPlayer } },
        modifier = modifier.fillMaxSize(),
    )
}
