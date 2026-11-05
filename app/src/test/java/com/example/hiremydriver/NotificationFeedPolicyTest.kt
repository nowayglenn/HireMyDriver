package com.example.hiremydriver

import com.example.hiremydriver.notifications.NotificationFeedPolicy
import org.junit.Assert.*
import org.junit.Test

class NotificationFeedPolicyTest {
    @Test fun chatAndUnknownEventsNeverEnterEitherFeed() {
        for (driver in listOf(false, true)) {
            assertFalse(NotificationFeedPolicy.includes("message", driver))
            assertFalse(NotificationFeedPolicy.includes(null, driver))
            assertFalse(NotificationFeedPolicy.includes("unknown", driver))
            assertTrue(NotificationFeedPolicy.includes("booking", driver))
        }
    }
    @Test fun ReviewsAndApprovalsBelongToDriverFeed() {
        for (kind in listOf("review", "approval")) {
            assertTrue(NotificationFeedPolicy.includes(kind, true))
            assertFalse(NotificationFeedPolicy.includes(kind, false))
        }
    }
}
