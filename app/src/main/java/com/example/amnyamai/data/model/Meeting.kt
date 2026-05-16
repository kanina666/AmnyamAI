package com.example.amnyamai.data.model

data class Meeting(
    val id: String,
    val code: String,          // короткий код для присоединения, напр. "ABC-123"
    val title: String,
    val organizer: String,
    val participants: List<Participant> = emptyList(),
    val status: MeetingStatus = MeetingStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val summary: String = "",
    val tasks: List<Task> = emptyList()
)

data class Participant(
    val id: String,
    val name: String,
    val login: String
)

enum class MeetingStatus { ACTIVE, PROCESSING, DONE }
