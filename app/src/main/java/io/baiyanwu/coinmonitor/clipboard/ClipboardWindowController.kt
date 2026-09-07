package io.baiyanwu.coinmonitor.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AppThemeMode
import io.baiyanwu.coinmonitor.ui.AppConfigurationApplier
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import kotlinx.coroutines.*

/** One focusable, transparent window exists only between an explicit tap and dismissal. */
class ClipboardWindowController(
    private val context: Context,
    private val container: AppContainer,
    private val serviceScope: CoroutineScope
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: ClipboardFocusView? = null
    private var owner: ClipboardWindowOwner? = null
    private var session: CoroutineScope? = null
    private var composeView: ComposeView? = null
    private var backDispatcher: OnBackInvokedDispatcher? = null
    private val backCallback = if (Build.VERSION.SDK_INT >= 33) OnBackInvokedCallback { hide() } else null

    init {
        serviceScope.launch {
            container.clipboardSettingsStore.settings.collect { if (!it.enabled) hide() }
        }
    }

    fun show(anchor: View) {
        if (!container.clipboardSettingsStore.settings.value.enabled) return
        hide()
        val localized = AppConfigurationApplier.wrapContext(context, container.appPreferencesRepository.getPreferences())
        val scope = CoroutineScope(serviceScope.coroutineContext + SupervisorJob(serviceScope.coroutineContext[Job]))
        session = scope
        val state = ClipboardPanelState(container.clipboardRepository, scope)
        val coordinates = IntArray(2).also(anchor::getLocationOnScreen)
        val anchorBounds = Rect(coordinates[0], coordinates[1], coordinates[0] + anchor.width, coordinates[1] + anchor.height)
        var read = false
        fun readClipboard() {
            val host = root ?: return
            if (!host.hasWindowFocus()) { state.readFailed(); return }
            try {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                // Only text is read; do not dereference content URIs copied by other apps.
                state.receive(clipboard.primaryClip?.let { clip ->
                    (0 until clip.itemCount.coerceAtMost(10)).mapNotNull { clip.getItemAt(it).text }
                        .joinToString("\n").take(32_768)
                })
            } catch (_: SecurityException) {
                state.readFailed()
            }
        }
        val host = ClipboardFocusView(localized, onBack = ::hide, onFocus = { focused ->
            if (focused && !read) {
                read = true
                readClipboard()
            } else if (!focused && read) {
                val current = root
                current?.post { if (root === current) hide() }
            }
        })
        root = host
        val windowOwner = ClipboardWindowOwner()
        owner = windowOwner
        host.setViewTreeLifecycleOwner(windowOwner)
        host.setViewTreeSavedStateRegistryOwner(windowOwner)
        val content = ComposeView(localized).apply {
            setContent {
                val settings by container.clipboardSettingsStore.settings.collectAsState()
                val preferences by container.appPreferencesRepository.observePreferences()
                    .collectAsState(initial = container.appPreferencesRepository.getPreferences())
                val items by container.watchlistRepository.observeWatchlist().collectAsState(initial = emptyList())
                val dark = when (preferences.themeMode) {
                    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.DARK -> true
                }
                CoinMonitorTheme(darkTheme = dark, themeTemplate = preferences.themeTemplate) {
                    AppConfigurationApplier.ProvideLocalizedResources(preferences.language) {
                        ClipboardPanelView(state, settings, items, anchor = {
                            val origin = IntArray(2).also(host::getLocationOnScreen)
                            intArrayOf(anchorBounds.left - origin[0], anchorBounds.top - origin[1], anchorBounds.bottom - origin[1])
                        }, onDismiss = ::hide, onRetryRead = ::readClipboard,
                            onFloatingChange = { value, existing ->
                                if (existing == null) {
                                    container.clipboardSettingsStore.update { it.copy(addToFloating = value) }
                                } else if (existing.overlaySelected != value && !state.actionBusy) {
                                    state.actionBusy = true
                                    state.actionMessage = null
                                    serviceScope.launch {
                                        try {
                                            container.overlayRepository.toggleItem(existing.id)
                                            container.clipboardSettingsStore.update { it.copy(addToFloating = value) }
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (_: Exception) {
                                            state.actionMessage = R.string.clipboard_action_failed
                                        } finally {
                                            state.actionBusy = false
                                        }
                                    }
                                }
                            },
                            onToggleWatchlist = { match, existing ->
                                if (!state.actionBusy) {
                                    state.actionBusy = true
                                    state.actionMessage = null
                                    serviceScope.launch {
                                        try {
                                            if (existing == null) {
                                                val addToFloating = container.clipboardSettingsStore.settings.value.addToFloating
                                                container.watchlistRepository.add(match.watchItem(addToFloating))
                                            }
                                            else container.watchlistRepository.remove(existing.id)
                                        } catch (error: CancellationException) { throw error
                                        } catch (_: Exception) { state.actionMessage = R.string.clipboard_action_failed
                                        } finally { state.actionBusy = false }
                                    }
                                }
                            }, onOpen = { url ->
                                if (safeClipboardUrl(url) != null) {
                                    hide()
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (_: Exception) {
                                        Toast.makeText(localized, R.string.clipboard_open_failed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }, onSettings = {
                                hide()
                                ClipboardSettingsActivity.start(context)
                            })
                    }
                }
            }
        }
        composeView = content
        host.addView(content, FrameLayout.LayoutParams(-1, -1))
        try {
            windowManager.addView(host, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT
            ).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN })
            windowOwner.resume()
            host.requestFocus()
            if (Build.VERSION.SDK_INT >= 33) {
                backDispatcher = host.findOnBackInvokedDispatcher()
                backCallback?.let { backDispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, it) }
            }
            scope.launch {
                delay(1_500)
                if (!read) state.readFailed()
            }
        } catch (_: WindowManager.BadTokenException) {
            hide()
        } catch (_: SecurityException) {
            hide()
        }
    }

    fun hide() {
        session?.cancel()
        session = null
        if (Build.VERSION.SDK_INT >= 33) backCallback?.let { backDispatcher?.unregisterOnBackInvokedCallback(it) }
        backDispatcher = null
        composeView?.disposeComposition()
        composeView = null
        root?.let { if (it.parent != null) windowManager.removeViewImmediate(it) }
        root = null
        owner?.destroy()
        owner = null
    }
}

private class ClipboardFocusView(context: Context, private val onBack: () -> Unit, private val onFocus: (Boolean) -> Unit) : FrameLayout(context) {
    init { isFocusableInTouchMode = true }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        onFocus(hasWindowFocus)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

private class ClipboardWindowOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = savedState.savedStateRegistry
    init { savedState.performAttach(); savedState.performRestore(null); registry.currentState = Lifecycle.State.CREATED }
    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}
