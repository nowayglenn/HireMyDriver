package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class BookingInputTest {
    @Test fun pricesKeepTheirDecimalPlaceAndThousandsSeparator() {
        assertEquals("650.00", BookingInput.hourlyRate("₱650.00 / hour")?.toPlainString())
        assertEquals("1250.50", BookingInput.hourlyRate("₱1,250.50 / hour")?.toPlainString())
        assertEquals("650", BookingInput.hourlyRate("₱650 / hour")?.toPlainString())
        assertNull(BookingInput.hourlyRate("Contact driver"))
        assertNull(BookingInput.hourlyRate("₱0 / hour"))
    }

    @Test fun bookingRequiresBothDateAndTimeAndMustBeInTheFuture() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 25, 10, 30, 0) }
        val date = now.timeInMillis
        assertFalse(BookingInput.isFutureBooking(null, 11, 0, date))
        assertFalse(BookingInput.isFutureBooking(date, null, null, date))
        assertFalse(BookingInput.isFutureBooking(date, 10, 0, date))
        assertTrue(BookingInput.isFutureBooking(date, 11, 0, date))
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        assertTrue(BookingInput.isFutureBooking(tomorrow.timeInMillis, 8, 0, date))
    }
}
