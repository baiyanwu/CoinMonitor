package io.baiyanwu.coinmonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "overlay_settings")
// Version 8 暂时保留空表结构以兼容已经安装过 v8 的开发包；运行时配置全部由 DataStore 管理。
data class OverlaySettingsEntity(
    @PrimaryKey val id: Int = DEFAULT_ID,
    val enabled: Boolean = false,
    val locked: Boolean = false,
    val opacity: Float = 0.42f,
    val maxItems: Int = 5,
    val leadingDisplayMode: String = "ICON",
    val fontScale: Float = 1f,
    val snapToEdge: Boolean = false,
    val windowX: Int? = null,
    val windowY: Int? = null
) {
    companion object {
        const val DEFAULT_ID: Int = 1
    }
}
