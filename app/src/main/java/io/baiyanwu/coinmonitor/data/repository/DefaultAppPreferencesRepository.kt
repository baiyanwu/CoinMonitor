package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.baiyanwu.coinmonitor.domain.model.AppLanguage
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.AppThemeMode
import io.baiyanwu.coinmonitor.domain.model.ThemeTemplateId
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DefaultAppPreferencesRepository(context: Context) : AppPreferencesRepository {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val preferencesFlow = MutableStateFlow(readPreferences())

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        preferencesFlow.value = readPreferences()
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun observePreferences(): Flow<AppPreferences> = preferencesFlow.asStateFlow()

    override fun getPreferences(): AppPreferences = preferencesFlow.value

    override suspend fun setThemeMode(mode: AppThemeMode) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(KEY_THEME_MODE, mode.name)
                .apply()
        }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(KEY_LANGUAGE, language.name)
                .apply()
        }
    }

    override suspend fun setThemeTemplate(templateId: ThemeTemplateId) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(KEY_THEME_TEMPLATE, templateId.name)
                .apply()
        }
    }

    private fun readPreferences(): AppPreferences {
        val themeMode = sharedPreferences.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
            ?.let(AppThemeMode::valueOf)
            ?: AppThemeMode.SYSTEM
        val language = sharedPreferences.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name)
            ?.let(AppLanguage::valueOf)
            ?: AppLanguage.SYSTEM
        val themeTemplate = sharedPreferences.getString(KEY_THEME_TEMPLATE, ThemeTemplateId.DEFAULT_MD.name)
            ?.let(ThemeTemplateId::valueOf)
            ?: ThemeTemplateId.DEFAULT_MD

        return AppPreferences(
            themeMode = themeMode,
            language = language,
            themeTemplate = themeTemplate
        )
    }

    internal companion object {
        const val PREFS_NAME = "token_monitor_preferences"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME_TEMPLATE = "theme_template"
    }
}
