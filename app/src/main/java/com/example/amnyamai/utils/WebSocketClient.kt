package com.example.amnyamai.utils

import com.example.amnyamai.data.remote.RetrofitClient
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
    private val onError: (Throwable) -> Unit = {}
) {
    private var ws: WebSocket? = null

    fun connect() {
        val wsUrl = RetrofitClient.BASE_URL
            .replace("http://", "ws://")
            .replace("https://", "wss://")
        val request = Request.Builder()
            .url("${wsUrl}ws/meetings/$meetingId/audio?token=$token")
            .build()

        ws = RetrofitClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
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

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError(t)
            }
        })
    }

    fun sendAudioChunk(chunk: ByteArray): Boolean =
        ws?.send(chunk.toByteString()) ?: false

    fun disconnect() = ws?.close(1000, "done")
}
