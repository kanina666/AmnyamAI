package com.example.amnyamai.data.repository

import android.content.Context
import android.util.Log
import com.example.amnyamai.data.local.UserStorage
import com.example.amnyamai.data.model.Meeting
import com.example.amnyamai.data.model.MeetingStatus
import com.example.amnyamai.data.model.Task
import com.example.amnyamai.data.remote.MeetingCreateRequest
import com.example.amnyamai.data.remote.RetrofitClient
import com.example.amnyamai.data.remote.TaskUpdateRequest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "MeetingRepo"

class MeetingRepository(context: Context) {

    private val api = RetrofitClient.apiService
    val userStorage = UserStorage(context)

    companion object {
        // Shared across ViewModel instances so finishMeeting result is available to ResultViewModel
        private val taskCache = mutableMapOf<String, List<Task>>()
    }

    suspend fun createMeeting(title: String): Result<Pair<String, String>> {
        Log.d(TAG, "createMeeting: запуск запроса с заголовком '$title'")
        return try {
            val res = api.createMeeting(MeetingCreateRequest(title))
            Log.d(TAG, "createMeeting: ответ получен, code=${res.code()}")
            if (res.isSuccessful && res.body() != null) {
                val id = res.body()!!.id
                Log.d(TAG, "createMeeting: успех, id=$id")
                Result.success(id to id.take(8).uppercase())
            } else {
                val error = res.errorBody()?.string()
                Log.e(TAG, "createMeeting: ошибка бэкенда code=${res.code()} body=$error")
                Result.failure(Exception("Ошибка создания: ${res.code()}"))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "createMeeting: Таймаут соединения. Проверьте, запущен ли сервер по адресу ${RetrofitClient.BASE_URL}", e)
            Result.failure(Exception("Сервер не отвечает. Проверьте подключение к сети."))
        } catch (e: Exception) {
            Log.e(TAG, "createMeeting: исключение при запросе", e)
            Result.failure(e)
        }
    }

    suspend fun finishMeeting(meetingId: String): Result<List<Task>> {
        return try {
            val res = api.finishMeeting(meetingId)
            if (res.isSuccessful && res.body() != null) {
                val tasks = res.body()!!.map { dto ->
                    Task(
                        id = dto.id,
                        title = dto.title,
                        description = normalizeDescription(dto.description),
                        assignee = dto.speaker_tag,
                        deadline = formatDueAt(dto.due_at),
                        meetingId = meetingId
                    )
                }
                taskCache[meetingId] = tasks
                Result.success(tasks)
            } else Result.failure(Exception("Ошибка анализа: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getCompletedMeeting(meetingId: String): Result<Meeting> {
        return try {
            val meetingRes = api.getMeeting(meetingId)
            if (!meetingRes.isSuccessful || meetingRes.body() == null)
                return Result.failure(Exception("Встреча не найдена: ${meetingRes.code()}"))

            val meetingDto = meetingRes.body()!!

            // Use tasks cached from finishMeeting if available; otherwise fetch
            val tasks = taskCache[meetingId] ?: run {
                val tasksRes = api.listTasks()
                if (!tasksRes.isSuccessful || tasksRes.body() == null)
                    return Result.failure(Exception("Ошибка загрузки задач: ${tasksRes.code()}"))
                tasksRes.body()!!
                    .filter { it.meeting_id == meetingId }
                    .map { dto ->
                        Task(
                            id = dto.id,
                            title = dto.title,
                            description = normalizeDescription(dto.description),
                            assignee = dto.speaker_tag,
                            deadline = formatDueAt(dto.due_at),
                            meetingId = meetingId
                        )
                    }
            }

            Result.success(
                Meeting(
                    id = meetingId,
                    title = meetingDto.title,
                    organizer = "",
                    status = MeetingStatus.DONE,
                    summary = meetingDto.summary ?: "",
                    tasks = tasks
                )
            )
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun confirmAndSyncTask(task: Task): Result<Unit> {
        return try {
            api.confirmTask(task.id)
            api.syncTaskCalendar(task.id)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateTaskTitle(taskId: String, newTitle: String): Result<Unit> {
        return try {
            val res = api.updateTask(taskId, TaskUpdateRequest(title = newTitle))
            if (res.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Ошибка обновления: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getMeetingHistory(): Result<List<Meeting>> {
        return try {
            val res = api.listMeetings()
            if (res.isSuccessful && res.body() != null) {
                val list = res.body()!!.map { dto ->
                    val createdAt = try { OffsetDateTime.parse(dto.created_at).toInstant().toEpochMilli() }
                                    catch (_: Exception) { System.currentTimeMillis() }
                    Meeting(
                        id = dto.id,
                        title = dto.title,
                        organizer = "",
                        status = MeetingStatus.DONE,
                        summary = dto.summary ?: "",
                        createdAt = createdAt
                    )
                }
                Result.success(list)
            } else Result.failure(Exception("Ошибка загрузки истории: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun normalizeDescription(value: String?): String {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return ""
        if (text.equals("null", ignoreCase = true)) return ""
        if (text.equals("none", ignoreCase = true)) return ""
        return text
    }

    private fun formatDueAt(value: String?): String? {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return null
        if (text.equals("null", ignoreCase = true)) return null
        if (text.equals("none", ignoreCase = true)) return null

        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale("ru"))
        val dateOnlyFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

        // Support a few common formats we might get from backend/LLM.
        return try {
            OffsetDateTime.parse(text).atZoneSameInstant(zone).format(formatter)
        } catch (_: Exception) {
            try {
                Instant.parse(text).atZone(zone).format(formatter)
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(text).atZone(zone).format(formatter)
                } catch (_: Exception) {
                    try {
                        LocalDate.parse(text).format(dateOnlyFormatter)
                    } catch (_: Exception) {
                        // If we can't parse, show the raw string (but at least avoid "null").
                        text
                    }
                }
            }
        }
    }
}
