package com.example.amnyamai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.amnyamai.data.local.UserStorage
import com.example.amnyamai.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = UserStorage(application)

    private val _registered = MutableStateFlow(storage.isRegistered())
    val registered: StateFlow<Boolean> = _registered

    val currentUser get() = storage.getUser()

    fun register(name: String, lastName: String, login: String) {
        storage.saveUser(User(name.trim(), lastName.trim(), login.trim()))
        _registered.value = true
    }

    fun connectCalendar() {
        storage.setCalendarConnected(true)
    }
}
