package com.example.amnyamai.data.local

import android.content.Context
import com.example.amnyamai.data.model.User
import com.google.gson.Gson

class UserStorage(context: Context) {

    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveUser(user: User) =
        prefs.edit().putString("user", gson.toJson(user)).apply()

    fun getUser(): User? = try {
        prefs.getString("user", null)?.let { gson.fromJson(it, User::class.java) }
    } catch (_: Exception) { null }

    fun isRegistered(): Boolean = getUser() != null

    fun setCalendarConnected(connected: Boolean) {
        val user = getUser() ?: return
        saveUser(user.copy(isCalendarConnected = connected))
    }

    fun clear() = prefs.edit().clear().apply()
}
