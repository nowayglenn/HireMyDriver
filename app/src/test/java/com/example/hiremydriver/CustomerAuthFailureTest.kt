package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class CustomerAuthFailureTest {
    @Test fun restrictedApiInsideInternalErrorIsNotAnEmailError() {
        val result = CustomerAuthFailureMessages.describe("ERROR_INTERNAL_ERROR",
            "An internal error has occurred. [ Requests to this API identitytoolkit method SignUp are blocked. ]")
        assertEquals(AuthErrorField.FORM, result.field)
        assertTrue(result.message.contains("Firebase API settings"))
    }

    @Test fun distinguishesBlockedServiceFromBlockedAndroidCertificate() {
        assertTrue(CustomerAuthFailureMessages.describe(null, "API_KEY_SERVICE_BLOCKED")
            .message.contains("Firebase API settings"))
        val result = CustomerAuthFailureMessages.describe(null, "API_KEY_ANDROID_APP_BLOCKED")
        assertEquals(AuthErrorField.FORM, result.field)
        assertTrue(result.message.contains("signing certificate"))
    }

    @Test fun onlyFieldSpecificProblemsHighlightEmailOrPassword() {
        assertEquals(AuthErrorField.EMAIL, CustomerAuthFailureMessages.describe("ERROR_EMAIL_ALREADY_IN_USE").field)
        assertEquals(AuthErrorField.EMAIL, CustomerAuthFailureMessages.describe("ERROR_INVALID_EMAIL").field)
        assertEquals(AuthErrorField.PASSWORD, CustomerAuthFailureMessages.describe("ERROR_WEAK_PASSWORD").field)
        listOf("NETWORK", "ERROR_OPERATION_NOT_ALLOWED", "ERROR_APP_NOT_AUTHORIZED", "UNKNOWN").forEach {
            assertEquals(AuthErrorField.FORM, CustomerAuthFailureMessages.describe(it).field)
        }
    }

    @Test fun unknownServerDetailsNeverAppearInTheMessage() {
        val result = CustomerAuthFailureMessages.describe("UNKNOWN", "email=private@example.com password=secret idToken=secret")
        assertFalse(result.message.contains("private@example.com"))
        assertFalse(result.message.contains("secret"))
        assertEquals(AuthErrorField.FORM, result.field)
    }

    @Test fun missingConfigurationAndInvalidKeyHaveActionableMessages() {
        assertTrue(CustomerAuthFailureMessages.describe(null, "CONFIGURATION_NOT_FOUND").message.contains("not configured"))
        assertTrue(CustomerAuthFailureMessages.describe("ERROR_INVALID_API_KEY").message.contains("configuration is invalid"))
    }
}
