package com.example.amnyamai.utils

import com.example.amnyamai.data.remote.RetrofitClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class MeetingWebSocket(
    private val meetingId: String,
    private val onParticipantJoined: (String) -> Unit = {},
    private val onMeetingEnded: () -> Unit = {}
) {
    private var ws: WebSocket? = null

    fun connect() {
        val wsUrl = RetrofitClient.BASE_URL
            .replace("http://", "ws://")
            .replace("https://", "wss://")
        val request = Request.Builder()
            .url("${wsUrl}ws/meeting/$meetingId")
            .build()

        ws = RetrofitClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when {
                    text.startsWith("JOIN:") -> onParticipantJoined(text.removePrefix("JOIN:"))
                    text == "MEETING_ENDED"  -> onMeetingEnded()
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {}
        })
    }

    fun send(message: String) = ws?.send(message)
    fun disconnect() = ws?.close(1000, "left")
}
