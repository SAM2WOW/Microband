package com.unsame.microband.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.unsame.microband.MicrobandApplication
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Normalizes and forwards allowed phone notifications without persisting their private content.
 */
class MicrobandNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentFingerprints = LinkedHashMap<Int, Long>()
    private val recentSends = ArrayDeque<Long>()
    private val activeCalls = ConcurrentHashMap<String, BandPhoneNotification>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch {
            val preferences = (application as MicrobandApplication).preferences
            activeNotifications.orEmpty()
                .asSequence()
                .filter { it.packageName != packageName }
                .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .map { it.packageName }
                .distinct()
                .forEach { preferences.recordNotificationApp(it) }
        }
    }

    override fun onNotificationPosted(notification: StatusBarNotification?) {
        val posted = notification ?: return
        if (posted.packageName == packageName || posted.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (NotificationClassifier.isMediaPlayback(
                posted.notification.category,
                posted.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
            )
        ) return
        val normalized = normalize(posted)
        posted.notification.actions.orEmpty().firstOrNull { action ->
            !action.remoteInputs.isNullOrEmpty() && action.actionIntent != null
        }?.let { action ->
            BandReplyRegistry.register(posted.key, action.actionIntent, action.remoteInputs)
        }
        val category = NotificationClassifier.classify(
            posted.packageName,
            normalized.sourceLabel,
            posted.notification.category,
        )
        if (posted.isOngoing && category != NotificationCategory.CALLS) return
        val kind = deliveryKind(posted, normalized, category)
        if (kind == BandNotificationKind.INCOMING_CALL || kind == BandNotificationKind.ANSWERED_CALL) {
            activeCalls[posted.key] = normalized
        } else if (category == NotificationCategory.CALLS) {
            activeCalls.remove(posted.key)
        }

        scope.launch {
            val app = application as MicrobandApplication
            app.preferences.recordNotificationApp(normalized.sourcePackage)
            if (!app.preferences.isNotificationPackageEnabled(normalized.sourcePackage)) return@launch
            if (!shouldSend(normalized, kind)) return@launch

            val association = runCatching { app.associationManager.currentAssociation() }.getOrNull() ?: return@launch
            app.connectionManager.forwardNotification(normalized, kind, association.device)
        }
    }

    override fun onNotificationRemoved(notification: StatusBarNotification?) {
        val posted = notification ?: return
        BandReplyRegistry.remove(posted.key)
        val call = activeCalls.remove(posted.key) ?: return
        scope.launch {
            val app = application as MicrobandApplication
            if (!app.preferences.isNotificationPackageEnabled(call.sourcePackage)) return@launch
            val association = runCatching { app.associationManager.currentAssociation() }.getOrNull() ?: return@launch
            app.connectionManager.forwardNotification(
                call.copy(timestamp = Instant.now()),
                BandNotificationKind.HANGUP_CALL,
                association.device,
            )
        }
    }

    private fun normalize(posted: StatusBarNotification): BandPhoneNotification {
        val extras = posted.notification.extras
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(posted.packageName, 0)).toString()
        }.getOrDefault(posted.packageName)
        return BandPhoneNotification(
            notificationKey = posted.key,
            sourcePackage = posted.packageName,
            sourceLabel = label,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(80),
            body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.take(320),
            timestamp = Instant.ofEpochMilli(posted.postTime),
        )
    }

    private fun deliveryKind(
        posted: StatusBarNotification,
        notification: BandPhoneNotification,
        category: NotificationCategory,
    ): BandNotificationKind {
        if (category != NotificationCategory.CALLS) return BandNotificationKind.MESSAGE
        val text = listOfNotNull(notification.title, notification.body).joinToString(" ").lowercase()
        return when {
            "voicemail" in text -> BandNotificationKind.VOICEMAIL
            "missed" in text -> BandNotificationKind.MISSED_CALL
            posted.isOngoing && ("incoming" in text || "ringing" in text || posted.notification.actions.orEmpty().any {
                val action = it.title?.toString()?.lowercase().orEmpty()
                "answer" in action || "accept" in action
            }) -> BandNotificationKind.INCOMING_CALL
            posted.isOngoing -> BandNotificationKind.ANSWERED_CALL
            else -> BandNotificationKind.MISSED_CALL
        }
    }

    @Synchronized
    private fun shouldSend(notification: BandPhoneNotification, kind: BandNotificationKind): Boolean {
        val now = System.currentTimeMillis()
        recentFingerprints.entries.removeAll { now - it.value > DUPLICATE_WINDOW_MILLIS }
        while (recentSends.isNotEmpty() && now - recentSends.first() > RATE_WINDOW_MILLIS) recentSends.removeFirst()
        val fingerprint = listOf(notification.sourcePackage, notification.title, notification.body, kind).hashCode()
        if (fingerprint in recentFingerprints || recentSends.size >= MAX_PER_MINUTE) return false
        recentFingerprints[fingerprint] = now
        recentSends.addLast(now)
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val DUPLICATE_WINDOW_MILLIS = 30_000L
        private const val RATE_WINDOW_MILLIS = 60_000L
        private const val MAX_PER_MINUTE = 8
    }
}
