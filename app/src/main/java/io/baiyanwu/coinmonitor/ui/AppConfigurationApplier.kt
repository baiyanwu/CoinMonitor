package io.baiyanwu.coinmonitor.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.text.TextUtils
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.baiyanwu.coinmonitor.data.repository.DefaultAppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.model.AppLanguage
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.AppThemeMode
import io.baiyanwu.coinmonitor.domain.model.ThemeTemplateId
import java.util.Locale

/**
 * 统一处理应用内语言偏好。
 * 运行时切换时尽量走本地 Context 重组，避免直接触发系统级 Activity 重建。
 */
object AppConfigurationApplier {
    fun readPreferences(context: Context): AppPreferences {
        val sharedPreferences = context.getSharedPreferences(
            DefaultAppPreferencesRepository.Companion.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val themeMode = sharedPreferences.getString(
            DefaultAppPreferencesRepository.Companion.KEY_THEME_MODE,
            AppThemeMode.SYSTEM.name
        )?.let(AppThemeMode::valueOf) ?: AppThemeMode.SYSTEM
        val language = sharedPreferences.getString(
            DefaultAppPreferencesRepository.Companion.KEY_LANGUAGE,
            AppLanguage.SYSTEM.name
        )?.let(AppLanguage::valueOf) ?: AppLanguage.SYSTEM
        val themeTemplate = sharedPreferences.getString(
            DefaultAppPreferencesRepository.Companion.KEY_THEME_TEMPLATE,
            ThemeTemplateId.DEFAULT_MD.name
        )?.let(ThemeTemplateId::valueOf) ?: ThemeTemplateId.DEFAULT_MD
        val refreshIntervalSeconds = sharedPreferences.getInt(
            DefaultAppPreferencesRepository.Companion.KEY_REFRESH_INTERVAL_SECONDS,
            AppPreferences.DEFAULT_REFRESH_INTERVAL_SECONDS
        ).coerceIn(
            AppPreferences.MIN_REFRESH_INTERVAL_SECONDS,
            AppPreferences.MAX_REFRESH_INTERVAL_SECONDS
        )

        return AppPreferences(
            themeMode = themeMode,
            language = language,
            themeTemplate = themeTemplate,
            refreshIntervalSeconds = refreshIntervalSeconds
        )
    }

    fun wrapContext(context: Context): Context {
        return wrapContext(context, readPreferences(context))
    }

    fun wrapContext(
        context: Context,
        preferences: AppPreferences
    ): Context {
        return createLocalizedContext(context, preferences.language)
    }

    fun getString(
        context: Context,
        preferences: AppPreferences,
        @StringRes resId: Int,
        vararg formatArgs: Any
    ): String {
        val localizedContext = wrapContext(context, preferences)
        return if (formatArgs.isEmpty()) {
            localizedContext.getString(resId)
        } else {
            localizedContext.getString(resId, *formatArgs)
        }
    }

    @Composable
    fun ProvideLocalizedResources(
        language: AppLanguage,
        content: @Composable () -> Unit
    ) {
        val baseContext = LocalContext.current
        val baseConfiguration = LocalConfiguration.current
        val localizedContext = remember(baseContext, baseConfiguration, language) {
            createLocalizedContext(baseContext, language)
        }
        val localizedConfiguration = remember(localizedContext) {
            Configuration(localizedContext.resources.configuration)
        }
        val layoutDirection = remember(localizedConfiguration) {
            if (TextUtils.getLayoutDirectionFromLocale(resolveLocale(localizedConfiguration)) == View.LAYOUT_DIRECTION_RTL) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
        }

        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedConfiguration,
            LocalLayoutDirection provides layoutDirection,
            content = content
        )
    }

    private fun createLocalizedContext(
        context: Context,
        language: AppLanguage
    ): Context {
        val targetLocale = when (language) {
            AppLanguage.SYSTEM -> resolveLocale(context.resources.configuration)
            else -> Locale.forLanguageTag(language.languageTag.orEmpty())
        }
        val currentLocale = resolveLocale(context.resources.configuration)
        if (currentLocale == targetLocale) {
            return context
        }

        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(targetLocale)
            setLayoutDirection(targetLocale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(targetLocale))
            }
        }
        val configurationContext = context.createConfigurationContext(configuration)
        return LocalizedResourcesContextWrapper(
            base = context,
            localizedResources = configurationContext.resources
        )
    }

    private fun resolveLocale(configuration: Configuration): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!configuration.locales.isEmpty) {
                configuration.locales[0]
            } else {
                Locale.getDefault()
            }
        } else {
            @Suppress("DEPRECATION")
            configuration.locale ?: Locale.getDefault()
        }
    }
}

/**
 * 只替换资源和主题读取，保留原始 Activity Context 的能力，避免丢失 ActivityResultRegistryOwner。
 */
private class LocalizedResourcesContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources

    override fun getAssets() = localizedResources.assets

    override fun getTheme() = baseContext.theme
}
