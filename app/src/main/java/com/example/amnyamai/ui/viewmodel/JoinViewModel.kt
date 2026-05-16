package com.example.amnyamai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.amnyamai.data.model.Meeting
import com.example.amnyamai.data.remote.RetrofitClient
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

    private val _uiState = MutableStateFlow<JoinUiState>(JoinUiState.Idle)
    val uiState: StateFlow<JoinUiState> = _uiState

    private val _meetingEnded = MutableStateFlow<String?>(null)
    val meetingEnded: StateFlow<String?> = _meetingEnded

    fun reset() { _uiState.value = JoinUiState.Idle }

    fun join(code: String) {
        val normalized = code.trim().uppercase().replace("-", "").take(8)
        if (normalized.isBlank()) return

        _uiState.value = JoinUiState.Loading
        viewModelScope.launch {
            try {
                val res = RetrofitClient.apiService.joinMeeting(normalized)
                if (!res.isSuccessful || res.body() == null) {
                    _uiState.value = JoinUiState.Error("Ошибка подключения: ${res.code()}")
                    return@launch
                }
                _meetingEnded.value = res.body()!!.id
            } catch (e: Exception) {
                _uiState.value = JoinUiState.Error(e.message ?: "Ошибка подключения к серверу")
            }
        }
    }
}
