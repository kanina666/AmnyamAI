package com.example.amnyamai.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.amnyamai.data.repository.MeetingRepository
import com.example.amnyamai.service.RecordingService
import com.example.amnyamai.utils.MeetingWebSocket
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class RecordingUiState {
    object Idle : RecordingUiState()
    data class Recording(val seconds: Long, val participants: List<String>) : RecordingUiState()
    object Uploading : RecordingUiState()
    data class Error(val message: String) : RecordingUiState()
}

class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MeetingRepository(application)

    private val _uiState = MutableStateFlow<RecordingUiState>(RecordingUiState.Idle)
    val uiState: StateFlow<RecordingUiState> = _uiState

    private val _navigateToResult = MutableStateFlow<String?>(null)
    val navigateToResult: StateFlow<String?> = _navigateToResult

    private var recService: RecordingService? = null
    private var timerJob: Job? = null
    private var elapsedSec = 0L
    private var meetingId = ""
    private var isBound = false
    private val participants = mutableListOf<String>()
    private var webSocket: MeetingWebSocket? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            recService = (b as RecordingService.RecordingBinder).getService()
            recService?.startRecording()
            startTimer()
        }
        override fun onServiceDisconnected(n: ComponentName?) { recService = null; isBound = false }
    }

    fun start(meetingId: String) {
        this.meetingId = meetingId
        val app = getApplication<Application>()
        app.startForegroundService(Intent(app, RecordingService::class.java))
        app.bindService(Intent(app, RecordingService::class.java), conn, Context.BIND_AUTO_CREATE)
        isBound = true
        _uiState.value = RecordingUiState.Recording(0, emptyList())

        webSocket = MeetingWebSocket(
            meetingId = meetingId,
            onParticipantJoined = { name ->
                participants.add(name)
                val cur = _uiState.value
                if (cur is RecordingUiState.Recording)
                    _uiState.value = cur.copy(participants = participants.toList())
            }
        ).also { it.connect() }
    }

    fun stopAndUpload() {
        timerJob?.cancel()
        webSocket?.send("MEETING_ENDED")
        webSocket?.disconnect()
        val file = recService?.stopRecording()
        unbind()
        if (file != null && file.exists() && file.length() > 0) upload(file)
        else _uiState.value = RecordingUiState.Error("Файл записи пустой")
    }

    private fun upload(file: File) {
        _uiState.value = RecordingUiState.Uploading
        viewModelScope.launch {
            repository.uploadAudio(meetingId, file).fold(
                onSuccess = { _navigateToResult.value = meetingId },
                onFailure = { _uiState.value = RecordingUiState.Error(it.message ?: "Ошибка загрузки") }
            )
        }
    }

    private fun startTimer() {
        elapsedSec = 0
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L); elapsedSec++
                val cur = _uiState.value
                if (cur is RecordingUiState.Recording)
                    _uiState.value = cur.copy(seconds = elapsedSec)
            }
        }
    }

    private fun unbind() {
        if (isBound) {
            try { getApplication<Application>().unbindService(conn) } catch (_: Exception) {}
            isBound = false
        }
    }

    override fun onCleared() { timerJob?.cancel(); recService?.stopRecording(); unbind(); super.onCleared() }
}
