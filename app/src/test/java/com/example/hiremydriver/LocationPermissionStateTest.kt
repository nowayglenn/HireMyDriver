package com.example.hiremydriver

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPermissionStateTest {
    @Test fun approximatePermissionIsAllowedWithoutPreciseAccess() {
        assertEquals(LocationPermissionState.APPROXIMATE, LocationPermissionState.from(false, true))
    }

    @Test fun precisePermissionTakesPriority() {
        assertEquals(LocationPermissionState.PRECISE, LocationPermissionState.from(true, true))
        assertEquals(LocationPermissionState.PRECISE, LocationPermissionState.from(true, false))
    }

    @Test fun neitherPermissionIsDenied() {
        assertEquals(LocationPermissionState.DENIED, LocationPermissionState.from(false, false))
    }
}
