package io.baiyanwu.coinmonitor.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiChatSessionSelectionStore {
    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    fun select(sessionId: String?) {
        _selectedSessionId.value = sessionId
    }

    fun clear() {
        _selectedSessionId.value = null
    }
}
