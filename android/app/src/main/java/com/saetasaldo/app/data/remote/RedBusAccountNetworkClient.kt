package com.saetasaldo.app.data.remote

import com.saetasaldo.app.data.remote.api.RedBusAccountApiService
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RedBusAccountNetworkClient {
    private const val BASE_URL = "https://salta.miredbus.com.ar/"
    const val HOST = "salta.miredbus.com.ar"

    fun create(cookieJar: CookieJar): RedBusAccountApiService {
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RedBusAccountApiService::class.java)
    }
}
