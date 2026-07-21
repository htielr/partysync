package com.karthick.partysync.ui.common

/** Shared UI state for the live server folder browser (share-upload and add-mapping screens). */
data class RemoteFolderBrowserState(
    val isOpen: Boolean = false,
    val currentPath: String = "",
    val folders: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
