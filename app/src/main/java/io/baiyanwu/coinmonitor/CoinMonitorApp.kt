package io.baiyanwu.coinmonitor

import android.app.Application
import android.content.Context
import io.baiyanwu.coinmonitor.data.AppContainer

class CoinMonitorApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

fun Context.appContainer(): AppContainer {
    return (applicationContext as CoinMonitorApp).container
}
