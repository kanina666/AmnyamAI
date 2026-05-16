package com.example.amnyamai.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Для эмулятора: http://10.0.2.2:8000/
    // Для реального устройства: http://<IP сервера>:8000/
    const val BASE_URL = "http://192.168.1.218:8000/"

    @Volatile private var token: String? = null

    fun setToken(newToken: String?) { token = newToken }
    fun getToken(): String? = token

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
            token?.let { req.addHeader("Authorization", "Bearer $it") }
            chain.proceed(req.build())
        }
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
