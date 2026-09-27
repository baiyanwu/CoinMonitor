package io.baiyanwu.coinmonitor.data.wallet

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.baiyanwu.coinmonitor.domain.repository.WalletVaultRepository

class WalletSessionLockObserver(
    private val vaultRepository: WalletVaultRepository,
    private val lockTimeoutMillis: Long = DEFAULT_LOCK_TIMEOUT_MILLIS
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private var backgroundedAtMillis: Long? = null
    private val backgroundLock = Runnable {
        if (backgroundedAtMillis != null) {
            backgroundedAtMillis = null
            Log.i(LOG_TAG, "Locking wallet after 30 minutes in background")
            vaultRepository.lock()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAtMillis = SystemClock.elapsedRealtime()
        handler.removeCallbacks(backgroundLock)
        handler.postDelayed(backgroundLock, lockTimeoutMillis)
        Log.i(LOG_TAG, "App entered background; scheduled wallet lock")
    }

    override fun onStart(owner: LifecycleOwner) {
        val backgroundedAt = backgroundedAtMillis
        backgroundedAtMillis = null
        handler.removeCallbacks(backgroundLock)
        if (backgroundedAt != null && SystemClock.elapsedRealtime() - backgroundedAt >= lockTimeoutMillis) {
            Log.i(LOG_TAG, "Locking wallet when returning after 30 minutes in background")
            vaultRepository.lock()
        } else {
            Log.i(LOG_TAG, "App is foreground; preserving wallet unlock state")
        }
    }

    companion object {
        const val DEFAULT_LOCK_TIMEOUT_MILLIS = 30 * 60 * 1000L
        private const val LOG_TAG = "WalletSession"
    }
}
