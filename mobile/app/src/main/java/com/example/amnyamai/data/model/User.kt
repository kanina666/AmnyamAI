package com.example.amnyamai.data.model

data class User(
    val name: String,
    val lastName: String,
    val login: String,
    val isCalendarConnected: Boolean = false
) {
    val fullName get() = "$name $lastName"
}
