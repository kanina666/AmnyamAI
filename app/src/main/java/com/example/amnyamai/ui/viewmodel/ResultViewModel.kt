package com.example.amnyamai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.amnyamai.data.model.Task
import com.example.amnyamai.data.repository.MeetingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ResultUiState {
    object Loading : ResultUiState()
    data class Ready(
        val summary: String,
        val tasks: List<Task>,
        val currentIndex: Int = 0,
        val isSaving: Boolean = false
    ) : ResultUiState()
    object AllDone : ResultUiState()
    data class Error(val message: String) : ResultUiState()
}

class ResultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MeetingRepository(application)

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState

    private var loadedId = ""

    fun load(meetingId: String) {
        if (loadedId == meetingId) return
        loadedId = meetingId
        viewModelScope.launch {
            repository.getCompletedMeeting(meetingId).fold(
                onSuccess = { meeting ->
                    if (meeting.tasks.isEmpty())
                        _uiState.value = ResultUiState.AllDone
                    else
                        _uiState.value = ResultUiState.Ready(meeting.summary, meeting.tasks)
                },
                onFailure = { _uiState.value = ResultUiState.Error(it.message ?: "Ошибка") }
            )
        }
    }

    fun acceptTask(task: Task) {
        val state = _uiState.value as? ResultUiState.Ready ?: return
        viewModelScope.launch {
            repository.confirmAndSyncTask(task)
        }
        advance(state)
    }

    fun rejectTask() {
        val state = _uiState.value as? ResultUiState.Ready ?: return
        advance(state)
    }

    private fun advance(state: ResultUiState.Ready) {
        val next = state.currentIndex + 1
        _uiState.value = if (next >= state.tasks.size) ResultUiState.AllDone
        else state.copy(currentIndex = next)
    }

    fun updateTaskTitle(index: Int, newTitle: String) {
        val state = _uiState.value as? ResultUiState.Ready ?: return
        val updated = state.tasks.toMutableList()
        val task = updated[index]
        updated[index] = task.copy(title = newTitle)
        _uiState.value = state.copy(tasks = updated)
        viewModelScope.launch {
            repository.updateTaskTitle(task.id, newTitle)
        }
    }
}
