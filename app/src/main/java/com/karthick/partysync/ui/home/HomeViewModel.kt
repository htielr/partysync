package com.karthick.partysync.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karthick.partysync.data.local.db.FolderMappingEntity
import com.karthick.partysync.data.repository.FolderMappingRepository
import com.karthick.partysync.sync.worker.SyncWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    folderMappingRepository: FolderMappingRepository,
    private val syncWorkScheduler: SyncWorkScheduler,
) : ViewModel() {

    val mappings: StateFlow<List<FolderMappingEntity>> = folderMappingRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun syncNow() = syncWorkScheduler.enqueueManualSync()
}
