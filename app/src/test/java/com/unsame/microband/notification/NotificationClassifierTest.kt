package com.unsame.microband.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationClassifierTest {
    @Test
    fun recognizesSupportedSources() {
        assertEquals(NotificationCategory.CALLS, NotificationClassifier.classify("com.google.android.dialer", "Phone", "call"))
        assertEquals(NotificationCategory.MESSAGES, NotificationClassifier.classify("com.google.android.apps.messaging", "Messages", "msg"))
        assertEquals(NotificationCategory.DISCORD, NotificationClassifier.classify("com.discord", "Discord", "msg"))
        assertEquals(NotificationCategory.GMAIL, NotificationClassifier.classify("com.google.android.gm", "Gmail", "email"))
        assertEquals(NotificationCategory.CALENDAR, NotificationClassifier.classify("com.google.android.calendar", "Calendar", "event"))
        assertEquals(NotificationCategory.OTHER, NotificationClassifier.classify("example.app", "Example", null))
    }

    @Test
    fun filtersMediaPlaybackEvenWhenNotificationIsNotOngoing() {
        assertTrue(NotificationClassifier.isMediaPlayback("transport", false))
        assertTrue(NotificationClassifier.isMediaPlayback(null, true))
        assertFalse(NotificationClassifier.isMediaPlayback("msg", false))
    }
}
