package com.karthick.partysync.ui.mappingdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karthick.partysync.data.local.db.FolderMappingDao
import com.karthick.partysync.data.local.db.FolderMappingEntity
import com.karthick.partysync.data.local.db.SyncFileStateDao
import com.karthick.partysync.data.local.db.SyncFileStateEntity
import com.karthick.partysync.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MappingDetailUiState(
    val mapping: FolderMappingEntity? = null,
    val files: List<SyncFileStateEntity> = emptyList(),
)

@HiltViewModel
class MappingDetailViewModel @Inject constructor(
    private val folderMappingDao: FolderMappingDao,
    syncFileStateDao: SyncFileStateDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mappingId: Long =
        checkNotNull(savedStateHandle.get<String>(Screen.MappingDetail.ARG_MAPPING_ID)).toLong()

    private val mappingState = MutableStateFlow<FolderMappingEntity?>(null)

    val uiState: StateFlow<MappingDetailUiState> = kotlinx.coroutines.flow.combine(
        mappingState,
        syncFileStateDao.observeAllForMapping(mappingId),
    ) { mapping, files -> MappingDetailUiState(mapping, files) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MappingDetailUiState())

    init {
        viewModelScope.launch {
            mappingState.value = folderMappingDao.getById(mappingId)
        }
    }
}
