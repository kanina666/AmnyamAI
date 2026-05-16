package com.example.amnyamai.data.model

data class User(
    val name: String,
    val lastName: String,
    val login: String,
    val isCalendarConnected: Boolean = false,
    val googleId: String? = null,
    val googleIdToken: String? = null,
    val googleServerAuthCode: String? = null,
    val backendId: String? = null
) {
    val fullName get() = "$name $lastName"
}
