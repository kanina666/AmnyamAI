package com.example.amnyamai.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.amnyamai.data.local.UserStorage
import com.example.amnyamai.data.repository.MeetingRepository
import com.example.amnyamai.service.RecordingService
import com.example.amnyamai.utils.MeetingWebSocket
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class TranscriptLine(val speaker: String, val text: String)

sealed class RecordingUiState {
    object Idle : RecordingUiState()
    data class Recording(
        val seconds: Long,
        val participants: List<String>,
        val transcriptLines: List<TranscriptLine> = emptyList(),
        val isReconnecting: Boolean = false
    ) : RecordingUiState()
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
    private var webSocket: MeetingWebSocket? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            recService = (b as RecordingService.RecordingBinder).getService()
            recService?.onChunkAvailable = fun(chunk: ByteArray) {
                Log.d("TAG", "хочу чтобы на меня написили")
                webSocket?.sendAudioChunk(chunk)
            }
            recService?.startRecording()
            startTimer()
        }
        override fun onServiceDisconnected(n: ComponentName?) { recService = null; isBound = false }
    }

    fun start(meetingId: String) {
        this.meetingId = meetingId
        val app = getApplication<Application>()
        val token = UserStorage(app).getToken() ?: ""

        webSocket = MeetingWebSocket(
            meetingId = meetingId,
            token = token,
            onTranscript = { speaker, text, isFinal ->
                if (isFinal && text.isNotBlank()) {
                    viewModelScope.launch {
                        val cur = _uiState.value as? RecordingUiState.Recording ?: return@launch
                        val newLines = (cur.transcriptLines + TranscriptLine(speaker, text)).takeLast(10)
                        val speakers = (cur.participants + speaker).distinct()
                        _uiState.value = cur.copy(transcriptLines = newLines, participants = speakers)
                    }
                }
            },
            onConnected = {
                viewModelScope.launch {
                    val cur = _uiState.value as? RecordingUiState.Recording ?: return@launch
                    _uiState.value = cur.copy(isReconnecting = false)
                }
            },
            onReconnecting = {
                viewModelScope.launch {
                    val cur = _uiState.value as? RecordingUiState.Recording ?: return@launch
                    _uiState.value = cur.copy(isReconnecting = true)
                }
            },
            onError = { err ->
                viewModelScope.launch {
                    _uiState.value = RecordingUiState.Error(
                        "Потеряно соединение с сервером: ${err.message}"
                    )
                }
            }
        ).also { it.connect() }

        app.startForegroundService(Intent(app, RecordingService::class.java))
        app.bindService(Intent(app, RecordingService::class.java), conn, Context.BIND_AUTO_CREATE)
        isBound = true
        _uiState.value = RecordingUiState.Recording(0, emptyList())
    }

    fun reset() { _uiState.value = RecordingUiState.Idle }

    fun stopAndUpload() {
        timerJob?.cancel()
        recService?.stopRecording()
        unbind()
        val ws = webSocket
        webSocket = null
        viewModelScope.launch {
            ws?.let {
                it.disconnect()
                withTimeoutOrNull(2000L) { it.awaitClosed() }
            }
            analyzeAndNavigate()
        }
    }

    private suspend fun analyzeAndNavigate() {
        _uiState.value = RecordingUiState.Uploading
        repository.finishMeeting(meetingId).fold(
            onSuccess = { _navigateToResult.value = meetingId },
            onFailure = { _uiState.value = RecordingUiState.Error(it.message ?: "Ошибка анализа") }
        )
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

    override fun onCleared() {
        timerJob?.cancel()
        recService?.stopRecording()
        unbind()
        webSocket?.disconnect()
        super.onCleared()
    }
}
