package com.example.amnyamai

import android.app.Application
import com.example.amnyamai.data.local.UserStorage
import com.example.amnyamai.data.remote.RetrofitClient

class AmnyamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val token = UserStorage(this).getToken()
        if (token != null) RetrofitClient.setToken(token)
    }
}
