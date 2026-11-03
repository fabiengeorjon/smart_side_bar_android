package com.local.edgebar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.core.SettingsCodec
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.edgeBarDataStore: DataStore<Preferences> by preferencesDataStore(name = "edge_bar_settings")

/**
 * Single source of truth for settings, persisted with Preferences DataStore so they
 * survive service restarts and reboots. The service and the settings screen share one
 * instance (same process), so edits in the app reach the live handle immediately.
 */
class SettingsRepository private constructor(context: Context) {
    private val store = context.applicationContext.edgeBarDataStore

    val settings: Flow<EdgeBarSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> SettingsCodec.decode(prefs.toRawMap()) }
        .distinctUntilChanged()

    suspend fun current(): EdgeBarSettings = settings.first()

    suspend fun update(transform: (EdgeBarSettings) -> EdgeBarSettings) {
        store.edit { prefs ->
            val next = transform(SettingsCodec.decode(prefs.toRawMap())).normalized()
            prefs.writeAll(SettingsCodec.encode(next))
        }
    }

    private fun Preferences.toRawMap(): Map<String, Any?> = asMap().mapKeys { it.key.name }

    private fun MutablePreferences.writeAll(values: Map<String, Any>) {
        for ((key, value) in values) {
            when (value) {
                is Boolean -> this[booleanPreferencesKey(key)] = value
                is Int -> this[intPreferencesKey(key)] = value
                is Float -> this[floatPreferencesKey(key)] = value
                is String -> this[stringPreferencesKey(key)] = value
                is Set<*> -> this[stringSetPreferencesKey(key)] = value.filterIsInstance<String>().toSet()
                else -> error("Unsupported settings value type for $key")
            }
        }
    }

    companion object {
        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
