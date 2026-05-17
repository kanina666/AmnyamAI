package com.example.amnyamai.utils

import android.util.Log
import com.example.amnyamai.data.remote.RetrofitClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

class MeetingWebSocket(
    private val meetingId: String,
    private val token: String,
    private val onTranscript: (speaker: String, text: String, isFinal: Boolean) -> Unit = { _, _, _ -> },
    private val onConnected: () -> Unit = {},
    private val onReconnecting: (attempt: Int) -> Unit = {},
    private val onError: (Throwable) -> Unit = {},
) {
    @Volatile private var ws: WebSocket? = null
    @Volatile var isConnected = false
        private set
    @Volatile private var isStopped = false
    private var reconnectAttempts = 0
    private val closedDeferred = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        isStopped = false
        openSocket()
    }

    private fun openSocket() {
        val wsUrl = RetrofitClient.BASE_URL
            .replace("http://", "ws://")
            .replace("https://", "wss://")
        val request = Request.Builder()
            .url("${wsUrl}ws/meetings/$meetingId/audio?token=$token")
            .build()

        ws = RetrofitClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                reconnectAttempts = 0
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.optString("type") == "transcript") {
                        onTranscript(
                            json.optString("speaker_tag", "unknown"),
                            json.optString("text", ""),
                            json.optBoolean("is_final", false)
                        )
                    }
                } catch (_: Exception) {}
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                closedDeferred.completeIfActive()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                if (isStopped) {
                    closedDeferred.completeIfActive()
                    return
                }
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++
                    onReconnecting(reconnectAttempts)
                    scope.launch {
                        delay(RECONNECT_DELAY_MS * reconnectAttempts)
                        if (!isStopped) openSocket()
                    }
                } else {
                    closedDeferred.completeIfActive()
                    onError(t)
                }
            }
        })
    }

    fun sendAudioChunk(chunk: ByteArray): Boolean {
        if (!isConnected) return false
        val chank =  ws?.send(chunk.toByteString()) ?: false
        Log.d("TAG", "sendAudioChunk: $chank")

        return chank
    }

    suspend fun awaitClosed() = closedDeferred.await()

    fun disconnect() {
        isStopped = true
        isConnected = false
        scope.cancel()
        ws?.close(1000, "done")
        ws = null
    }

    private fun CompletableDeferred<Unit>.completeIfActive() {
        if (!isCompleted) complete(Unit)
    }

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_DELAY_MS = 1500L
    }
}
