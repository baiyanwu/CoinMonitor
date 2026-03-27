package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.AppLanguage
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.AppThemeMode
import io.baiyanwu.coinmonitor.domain.model.RefreshIntervalMode
import io.baiyanwu.coinmonitor.domain.model.ThemeTemplateId
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>

    fun getPreferences(): AppPreferences

    suspend fun setThemeMode(mode: AppThemeMode)

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setThemeTemplate(templateId: ThemeTemplateId)

    suspend fun setRefreshIntervalSeconds(seconds: Int)

    suspend fun setRefreshIntervalMode(mode: RefreshIntervalMode)
}
