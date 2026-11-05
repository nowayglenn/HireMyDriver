package com.example.hiremydriver

import com.google.firebase.auth.FirebaseAuthException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Firebase exceptions require Android TextUtils; no network or account creation. */
class RegistrationDuplicateFirebaseTest {
    @Test fun wrappedCollisionIsRecognizedButIncorrectPasswordIsNot() {
        assertTrue(CustomerAuth.isExistingAccount(java.util.concurrent.ExecutionException(
            FirebaseAuthException("ERROR_EMAIL_ALREADY_IN_USE", "Account exists"))))
        assertFalse(CustomerAuth.isExistingAccount(
            FirebaseAuthException("ERROR_INVALID_CREDENTIAL", "Invalid credentials")))
    }
}
