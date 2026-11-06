package com.karthick.partysync.ui.mappingdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karthick.partysync.data.local.db.FolderMappingDao
import com.karthick.partysync.data.local.db.FolderMappingEntity
import com.karthick.partysync.data.local.db.SyncHistoryDao
import com.karthick.partysync.data.local.db.SyncHistoryEntity
import com.karthick.partysync.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MappingDetailUiState(
    val mapping: FolderMappingEntity? = null,
    val history: List<SyncHistoryEntity> = emptyList(),
) {
    val totalBytes: Long get() = history.sumOf { it.sizeBytes }
    val offloadedCount: Int get() = history.count { it.offloaded }
}

@HiltViewModel
class MappingDetailViewModel @Inject constructor(
    private val folderMappingDao: FolderMappingDao,
    private val syncHistoryDao: SyncHistoryDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mappingId: Long =
        checkNotNull(savedStateHandle.get<String>(Screen.MappingDetail.ARG_MAPPING_ID)).toLong()

    private val mappingState = MutableStateFlow<FolderMappingEntity?>(null)

    val uiState: StateFlow<MappingDetailUiState> = combine(
        mappingState,
        syncHistoryDao.observeForMapping(mappingId),
    ) { mapping, history -> MappingDetailUiState(mapping, history) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MappingDetailUiState())

    init {
        viewModelScope.launch {
            mappingState.value = folderMappingDao.getById(mappingId)
        }
    }

    /** Clears this mapping's history only; the sync engine's operational state is untouched. */
    fun clearHistory() {
        viewModelScope.launch { syncHistoryDao.clearForMapping(mappingId) }
    }
}
