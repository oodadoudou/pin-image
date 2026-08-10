package app.pinimage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppSettings(private val context: Context) {

    private object Keys {
        val instantPin = booleanPreferencesKey("instant_pin")
        val rememberPosition = booleanPreferencesKey("remember_position")
        val rememberSize = booleanPreferencesKey("remember_size")
        val snapToEdge = booleanPreferencesKey("snap_to_edge")
        val autoSaveScreenshot = booleanPreferencesKey("auto_save_screenshot")
        val floatingButton = booleanPreferencesKey("floating_button")
        val defaultOpacity = floatPreferencesKey("default_opacity")
        val lastFrameWidth = intPreferencesKey("last_frame_width")
        val lastFrameHeight = intPreferencesKey("last_frame_height")
    }

    data class Snapshot(
        val instantPin: Boolean,
        val rememberPosition: Boolean,
        val rememberSize: Boolean,
        val snapToEdge: Boolean,
        val autoSaveScreenshot: Boolean,
        val floatingButton: Boolean,
        val defaultOpacity: Float,
        val lastFrameWidth: Int,
        val lastFrameHeight: Int,
    )

    val snapshot: Flow<Snapshot> = context.dataStore.data.map { p ->
        Snapshot(
            instantPin = p[Keys.instantPin] ?: true,
            rememberPosition = p[Keys.rememberPosition] ?: true,
            rememberSize = p[Keys.rememberSize] ?: true,
            snapToEdge = p[Keys.snapToEdge] ?: true,
            autoSaveScreenshot = p[Keys.autoSaveScreenshot] ?: false,
            floatingButton = p[Keys.floatingButton] ?: true,
            defaultOpacity = p[Keys.defaultOpacity] ?: 1f,
            lastFrameWidth = p[Keys.lastFrameWidth] ?: 0,
            lastFrameHeight = p[Keys.lastFrameHeight] ?: 0,
        )
    }

    suspend fun setInstantPin(value: Boolean) = context.dataStore.edit { it[Keys.instantPin] = value }
    suspend fun setRememberPosition(value: Boolean) = context.dataStore.edit { it[Keys.rememberPosition] = value }
    suspend fun setRememberSize(value: Boolean) = context.dataStore.edit { it[Keys.rememberSize] = value }
    suspend fun setSnapToEdge(value: Boolean) = context.dataStore.edit { it[Keys.snapToEdge] = value }
    suspend fun setAutoSaveScreenshot(value: Boolean) = context.dataStore.edit { it[Keys.autoSaveScreenshot] = value }
    suspend fun setFloatingButton(value: Boolean) = context.dataStore.edit { it[Keys.floatingButton] = value }
    suspend fun setDefaultOpacity(value: Float) = context.dataStore.edit { it[Keys.defaultOpacity] = value }
    suspend fun setLastFrameSize(width: Int, height: Int) = context.dataStore.edit {
        it[Keys.lastFrameWidth] = width
        it[Keys.lastFrameHeight] = height
    }
}
