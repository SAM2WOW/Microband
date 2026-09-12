package com.unsame.microband.notification

import java.time.Instant

data class BandPhoneNotification(
    val sourcePackage: String,
    val sourceLabel: String,
    val title: String?,
    val body: String?,
    val timestamp: Instant,
)

enum class NotificationCategory(val preferenceKey: String, val label: String) {
    CALLS("calls", "Phone calls"),
    MESSAGES("messages", "Messages"),
    DISCORD("discord", "Discord"),
    GMAIL("gmail", "Gmail"),
    CALENDAR("calendar", "Calendar"),
    OTHER("other", "Everything else"),
}

object NotificationClassifier {
    fun classify(packageName: String, sourceLabel: String, androidCategory: String?): NotificationCategory {
        val packageLower = packageName.lowercase()
        val labelLower = sourceLabel.lowercase()
        return when {
            androidCategory == "call" || "dialer" in packageLower || labelLower == "phone" -> NotificationCategory.CALLS
            packageLower == "com.discord" || "discord" in packageLower -> NotificationCategory.DISCORD
            packageLower == "com.google.android.gm" || labelLower == "gmail" -> NotificationCategory.GMAIL
            "calendar" in packageLower || "calendar" in labelLower -> NotificationCategory.CALENDAR
            androidCategory == "msg" || "messag" in packageLower || "messag" in labelLower ||
                "whatsapp" in packageLower || "telegram" in packageLower || "signal" in packageLower -> NotificationCategory.MESSAGES
            else -> NotificationCategory.OTHER
        }
    }
}
