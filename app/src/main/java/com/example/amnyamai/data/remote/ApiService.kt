package com.example.amnyamai.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // ─── Auth ────────────────────────────────────────────────────────────────

    @POST("api/v1/auth/google/callback")
    suspend fun googleCallback(@Query("code") code: String): Response<TokenResponse>

    // ─── Meetings ─────────────────────────────────────────────────────────────

    @POST("api/v1/meetings")
    suspend fun createMeeting(@Body request: MeetingCreateRequest): Response<MeetingReadDto>

    @GET("api/v1/meetings")
    suspend fun listMeetings(): Response<List<MeetingReadDto>>

    @GET("api/v1/meetings/{id}")
    suspend fun getMeeting(@Path("id") id: String): Response<MeetingReadDto>

    @POST("api/v1/meetings/{id}/finish")
    suspend fun finishMeeting(@Path("id") id: String): Response<List<TaskReadDto>>

    // ─── Tasks ────────────────────────────────────────────────────────────────

    @GET("api/v1/tasks")
    suspend fun listTasks(): Response<List<TaskReadDto>>

    @PATCH("api/v1/tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body request: TaskUpdateRequest): Response<TaskReadDto>

    @POST("api/v1/tasks/{id}/confirm")
    suspend fun confirmTask(@Path("id") id: String): Response<TaskReadDto>

    @POST("api/v1/tasks/{id}/sync-calendar")
    suspend fun syncTaskCalendar(@Path("id") id: String): Response<CalendarSyncDto>
}

// ─── DTOs ────────────────────────────────────────────────────────────────────

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val user: UserDto
)

data class UserDto(
    val id: String,
    val email: String,
    val name: String?
)

data class MeetingCreateRequest(val title: String)

data class MeetingReadDto(
    val id: String,
    val title: String,
    val status: String,
    val summary: String?,
    val created_at: String,
    val started_at: String?,
    val ended_at: String?
)

data class TaskReadDto(
    val id: String,
    val meeting_id: String,
    val speaker_tag: String,
    val title: String,
    val description: String?,
    val due_at: String?,
    val status: String,
    val calendar_event_id: String?
)

data class CalendarSyncDto(
    val task_id: String,
    val calendar_event_id: String,
    val status: String
)

data class TaskUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val due_at: String? = null
)
