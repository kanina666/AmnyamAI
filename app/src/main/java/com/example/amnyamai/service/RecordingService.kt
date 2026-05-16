package com.example.amnyamai.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RecordingService : Service() {

    private val binder = RecordingBinder()
    private var audioRecord: AudioRecord? = null
    private var readThread: Thread? = null

    var onChunkAvailable: ((ByteArray) -> Unit)? = null
    @Volatile var isRecording = false; private set

    inner class RecordingBinder : Binder() {
        fun getService() = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Запись встречи", NotificationManager.IMPORTANCE_LOW)
        )
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, CHUNK_BYTES * 4)
        )
        audioRecord!!.startRecording()
        isRecording = true
        startForeground(NOTIF_ID, buildNotification())

        readThread = Thread {
            val chunk = ByteArray(CHUNK_BYTES)
            while (isRecording) {
                val ar = audioRecord ?: break
                val read = ar.read(chunk, 0, chunk.size)
                if (read > 0) onChunkAvailable?.invoke(chunk.copyOf(read))
                else if (read < 0) break
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stopRecording() {
        isRecording = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        readThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeetingAgent")
            .setContentText("Идёт запись встречи...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).build()

    override fun onDestroy() { stopRecording(); super.onDestroy() }

    companion object {
        const val CHANNEL_ID = "recording_ch"
        const val NOTIF_ID = 1001
        const val SAMPLE_RATE = 16000
        const val CHUNK_BYTES = 3200  // 100ms at 16kHz mono 16-bit
    }
}
