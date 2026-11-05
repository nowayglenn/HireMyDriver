package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class PasswordChangeValidationTest {
    @Test fun currentPasswordIsRequiredBeforeSendingAnything() {
        assertEquals(PasswordChangeField.CURRENT, PasswordChangeValidation.validate("", "Newpass123", "Newpass123")?.field)
    }

    @Test fun confirmationIsComparedExactlyIncludingWhitespace() {
        assertNull(PasswordChangeValidation.validate("Oldpass123", " Newpass123 ", " Newpass123 "))
        assertEquals(PasswordChangeField.CONFIRM,
            PasswordChangeValidation.validate("Oldpass123", " Newpass123 ", "Newpass123")?.field)
    }

    @Test fun unchangedPasswordIsRejectedButCredentialsAreNotTrimmed() {
        assertEquals(PasswordChangeField.NEW, PasswordChangeValidation.validate("Samepass123", "Samepass123", "Samepass123")?.field)
        assertNull(PasswordChangeValidation.validate("Samepass123 ", "Samepass123", "Samepass123"))
    }

    @Test fun enforcesRequirementsAlreadyShownOnTheScreen() {
        listOf("Aa1", "password123", "PASSWORD123", "PasswordOnly", "12345678").forEach { weak ->
            assertEquals(weak, PasswordChangeField.NEW, PasswordChangeValidation.validate("Oldpass123", weak, weak)?.field)
        }
        assertNull(PasswordChangeValidation.validate("Oldpass123", "Abcdefg1", "Abcdefg1"))
    }
}
