package com.example.hiremydriver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationDuplicateAccountTest {
    @Test fun driverRoleConflictUsesTheSameDuplicateAccountHandling() {
        assertTrue(CustomerAuth.isExistingAccount(java.util.concurrent.ExecutionException(
            CustomerAuth.AccountAlreadyExistsException())))
    }

    @Test fun networkErrorsAreNotDuplicateAccounts() {
        assertFalse(CustomerAuth.isExistingAccount(java.io.IOException("Offline")))
        assertFalse(CustomerAuth.isExistingAccount(null))
    }
}
