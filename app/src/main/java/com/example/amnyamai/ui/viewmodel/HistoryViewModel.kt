package com.example.amnyamai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.amnyamai.data.model.Meeting
import com.example.amnyamai.data.repository.MeetingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MeetingRepository(application)

    private val _meetings = MutableStateFlow<List<Meeting>>(emptyList())
    val meetings: StateFlow<List<Meeting>> = _meetings

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getMeetingHistory().fold(
                onSuccess = { _meetings.value = it.sortedByDescending { m -> m.createdAt } },
                onFailure = { _meetings.value = emptyList() }
            )
            _isLoading.value = false
        }
    }

    fun deleteMeeting(meetingId: String) {
        viewModelScope.launch {
            repository.deleteMeeting(meetingId).onSuccess {
                _meetings.value = _meetings.value.filter { it.id != meetingId }
            }
        }
    }
}
