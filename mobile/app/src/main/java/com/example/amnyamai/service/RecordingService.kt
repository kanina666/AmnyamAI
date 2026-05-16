package com.example.amnyamai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class RecordingService : Service() {

    private val binder = RecordingBinder()
    private var recorder: MediaRecorder? = null

    var outputFile: File? = null; private set
    var isRecording = false; private set

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

    fun startRecording(): File {
        val file = File(cacheDir, "meeting_${System.currentTimeMillis()}.m4a").also { outputFile = it }
        recorder = MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            prepare(); start()
        }
        isRecording = true
        startForeground(NOTIF_ID, buildNotification())
        return file
    }

    fun stopRecording(): File? {
        if (!isRecording) return outputFile
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        recorder = null; isRecording = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        return outputFile
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
    }
}
