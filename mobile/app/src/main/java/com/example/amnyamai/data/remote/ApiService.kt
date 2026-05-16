package com.example.amnyamai.data.remote

import com.example.amnyamai.data.model.Task
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {

    @POST("api/meeting/create")
    suspend fun createMeeting(@Body request: CreateMeetingRequest): Response<CreateMeetingResponse>

    @POST("api/meeting/{id}/join")
    suspend fun joinMeeting(
        @Path("id") id: String,
        @Body request: JoinRequest
    ): Response<MeetingInfoResponse>

    @Multipart
    @POST("api/meeting/{id}/upload")
    suspend fun uploadAudio(
        @Path("id") id: String,
        @Part audio: MultipartBody.Part
    ): Response<SimpleResponse>

    @GET("api/meeting/{id}/result")
    suspend fun getMeetingResult(@Path("id") id: String): Response<MeetingResultResponse>

    @POST("api/tasks/save")
    suspend fun saveTask(@Body request: SaveTaskRequest): Response<SimpleResponse>

    @GET("api/meetings")
    suspend fun getMeetings(): Response<List<MeetingHistoryItem>>
}

// ─── DTOs ───────────────────────────────────────────────────────────────────

data class CreateMeetingRequest(val title: String, val organizerLogin: String)

data class CreateMeetingResponse(
    val meetingId: String,
    val code: String           // короткий код для участников
)

data class JoinRequest(val login: String, val name: String)

data class MeetingInfoResponse(
    val meetingId: String,
    val title: String,
    val organizer: String,
    val participants: List<ParticipantDto>
)

data class ParticipantDto(val login: String, val name: String)

data class MeetingResultResponse(
    val meetingId: String,
    val status: String,        // "processing" | "done"
    val summary: String = "",
    val tasks: List<TaskDto> = emptyList()
)

data class TaskDto(
    val id: String,
    val title: String,
    val description: String = "",
    val assignee: String,
    val deadline: String? = null
)

data class SaveTaskRequest(
    val taskId: String,
    val meetingId: String,
    val login: String
)

data class MeetingHistoryItem(
    val meetingId: String,
    val title: String,
    val createdAt: Long,
    val participantCount: Int,
    val summary: String = ""
)

data class SimpleResponse(val success: Boolean = true, val message: String = "")
