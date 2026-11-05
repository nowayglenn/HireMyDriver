package com.example.hiremydriver.trip

data class TripPlace(val label: String, val latitude: Double, val longitude: Double) {
    init {
        require(label.isNotBlank())
        require(latitude.isFinite() && latitude in -85.0..85.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

enum class TripPhase { REQUESTED, ACCEPTED, ON_THE_WAY, ARRIVED, IN_PROGRESS, COMPLETED, DECLINED, CANCELLED }

/** Booking state shared by the Firestore repository and the account-scoped device cache. */
data class TripSession(
    val id: String,
    val driverName: String,
    val customerName: String,
    val pickupLabel: String,
    val destinationLabel: String,
    val pickup: TripPlace? = null,
    val destinations: List<TripPlace> = emptyList(),
    val durationHours: Int = 4,
    val vehicle: String = "Not specified",
    val notes: String = "",
    val fee: String = "",
    val date: String = "",
    val time: String = "",
    val phase: TripPhase = TripPhase.REQUESTED,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val pendingDestination: TripPlace? = null,
    val requestStatus: String = "",
    val reachedStops: Int = 0,
    val driverLocation: TripPlace? = null,
    val locationAt: Long? = null,
    val localBooking: Boolean = true,
    val driverImage: Int = 0,
    val driverRating: String = "",
    val customerUid: String = "",
    val driverUid: String = "",
    val synced: Boolean = false,
    val extensionMinutes: Int = 0,
    val requestedMinutes: Int = 0,
    val extensionStatus: String = "",
    val extensionSequence: Int = 0,
    val hourlyRate: Int = 0,
    val cancellationReason: String = ""
) {
    init { require(durationHours in 1..24); require(reachedStops in 0..destinations.size) }
    fun decline(reason: String = "") = if (phase == TripPhase.REQUESTED)
        copy(phase = TripPhase.DECLINED, requestStatus = if (reason.isBlank()) "Booking declined" else "Booking declined: $reason") else this
    fun accept() = if (phase == TripPhase.REQUESTED) copy(phase = TripPhase.ACCEPTED) else this
    fun canCancel() = phase in setOf(TripPhase.REQUESTED, TripPhase.ACCEPTED, TripPhase.ON_THE_WAY, TripPhase.ARRIVED)
    fun cancel(reason: String) = if (canCancel() && reason.trim().length in 1..1000)
        copy(phase = TripPhase.CANCELLED, cancellationReason = reason.trim(), requestStatus = "Cancelled: ${reason.trim()}") else this
    fun cancelPending(reason: String) = if (phase == TripPhase.REQUESTED) cancel(reason) else this
    fun requestExtension(minutes: Int) = if (phase == TripPhase.IN_PROGRESS && extensionStatus != "PENDING" &&
        minutes in 1..1440 && totalMinutes() + minutes <= 1440)
        copy(requestedMinutes = minutes, extensionStatus = "PENDING", extensionSequence = extensionSequence + 1) else this
    fun resolveExtension(approved: Boolean) = if (phase == TripPhase.IN_PROGRESS && extensionStatus == "PENDING")
        copy(extensionMinutes = extensionMinutes + if (approved) requestedMinutes else 0,
            extensionStatus = if (approved) "APPROVED" else "DECLINED") else this
    fun totalMinutes() = durationHours * 60 + extensionMinutes
    fun scheduledEnd() = startedAt?.let { it + totalMinutes() * 60000L }
    fun rate() = hourlyRate.takeIf { it > 0 }?.toDouble() ?: ((fee.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0) / durationHours)
    fun extensionFee() = rate() * extensionMinutes / 60.0
    fun totalAmount() = rate() * durationHours + extensionFee()
    fun headToPickup() = if (phase == TripPhase.ACCEPTED) copy(phase = TripPhase.ON_THE_WAY) else this
    fun arrive() = if (phase == TripPhase.ON_THE_WAY) copy(phase = TripPhase.ARRIVED) else this
    fun start(now: Long, customerOnBoard: Boolean): TripSession =
        if (phase == TripPhase.ARRIVED && customerOnBoard && startedAt == null)
            copy(phase = TripPhase.IN_PROGRESS, startedAt = now) else this
    fun complete(now: Long) = if (phase == TripPhase.IN_PROGRESS)
        copy(phase = TripPhase.COMPLETED, completedAt = now, pendingDestination = null) else this
    fun requestStop(place: TripPlace) = if (phase == TripPhase.IN_PROGRESS && pendingDestination == null)
        copy(pendingDestination = place, requestStatus = "Waiting for driver confirmation") else this
    fun resolveStop(accepted: Boolean): TripSession {
        val pending = pendingDestination ?: return this
        if (phase != TripPhase.IN_PROGRESS) return this
        return copy(destinations = if (accepted) destinations + pending else destinations,
            pendingDestination = null, requestStatus = if (accepted) "Destination accepted" else "Destination declined")
    }
    fun reachStop() = if (phase == TripPhase.IN_PROGRESS && reachedStops < destinations.size)
        copy(reachedStops = reachedStops + 1) else this
    fun hasReachedLastDestination() = destinations.isNotEmpty() && reachedStops == destinations.size
    fun completeAtLastDestination(now: Long) =
        if (hasReachedLastDestination()) complete(now) else this
    fun remainingMillis(now: Long): Long {
        val total = totalMinutes() * 60_000L
        val start = startedAt ?: return total
        return (total - ((completedAt ?: now) - start).coerceAtLeast(0)).coerceAtLeast(0)
    }
    fun nextDestination(): TripPlace? = destinations.getOrNull(reachedStops)
    fun elapsedMillis(now: Long): Long? {
        val start = startedAt?.takeIf { it > 0 } ?: return null
        val end = when (phase) {
            TripPhase.IN_PROGRESS -> now
            TripPhase.COMPLETED -> completedAt ?: return null
            else -> return null
        }
        return (end - start).coerceAtLeast(0)
    }
}
