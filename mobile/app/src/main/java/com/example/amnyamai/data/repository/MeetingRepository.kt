package com.example.amnyamai.data.repository

import android.content.Context
import com.example.amnyamai.data.local.UserStorage
import com.example.amnyamai.data.model.Meeting
import com.example.amnyamai.data.model.MeetingStatus
import com.example.amnyamai.data.model.Participant
import com.example.amnyamai.data.model.Task
import com.example.amnyamai.data.remote.CreateMeetingRequest
import com.example.amnyamai.data.remote.JoinRequest
import com.example.amnyamai.data.remote.RetrofitClient
import com.example.amnyamai.data.remote.SaveTaskRequest
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MeetingRepository(context: Context) {

    private val api = RetrofitClient.apiService
    val userStorage = UserStorage(context)

    suspend fun createMeeting(title: String): Result<Pair<String, String>> {
        val login = userStorage.getUser()?.login ?: "unknown"
        return try {
            val res = api.createMeeting(CreateMeetingRequest(title, login))
            if (res.isSuccessful && res.body() != null)
                Result.success(res.body()!!.meetingId to res.body()!!.code)
            else Result.failure(Exception("Ошибка создания: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun joinMeeting(code: String): Result<Meeting> {
        val user = userStorage.getUser() ?: return Result.failure(Exception("Не авторизован"))
        return try {
            // Use code as meetingId for simplicity (backend maps code → id)
            val res = api.joinMeeting(code, JoinRequest(user.login, user.fullName))
            if (res.isSuccessful && res.body() != null) {
                val b = res.body()!!
                Result.success(
                    Meeting(
                        id = b.meetingId,
                        code = code,
                        title = b.title,
                        organizer = b.organizer,
                        participants = b.participants.map { Participant(it.login, it.name, it.login) }
                    )
                )
            } else Result.failure(Exception("Встреча не найдена"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun uploadAudio(meetingId: String, file: File): Result<Unit> {
        return try {
            val part = MultipartBody.Part.createFormData(
                "audio", file.name, file.asRequestBody("audio/mpeg".toMediaType())
            )
            val res = api.uploadAudio(meetingId, part)
            if (res.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Ошибка загрузки: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun pollResult(meetingId: String, maxAttempts: Int = 40): Result<Meeting> {
        repeat(maxAttempts) { attempt ->
            try {
                val res = api.getMeetingResult(meetingId)
                if (res.isSuccessful && res.body() != null) {
                    val b = res.body()!!
                    if (b.status == "done") {
                        val user = userStorage.getUser()
                        val myTasks = b.tasks
                            .filter { it.assignee == user?.login || it.assignee == user?.fullName }
                            .map { Task(it.id, it.title, it.description, it.assignee, it.deadline, meetingId) }
                        return Result.success(
                            Meeting(
                                id = meetingId, code = "", title = "",
                                organizer = "", status = MeetingStatus.DONE,
                                summary = b.summary, tasks = myTasks
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (attempt == maxAttempts - 1) return Result.failure(e)
            }
            delay(3000L)
        }
        return Result.failure(Exception("Таймаут ожидания результата"))
    }

    suspend fun saveTaskToCalendar(task: Task): Result<Unit> {
        val login = userStorage.getUser()?.login ?: return Result.failure(Exception("Не авторизован"))
        return try {
            val res = api.saveTask(SaveTaskRequest(task.id, task.meetingId, login))
            if (res.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Ошибка сохранения: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getMeetingHistory(): Result<List<Meeting>> {
        return try {
            val res = api.getMeetings()
            if (res.isSuccessful && res.body() != null) {
                val list = res.body()!!.map {
                    Meeting(
                        id = it.meetingId, code = "", title = it.title,
                        organizer = "", createdAt = it.createdAt,
                        status = MeetingStatus.DONE, summary = it.summary,
                        participants = List(it.participantCount) { i ->
                            Participant("$i", "Участник ${i + 1}", "")
                        }
                    )
                }
                Result.success(list)
            } else Result.failure(Exception("Ошибка загрузки истории"))
        } catch (e: Exception) { Result.failure(e) }
    }
}
