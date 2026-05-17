package com.example.amnyamai.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.amnyamai.data.remote.RetrofitClient
import com.example.amnyamai.data.repository.MeetingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "HomeVM"

sealed class HomeUiState {
    object Idle : HomeUiState()
    object Loading : HomeUiState()
    data class MeetingCreated(val meetingId: String, val code: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MeetingRepository(application)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState

    val currentUser get() = repository.userStorage.getUser()

    fun createMeeting(title: String) {
        Log.d(TAG, "createMeeting: начало создания встречи '$title'")
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            repository.createMeeting(title).fold(
                onSuccess = { (id, code) -> 
                    Log.d(TAG, "createMeeting: успех id=$id code=$code")
                    _uiState.value = HomeUiState.MeetingCreated(id, code) 
                },
                onFailure = { 
                    Log.e(TAG, "createMeeting: ошибка", it)
                    val msg = it.message ?: ""
                    if (msg.contains("401")) {
                        logout()
                        _uiState.value = HomeUiState.Error("Сессия истекла или отсутствует. Войдите заново.")
                    } else {
                        _uiState.value = HomeUiState.Error(msg.ifBlank { "Ошибка создания встречи" })
                    }
                }
            )
        }
    }

    fun reset() { 
        _uiState.value = HomeUiState.Idle 
    }

    fun logout() {
        repository.userStorage.clear()
        RetrofitClient.setToken(null)
    }
}
