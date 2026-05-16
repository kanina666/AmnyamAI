package com.example.amnyamai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.amnyamai.data.local.UserStorage
import com.example.amnyamai.data.model.User
import com.example.amnyamai.data.remote.RetrofitClient
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    data class Error(val message: String) : RegisterState()
}

class RegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = UserStorage(application)

    private val _registered = MutableStateFlow(storage.isRegistered())
    val registered: StateFlow<Boolean> = _registered

    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState> = _registerState

    val currentUser get() = storage.getUser()

    data class GooglePrefill(
        val name: String,
        val lastName: String,
        val email: String,
        val googleId: String,
        val idToken: String?,
        val serverAuthCode: String?
    )

    private val _googlePrefill = MutableStateFlow<GooglePrefill?>(null)
    val googlePrefill: StateFlow<GooglePrefill?> = _googlePrefill

    fun onGoogleSignInSuccess(account: GoogleSignInAccount) {
        _googlePrefill.value = GooglePrefill(
            name = account.givenName ?: "",
            lastName = account.familyName ?: "",
            email = account.email ?: "",
            googleId = account.id ?: "",
            idToken = account.idToken,
            serverAuthCode = account.serverAuthCode
        )
    }

    fun register(name: String, lastName: String, login: String) {
        val google = _googlePrefill.value

        if (google?.serverAuthCode == null) {
            // Нет Google-входа — сохраняем локально, без JWT
            storage.saveUser(
                User(
                    name = name.trim(),
                    lastName = lastName.trim(),
                    login = login.trim(),
                    isCalendarConnected = false,
                    googleId = google?.googleId,
                    googleIdToken = google?.idToken
                )
            )
            _registered.value = true
            return
        }

        _registerState.value = RegisterState.Loading
        viewModelScope.launch {
            try {
                val res = RetrofitClient.apiService.googleCallback(google.serverAuthCode)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    storage.saveToken(body.access_token)
                    RetrofitClient.setToken(body.access_token)
                    storage.saveUser(
                        User(
                            name = name.trim(),
                            lastName = lastName.trim(),
                            login = login.trim(),
                            isCalendarConnected = true,
                            googleId = google.googleId,
                            googleIdToken = google.idToken,
                            googleServerAuthCode = google.serverAuthCode,
                            backendId = body.user.id
                        )
                    )
                    _registerState.value = RegisterState.Idle
                    _registered.value = true
                } else {
                    _registerState.value = RegisterState.Error("Ошибка авторизации: ${res.code()}")
                }
            } catch (e: Exception) {
                _registerState.value = RegisterState.Error(e.message ?: "Ошибка подключения к серверу")
            }
        }
    }
}
