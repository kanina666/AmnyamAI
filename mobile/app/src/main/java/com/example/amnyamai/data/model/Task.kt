package com.example.amnyamai.data.model

import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val assignee: String,
    val deadline: String? = null,
    val meetingId: String = "",
    val status: TaskStatus = TaskStatus.PENDING
)

enum class TaskStatus { PENDING, ACCEPTED, REJECTED }
