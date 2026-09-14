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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("microband")

class MicrobandPreferences(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val associationId: Flow<Int?> = context.dataStore.data.map { it[ASSOCIATION_ID] }
    val protocolLogging: Flow<Boolean> = context.dataStore.data.map { it[PROTOCOL_LOGGING] ?: false }
    val allNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[ALL_NOTIFICATIONS_ENABLED] ?: true }
    val maskNotificationsWhenLocked: Flow<Boolean> = context.dataStore.data.map { it[MASK_NOTIFICATIONS_WHEN_LOCKED] ?: true }
    val disabledNotificationPackages: Flow<Set<String>> = context.dataStore.data.map { it[DISABLED_NOTIFICATION_PACKAGES] ?: emptySet() }
    val notificationActivity: Flow<Map<String, NotificationAppActivity>> = context.dataStore.data.map {
        decodeNotificationActivity(it[NOTIFICATION_ACTIVITY].orEmpty())
    }
    val themeAccent: Flow<Int> = context.dataStore.data.map { it[THEME_ACCENT] ?: DEFAULT_THEME_ACCENT }
    val geminiAssistantEnabled: Flow<Boolean> = context.dataStore.data.map { it[GEMINI_ASSISTANT_ENABLED] ?: false }
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

    suspend fun setAllNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ALL_NOTIFICATIONS_ENABLED] = enabled
            if (enabled) preferences[DISABLED_NOTIFICATION_PACKAGES] = emptySet()
        }
    }

    suspend fun setMaskNotificationsWhenLocked(enabled: Boolean) {
        context.dataStore.edit { it[MASK_NOTIFICATIONS_WHEN_LOCKED] = enabled }
    }

    suspend fun setNotificationPackageEnabled(packageName: String, enabled: Boolean, knownPackages: Set<String>) {
        context.dataStore.edit { preferences ->
            val disabled = (preferences[DISABLED_NOTIFICATION_PACKAGES] ?: emptySet()).toMutableSet()
            if (enabled) {
                if (preferences[ALL_NOTIFICATIONS_ENABLED] == false) {
                    preferences[ALL_NOTIFICATIONS_ENABLED] = true
                    disabled += knownPackages
                }
                disabled -= packageName
            } else {
                disabled += packageName
            }
            preferences[DISABLED_NOTIFICATION_PACKAGES] = disabled
        }
    }

    suspend fun isNotificationPackageEnabled(packageName: String): Boolean {
        val preferences = context.dataStore.data.first()
        return (preferences[ALL_NOTIFICATIONS_ENABLED] ?: true) &&
            packageName !in (preferences[DISABLED_NOTIFICATION_PACKAGES] ?: emptySet())
    }

    suspend fun recordNotificationApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val activity = decodeNotificationActivity(preferences[NOTIFICATION_ACTIVITY].orEmpty()).toMutableMap()
            val previous = activity[packageName]
            activity[packageName] = NotificationAppActivity((previous?.count ?: 0) + 1, System.currentTimeMillis())
            preferences[NOTIFICATION_ACTIVITY] = activity.entries
                .sortedByDescending { it.value.lastSeenMillis }
                .take(200)
                .mapTo(mutableSetOf()) { (name, value) -> "$name\t${value.count}\t${value.lastSeenMillis}" }
        }
    }

    suspend fun setThemeAccent(accent: Int) {
        context.dataStore.edit { it[THEME_ACCENT] = accent }
    }

    suspend fun setGeminiAssistantEnabled(enabled: Boolean) {
        context.dataStore.edit { it[GEMINI_ASSISTANT_ENABLED] = enabled }
    }

    suspend fun setOobeStep(step: BandOobeStep) {
        context.dataStore.edit { it[OOBE_STEP] = step.name }
    }

    companion object {
        private val ASSOCIATION_ID = intPreferencesKey("association_id")
        private val PROTOCOL_LOGGING = booleanPreferencesKey("protocol_logging")
        private val OOBE_STEP = stringPreferencesKey("oobe_step")
        private val ALL_NOTIFICATIONS_ENABLED = booleanPreferencesKey("all_notifications_enabled")
        private val MASK_NOTIFICATIONS_WHEN_LOCKED = booleanPreferencesKey("mask_notifications_when_locked")
        private val DISABLED_NOTIFICATION_PACKAGES = stringSetPreferencesKey("disabled_notification_packages")
        private val NOTIFICATION_ACTIVITY = stringSetPreferencesKey("notification_activity")
        private val THEME_ACCENT = intPreferencesKey("theme_accent")
        private val GEMINI_ASSISTANT_ENABLED = booleanPreferencesKey("gemini_assistant_enabled")
        private const val DEFAULT_THEME_ACCENT = 0xFF0078D7.toInt()

        private fun decodeNotificationActivity(values: Set<String>): Map<String, NotificationAppActivity> =
            values.mapNotNull { value ->
                val parts = value.split('\t')
                if (parts.size != 3) null else {
                    val count = parts[1].toIntOrNull()
                    val lastSeen = parts[2].toLongOrNull()
                    if (count == null || lastSeen == null) null else parts[0] to NotificationAppActivity(count, lastSeen)
                }
            }.toMap()
    }
}

data class NotificationAppActivity(val count: Int, val lastSeenMillis: Long)
