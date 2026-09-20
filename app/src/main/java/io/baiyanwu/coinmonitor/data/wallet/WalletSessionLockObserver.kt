package io.baiyanwu.coinmonitor.data.wallet

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.baiyanwu.coinmonitor.domain.repository.WalletVaultRepository

class WalletSessionLockObserver(
    private val vaultRepository: WalletVaultRepository,
    private val lockTimeoutMillis: Long = DEFAULT_LOCK_TIMEOUT_MILLIS
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private val idleLock = Runnable { vaultRepository.lock() }

    override fun onStop(owner: LifecycleOwner) {
        handler.removeCallbacks(idleLock)
        vaultRepository.lock()
    }

    override fun onStart(owner: LifecycleOwner) {
        recordUserInteraction()
    }

    fun recordUserInteraction() {
        handler.removeCallbacks(idleLock)
        if (vaultRepository.currentState().unlocked) handler.postDelayed(idleLock, lockTimeoutMillis)
    }

    companion object {
        const val DEFAULT_LOCK_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}
