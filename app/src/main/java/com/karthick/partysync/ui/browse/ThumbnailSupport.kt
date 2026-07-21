package com.karthick.partysync.ui.browse

import com.karthick.partysync.data.remote.RemoteEntry

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm", "avi", "m4v", "3gp")

/** Whether this entry is an image/video file the server can generate a real thumbnail for. */
fun RemoteEntry.isThumbnailable(): Boolean =
    !isDirectory && name.substringAfterLast('.', "").lowercase() in (IMAGE_EXTENSIONS + VIDEO_EXTENSIONS)
