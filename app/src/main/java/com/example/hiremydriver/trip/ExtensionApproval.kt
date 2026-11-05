package com.example.hiremydriver.trip

import com.example.hiremydriver.recommendation.RegisteredSchedule

internal enum class ExtensionApprovalIssue { INVALID_REQUEST, AVAILABILITY_UNAVAILABLE, BOOKING_CONFLICT }

/** Driver consent may extend an ongoing service beyond regular hours, but never another reservation. */
internal fun extensionApprovalIssue(
    profile: Map<String, Any?>, before: TripSession, after: TripSession, now: Long
): ExtensionApprovalIssue? {
    if (before.phase != TripPhase.IN_PROGRESS || before.extensionStatus != "PENDING" ||
        before.requestedMinutes <= 0 || before.startedAt == null ||
        after != before.resolveExtension(true) || after.totalMinutes() !in 1..1440)
        return ExtensionApprovalIssue.INVALID_REQUEST
    val start = before.scheduledEnd() ?: return ExtensionApprovalIssue.INVALID_REQUEST
    val end = after.scheduledEnd() ?: return ExtensionApprovalIssue.INVALID_REQUEST
    if (end <= start) return ExtensionApprovalIssue.INVALID_REQUEST
    val slots = profile["busySlots"] as? Map<*, *> ?: return ExtensionApprovalIssue.AVAILABILITY_UNAVAILABLE
    if (slots.entries.any { it.key != before.id &&
            RegisteredSchedule.conflicts(it.value as? Map<*, *>, start, end, now) })
        return ExtensionApprovalIssue.BOOKING_CONFLICT
    return null
}
