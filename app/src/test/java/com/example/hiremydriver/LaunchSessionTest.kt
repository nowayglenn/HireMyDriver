package com.example.hiremydriver

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchSessionTest {
    @Test fun lastSuccessfulRoleWinsWhenBothFirebaseSessionsExist() {
        assertEquals(LaunchRole.CUSTOMER, LaunchSession.choose("CUSTOMER", "c", "c", "d", true))
        assertEquals(LaunchRole.DRIVER, LaunchSession.choose("DRIVER", "d", "c", "d", true))
    }
    @Test fun logoutNeverFallsBackToAnOlderRole() {
        assertEquals(LaunchRole.NONE, LaunchSession.choose("NONE", "", "c", "d", true))
    }
    @Test fun missingOrChangedFirebaseIdentityDoesNotRestoreASession() {
        assertEquals(LaunchRole.NONE, LaunchSession.choose("CUSTOMER", "old", "new", "d", true))
        assertEquals(LaunchRole.NONE, LaunchSession.choose("DRIVER", "d", "c", null, true))
    }
    @Test fun upgradeRecognizesCompletedSessionsButNotPartialDriverRegistration() {
        assertEquals(LaunchRole.CUSTOMER, LaunchSession.choose(null, null, "c", null, false))
        assertEquals(LaunchRole.DRIVER, LaunchSession.choose(null, null, null, "d", true))
        assertEquals(LaunchRole.NONE, LaunchSession.choose(null, null, null, "d", false))
        assertEquals(LaunchRole.NONE, LaunchSession.choose(null, null, null, null, false))
    }
}
