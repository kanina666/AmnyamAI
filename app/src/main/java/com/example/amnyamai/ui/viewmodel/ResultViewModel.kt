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
        val acceptedTasks: List<Task> = emptyList()
    ) : ResultUiState()
    data class AllDone(
        val acceptedTasks: List<Task>,
        val summary: String,
        val isSaving: Boolean = false,
        val saved: Boolean = false,
        val errorMessage: String? = null
    ) : ResultUiState()
    data class Error(val message: String) : ResultUiState()
}

class ResultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MeetingRepository(application)

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState

    private var loadedId = ""
    private var pollAttempts = 0

    fun reload(meetingId: String) {
        loadedId = ""
        pollAttempts = 0
        load(meetingId)
    }

    fun load(meetingId: String) {
        if (loadedId == meetingId) return
        loadedId = meetingId
        viewModelScope.launch {
            repository.getCompletedMeeting(meetingId).fold(
                onSuccess = { meeting ->
                    when (meeting.status) {
                        com.example.amnyamai.data.model.MeetingStatus.PROCESSING,
                        com.example.amnyamai.data.model.MeetingStatus.ACTIVE -> {
                            _uiState.value = ResultUiState.Loading
                            viewModelScope.launch {
                                pollAttempts++
                                if (pollAttempts > 40) {
                                    _uiState.value = ResultUiState.Error("Analysis is taking too long. Please retry.")
                                    return@launch
                                }
                                kotlinx.coroutines.delay(1500L)
                                reload(meetingId)
                            }
                        }
                        com.example.amnyamai.data.model.MeetingStatus.FAILED -> {
                            _uiState.value = ResultUiState.Error("Meeting analysis failed")
                        }
                        com.example.amnyamai.data.model.MeetingStatus.DONE -> {
                            _uiState.value = if (meeting.tasks.isEmpty())
                                ResultUiState.AllDone(emptyList(), meeting.summary)
                            else
                                ResultUiState.Ready(meeting.summary, meeting.tasks)
                        }
                    }
                },
                onFailure = { _uiState.value = ResultUiState.Error(it.message ?: "Ошибка") }
            )
        }
    }

    fun acceptTask(task: Task) {
        val state = _uiState.value as? ResultUiState.Ready ?: return
        val newAccepted = (state.acceptedTasks + task).distinctBy { it.id }
        val next = state.currentIndex + 1
        _uiState.value = if (next >= state.tasks.size)
            ResultUiState.AllDone(newAccepted, state.summary)
        else
            state.copy(currentIndex = next, acceptedTasks = newAccepted)
    }

    fun rejectTask() {
        val state = _uiState.value as? ResultUiState.Ready ?: return
        val next = state.currentIndex + 1
        _uiState.value = if (next >= state.tasks.size)
            ResultUiState.AllDone(state.acceptedTasks, state.summary)
        else
            state.copy(currentIndex = next)
    }

    fun saveToCalendar() {
        val state = _uiState.value as? ResultUiState.AllDone ?: return
        if (state.isSaving || state.saved) return
        _uiState.value = state.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            for (task in state.acceptedTasks) {
                val res = repository.confirmAndSyncTask(task)
                if (res.isFailure) {
                    _uiState.value = state.copy(
                        isSaving = false,
                        saved = false,
                        errorMessage = res.exceptionOrNull()?.message ?: "Calendar sync failed"
                    )
                    return@launch
                }
            }
            _uiState.value = state.copy(isSaving = false, saved = true, errorMessage = null)
        }
    }

    fun updateTaskTitle(index: Int, newTitle: String) {
        val state = _uiState.value as? ResultUiState.Ready ?: return
        val updated = state.tasks.toMutableList()
        val task = updated[index]
        updated[index] = task.copy(title = newTitle)
        _uiState.value = state.copy(tasks = updated)
        viewModelScope.launch { repository.updateTaskTitle(task.id, newTitle) }
    }
}
