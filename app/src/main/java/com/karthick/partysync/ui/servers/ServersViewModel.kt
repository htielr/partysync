package com.karthick.partysync.ui.servers

import androidx.lifecycle.ViewModel
import com.karthick.partysync.data.local.prefs.ServerProfile
import com.karthick.partysync.data.local.prefs.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ServersViewModel @Inject constructor(
    serverRepository: ServerRepository,
) : ViewModel() {
    val servers: StateFlow<List<ServerProfile>> = serverRepository.servers
}
