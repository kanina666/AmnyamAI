package com.example.amnyamai

import android.app.Application
import android.os.Build
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.amnyamai.data.local.UserStorage
import com.example.amnyamai.data.remote.RetrofitClient

class AmnyamApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val token = UserStorage(this).getToken()
        if (token != null) RetrofitClient.setToken(token)

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                    else add(GifDecoder.Factory())
                }
                .build()
        )
    }
}
