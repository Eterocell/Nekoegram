package com.eterocell.nekoegram.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

object NekoeStore {
    private val Context.nekoegramStore: DataStore<Preferences> by preferencesDataStore(name = "nekoegram_store")

    private val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
    private val UPDATE_SCHEDULE_TIMESTAMP = longPreferencesKey("update_schedule_timestamp")
    private val AUTO_OTA = booleanPreferencesKey("auto_ota")

    fun getLastUpdateCheckTime(context: Context): Long {
        val flow = context.nekoegramStore.data.map { preferences ->
            preferences[LAST_UPDATE_CHECK_TIME] ?: 0L
        }
        return runBlocking { flow.lastOrNull() ?: 0L }
    }

    fun setLastUpdateCheckTime(time: Long, context: Context) {
        runBlocking {
            context.nekoegramStore.edit { preferences ->
                preferences[LAST_UPDATE_CHECK_TIME] = time
            }
        }
    }

    fun getUpdateScheduleTimestamp(context: Context): Long {
        val flow = context.nekoegramStore.data.map { preferences ->
            preferences[UPDATE_SCHEDULE_TIMESTAMP] ?: 0L
        }
        return runBlocking { flow.lastOrNull() ?: 0L }
    }

    fun setUpdateScheduleTimestamp(timestamp: Long, context: Context) {
        runBlocking {
            context.nekoegramStore.edit { preferences ->
                preferences[UPDATE_SCHEDULE_TIMESTAMP] = timestamp
            }
        }
    }

    fun getAutoOTA(context: Context): Boolean {
        val flow = context.nekoegramStore.data.map { preferences ->
            preferences[AUTO_OTA] ?: false
        }
        return runBlocking { flow.lastOrNull() ?: false }
    }

    fun toggleAutoOTA(context: Context) {
        val before = getAutoOTA(context)
        runBlocking {
            context.nekoegramStore.edit { preferences ->
                preferences[AUTO_OTA] = before
            }
        }
    }
}
