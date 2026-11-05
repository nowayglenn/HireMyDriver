package com.example.hiremydriver.messaging

import com.example.hiremydriver.trip.TripSession
import org.junit.Assert.*
import org.junit.Test

class BookingChatParticipantsTest {
    private val booking = TripSession("booking", "Driver", "Customer", "A", "B",
        customerUid = "customer", driverUid = "driver", synced = true)

    @Test fun confirmedParticipantsCanUseCachedRoutingForEitherRole() {
        assertEquals("customer" to "driver", bookingChatParticipants(booking, "driver", true))
        assertEquals("customer" to "driver", bookingChatParticipants(booking, "customer", false))
    }

    @Test fun accountOrRoleChangesCannotReuseAnotherBookingsIdentity() {
        assertNull(bookingChatParticipants(booking, "stranger", true))
        assertNull(bookingChatParticipants(booking, "customer", true))
        assertNull(bookingChatParticipants(booking, "driver", false))
        assertNull(bookingChatParticipants(booking, "", false))
    }

    @Test fun localOnlyMissingAndIncompleteBookingsUseTheAuthoritativeFallback() {
        assertNull(bookingChatParticipants(null, "driver", true))
        assertNull(bookingChatParticipants(booking.copy(synced = false), "driver", true))
        assertNull(bookingChatParticipants(booking.copy(customerUid = ""), "driver", true))
        assertNull(bookingChatParticipants(booking.copy(customerUid = "driver"), "driver", true))
    }
}
