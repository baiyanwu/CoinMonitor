package io.coinbar.tokenmonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "overlay_settings")
data class OverlaySettingsEntity(
    @PrimaryKey val id: Int = DEFAULT_ID,
    val enabled: Boolean = false,
    val locked: Boolean = false,
    val opacity: Float = 0.42f,
    val maxItems: Int = 5,
    val leadingDisplayMode: String = "PAIR_NAME",
    val windowX: Int? = null,
    val windowY: Int? = null
) {
    companion object {
        const val DEFAULT_ID: Int = 1
    }
}
