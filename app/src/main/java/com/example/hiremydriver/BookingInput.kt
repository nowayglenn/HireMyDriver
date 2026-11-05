package com.example.hiremydriver

import java.math.BigDecimal

/** UI price parsing only; actual billing must be calculated by the backend. */
internal object BookingInput {
    fun hourlyRate(label: String): BigDecimal? =
        Regex("[0-9][0-9,]*(?:\\.[0-9]{1,2})?").find(label)?.value
            ?.replace(",", "")?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    fun isFutureBooking(dateMillis: Long?, hour: Int?, minute: Int?, now: Long): Boolean {
        if (dateMillis == null || hour == null || minute == null) return false
        return java.util.Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis > now
    }
}
