package com.example.amnyamai.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val DEV_URL = "http://10.0.2.2:8000/"
    private const val PROD_URL = "http://192.168.0.102:8000/"

    val BASE_URL: String = (if (isEmulator()) DEV_URL else PROD_URL).also {
        android.util.Log.d("RetrofitClient", "Используется BASE_URL: $it")
    }

    private fun isEmulator(): Boolean {
        return android.os.Build.MODEL.contains("sdk", ignoreCase = true) ||
                android.os.Build.MODEL.contains("Emulator", ignoreCase = true) ||
                android.os.Build.FINGERPRINT.contains("generic")
    }

    @Volatile private var token: String? = null

    fun setToken(newToken: String?) { token = newToken }
    fun getToken(): String? = token

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
            val currentToken = token
            if (currentToken != null) {
                req.addHeader("Authorization", "Bearer $currentToken")
            } else {
                android.util.Log.w("RetrofitClient", "TOKEN IS NULL for ${chain.request().url}")
            }
            chain.proceed(req.build())
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
