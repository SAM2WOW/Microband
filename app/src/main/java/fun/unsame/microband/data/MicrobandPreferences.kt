package com.unsame.microband.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.unsame.microband.band.oobe.BandOobeStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("microband")

class MicrobandPreferences(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val associationId: Flow<Int?> = context.dataStore.data.map { it[ASSOCIATION_ID] }
    val protocolLogging: Flow<Boolean> = context.dataStore.data.map { it[PROTOCOL_LOGGING] ?: false }
    val notificationCategories: Flow<Set<String>> = context.dataStore.data.map {
        it[NOTIFICATION_CATEGORIES] ?: DEFAULT_NOTIFICATION_CATEGORIES
    }
    val oobeStep: Flow<BandOobeStep> = context.dataStore.data.map { preferences ->
        preferences[OOBE_STEP]?.let { runCatching { BandOobeStep.valueOf(it) }.getOrNull() }
            ?: BandOobeStep.Inspect
    }

    suspend fun setAssociationId(value: Int) {
        context.dataStore.edit { it[ASSOCIATION_ID] = value }
    }

    fun setAssociationIdAsync(value: Int) {
        scope.launch { setAssociationId(value) }
    }

    suspend fun setProtocolLogging(enabled: Boolean) {
        context.dataStore.edit { it[PROTOCOL_LOGGING] = enabled }
    }

    suspend fun setNotificationCategory(category: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val categories = (preferences[NOTIFICATION_CATEGORIES] ?: DEFAULT_NOTIFICATION_CATEGORIES).toMutableSet()
            if (enabled) categories += category else categories -= category
            preferences[NOTIFICATION_CATEGORIES] = categories
        }
    }

    suspend fun setOobeStep(step: BandOobeStep) {
        context.dataStore.edit { it[OOBE_STEP] = step.name }
    }

    companion object {
        private val ASSOCIATION_ID = intPreferencesKey("association_id")
        private val PROTOCOL_LOGGING = booleanPreferencesKey("protocol_logging")
        private val OOBE_STEP = stringPreferencesKey("oobe_step")
        private val NOTIFICATION_CATEGORIES = stringSetPreferencesKey("notification_categories")
        private val DEFAULT_NOTIFICATION_CATEGORIES = setOf("calls", "messages", "discord", "calendar")
    }
}
