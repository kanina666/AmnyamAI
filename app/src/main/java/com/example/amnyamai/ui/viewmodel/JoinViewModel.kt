package com.example.amnyamai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.amnyamai.data.model.Meeting
import com.example.amnyamai.data.repository.MeetingRepository
import com.example.amnyamai.utils.MeetingWebSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class JoinUiState {
    object Idle : JoinUiState()
    object Loading : JoinUiState()
    data class Joined(val meeting: Meeting) : JoinUiState()
    data class Error(val message: String) : JoinUiState()
}

class JoinViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MeetingRepository(application)

    private val _uiState = MutableStateFlow<JoinUiState>(JoinUiState.Idle)
    val uiState: StateFlow<JoinUiState> = _uiState

    private val _meetingEnded = MutableStateFlow<String?>(null)
    val meetingEnded: StateFlow<String?> = _meetingEnded

    private var webSocket: MeetingWebSocket? = null

    fun join(code: String) {
        if (code.isBlank()) return
        _uiState.value = JoinUiState.Loading
        viewModelScope.launch {
            repository.joinMeeting(code.trim().uppercase()).fold(
                onSuccess = { meeting ->
                    _uiState.value = JoinUiState.Joined(meeting)
                    connectWebSocket(meeting.id)
                },
                onFailure = { _uiState.value = JoinUiState.Error(it.message ?: "Ошибка") }
            )
        }
    }

    private fun connectWebSocket(meetingId: String) {
        webSocket = MeetingWebSocket(
            meetingId = meetingId,
            onMeetingEnded = { _meetingEnded.value = meetingId }
        ).also { it.connect() }
    }

    override fun onCleared() { webSocket?.disconnect(); super.onCleared() }
}
