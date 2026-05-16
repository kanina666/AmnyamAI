package com.example.amnyamai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.amnyamai.data.model.Meeting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class JoinUiState {
    object Idle : JoinUiState()
    object Loading : JoinUiState()
    data class Joined(val meeting: Meeting) : JoinUiState()
    data class Error(val message: String) : JoinUiState()
}

class JoinViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<JoinUiState>(JoinUiState.Idle)
    val uiState: StateFlow<JoinUiState> = _uiState

    private val _meetingEnded = MutableStateFlow<String?>(null)
    val meetingEnded: StateFlow<String?> = _meetingEnded

    fun join(code: String) {
        _uiState.value = JoinUiState.Error("Присоединение по коду пока недоступно")
    }
}
