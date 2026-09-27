package io.baiyanwu.coinmonitor.ui.wallet.components

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.wallet.WalletBiometricManager
import javax.crypto.Cipher

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

internal fun promptWalletBiometric(
    activity: FragmentActivity,
    cipher: Cipher,
    title: String,
    negativeButtonText: String,
    confirmationRequired: Boolean = true,
    onAuthenticationError: (Int) -> Unit = {},
    onSuccess: (Cipher) -> Unit
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onAuthenticationError(errorCode)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onSuccess)
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText(negativeButtonText)
        .setConfirmationRequired(confirmationRequired)
        .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}

internal data class WalletBiometricAuthorizationState(
    val available: Boolean,
    val showPassword: Boolean,
    val errorMessage: String?,
    val authenticate: () -> Unit,
    val revealPassword: () -> Unit
)

/**
 * Makes biometrics the default authorization path while preserving an explicit password fallback.
 * A new request key automatically starts one biometric prompt; cancellation, prompt errors or an
 * invalid wrapped key reveal the password field instead of retrying in a loop.
 */
@Composable
internal fun rememberWalletBiometricAuthorization(
    requestKey: Any,
    biometricManager: WalletBiometricManager,
    enabled: Boolean,
    onAuthorized: (ByteArray) -> Unit
): WalletBiometricAuthorizationState {
    val activity = LocalContext.current.findFragmentActivity()
    val available = activity != null &&
        biometricManager.isEnabled() &&
        biometricManager.canAuthenticate()
    var showPassword by remember(requestKey, available) { mutableStateOf(!available) }
    var errorMessage by remember(requestKey) { mutableStateOf<String?>(null) }
    var automaticPromptStarted by remember(requestKey, available) { mutableStateOf(false) }
    val latestOnAuthorized by rememberUpdatedState(onAuthorized)
    val promptTitle = stringResource(R.string.wallet_biometric_sign)
    val passwordFallbackLabel = stringResource(R.string.wallet_use_password)
    val unavailableMessage = stringResource(R.string.wallet_biometric_unavailable)

    val authenticate: () -> Unit = authenticate@{
        if (!enabled) return@authenticate
        val hostActivity = activity
        if (hostActivity == null) {
            showPassword = true
            return@authenticate
        }
        errorMessage = null
        runCatching { biometricManager.prepareUnlockCipher() }
            .onSuccess { cipher ->
                promptWalletBiometric(
                    activity = hostActivity,
                    cipher = cipher,
                    title = promptTitle,
                    negativeButtonText = passwordFallbackLabel,
                    onAuthenticationError = { showPassword = true }
                ) { authenticatedCipher ->
                    runCatching { biometricManager.unwrapDerivedKey(authenticatedCipher) }
                        .onSuccess(latestOnAuthorized)
                        .onFailure {
                            biometricManager.clear()
                            errorMessage = unavailableMessage
                            showPassword = true
                        }
                }
            }
            .onFailure {
                biometricManager.clear()
                errorMessage = unavailableMessage
                showPassword = true
            }
    }

    LaunchedEffect(requestKey, available) {
        if (available && !automaticPromptStarted) {
            automaticPromptStarted = true
            authenticate()
        }
    }

    return WalletBiometricAuthorizationState(
        available = available,
        showPassword = showPassword,
        errorMessage = errorMessage,
        authenticate = authenticate,
        revealPassword = { showPassword = true }
    )
}
