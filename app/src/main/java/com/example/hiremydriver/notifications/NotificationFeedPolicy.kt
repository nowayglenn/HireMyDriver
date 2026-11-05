package com.example.hiremydriver.notifications

/** Chat keeps its own inbox and unread badge, separate from account/booking notifications. */
object NotificationFeedPolicy {
    fun kinds(driver: Boolean) = if (driver) listOf("booking", "review", "approval") else listOf("booking")
    fun includes(kind: String?, driver: Boolean) = kind in kinds(driver)
}
