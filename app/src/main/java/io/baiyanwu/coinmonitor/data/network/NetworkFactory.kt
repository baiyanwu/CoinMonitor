package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

class NetworkFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .build()

    private fun retrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val binanceApi: BinanceApi = retrofit("https://api.binance.com/")
        .create(BinanceApi::class.java)

    val okxApi: OkxApi = retrofit("https://www.okx.com/")
        .create(OkxApi::class.java)

    val alphaApi: BinanceAlphaApi = retrofit("https://www.binance.com/")
        .create(BinanceAlphaApi::class.java)
}
