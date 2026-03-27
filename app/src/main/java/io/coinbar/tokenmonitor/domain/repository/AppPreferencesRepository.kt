package io.coinbar.tokenmonitor.domain.repository

import io.coinbar.tokenmonitor.domain.model.AppLanguage
import io.coinbar.tokenmonitor.domain.model.AppPreferences
import io.coinbar.tokenmonitor.domain.model.AppThemeMode
import io.coinbar.tokenmonitor.domain.model.ThemeTemplateId
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>

    fun getPreferences(): AppPreferences

    suspend fun setThemeMode(mode: AppThemeMode)

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setThemeTemplate(templateId: ThemeTemplateId)
}
