package com.example.amnyamai.data.repository

import android.content.Context
import com.example.amnyamai.data.local.UserStorage
import com.example.amnyamai.data.model.Meeting
import com.example.amnyamai.data.model.MeetingStatus
import com.example.amnyamai.data.model.Task
import com.example.amnyamai.data.remote.MeetingCreateRequest
import com.example.amnyamai.data.remote.RetrofitClient
import com.example.amnyamai.data.remote.TaskUpdateRequest
import java.time.OffsetDateTime

class MeetingRepository(context: Context) {

    private val api = RetrofitClient.apiService
    val userStorage = UserStorage(context)

    suspend fun createMeeting(title: String): Result<Pair<String, String>> {
        return try {
            val res = api.createMeeting(MeetingCreateRequest(title))
            if (res.isSuccessful && res.body() != null) {
                val id = res.body()!!.id
                Result.success(id to id.take(8).uppercase())
            } else Result.failure(Exception("Ошибка создания: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun finishMeeting(meetingId: String): Result<List<Task>> {
        return try {
            val res = api.finishMeeting(meetingId)
            if (res.isSuccessful && res.body() != null) {
                val tasks = res.body()!!.map { dto ->
                    Task(
                        id = dto.id,
                        title = dto.title,
                        description = dto.description ?: "",
                        assignee = dto.speaker_tag,
                        deadline = dto.due_at,
                        meetingId = meetingId
                    )
                }
                Result.success(tasks)
            } else Result.failure(Exception("Ошибка анализа: ${res.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getCompletedMeeting(meetingId: String): Result<Meeting> {
        return try {
            val meetingRes = api.getMeeting(meetingId)
            val tasksRes = api.listTasks()

            if (!meetingRes.isSuccessful || meetingRes.body() == null)
                return Result.failure(Exception("Встреча не найдена: ${meetingRes.code()}"))
            if (!tasksRes.isSuccessful || tasksRes.body() == null)
                return Result.failure(Exception("Ошибка загрузки задач: ${tasksRes.code()}"))

            val meetingDto = meetingRes.body()!!
            val tasks = tasksRes.body()!!
                .filter { it.meeting_id == meetingId }
                .map { dto ->
                    Task(
                        id = dto.id,
                        title = dto.title,
                        description = dto.description ?: "",
                        assignee = dto.speaker_tag,
                        deadline = dto.due_at,
                        meetingId = meetingId
                    )
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
}
