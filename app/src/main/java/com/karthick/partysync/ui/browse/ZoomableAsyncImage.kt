package com.karthick.partysync.ui.browse

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/**
 * Fit-to-screen image with pinch-to-zoom, pan (once zoomed), and double-tap to reset.
 *
 * Deliberately does NOT use [androidx.compose.foundation.gestures.detectTransformGestures]
 * directly — that consumes every single-finger drag unconditionally, which would swallow the
 * horizontal swipe a parent `HorizontalPager` (the media viewer's page-to-page gesture) needs to
 * see. Instead this only consumes pointer events when there's an actual pinch (2+ pointers) or
 * the image is already zoomed in (a single-finger drag should pan the zoomed photo); a
 * single-finger drag while at 1x scale is left unconsumed so it reaches the pager underneath.
 */
@Composable
fun ZoomableAsyncImage(model: ImageRequest, contentDescription: String?, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val isPinch = event.changes.size > 1
                        if (isPinch || scale > MIN_SCALE) {
                            scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                            if (scale <= MIN_SCALE) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                offsetX += panChange.x
                                offsetY += panChange.y
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY,
            ),
    )
}
