package io.coinbar.tokenmonitor

import android.app.Application
import android.content.Context
import io.coinbar.tokenmonitor.data.AppContainer

class TokenMonitorApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

fun Context.appContainer(): AppContainer {
    return (applicationContext as TokenMonitorApp).container
}
