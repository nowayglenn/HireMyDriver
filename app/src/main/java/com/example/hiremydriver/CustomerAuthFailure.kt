package com.example.hiremydriver

internal enum class AuthErrorField { EMAIL, PASSWORD, FORM }

internal data class CustomerAuthFailure(
    val message: String,
    val field: AuthErrorField = AuthErrorField.FORM
)

/** Maps known errors to safe messages; never displays server responses, credentials or tokens. */
internal object CustomerAuthFailureMessages {
    fun describe(code: String?, detail: String? = null): CustomerAuthFailure {
        // Firebase can wrap Google's API-key restriction response in ERROR_INTERNAL_ERROR.
        val response = detail.orEmpty()
        return when {
            response.contains("API_KEY_SERVICE_BLOCKED") ||
                (response.contains("Requests to this API") && response.contains("are blocked")) ->
                CustomerAuthFailure("Sign-in is blocked by the app's Firebase API settings. Please contact support.")
            response.contains("API_KEY_ANDROID_APP_BLOCKED") ||
                response.contains("Requests from this Android client application") ->
                CustomerAuthFailure("This app's Android package or signing certificate is not authorized. Please contact support.")
            response.contains("API_KEY_INVALID") || code == "ERROR_INVALID_API_KEY" ->
                CustomerAuthFailure("The app's Firebase configuration is invalid. Please contact support.")
            code == "SETUP_MISSING" || response.contains("CONFIGURATION_NOT_FOUND") ->
                CustomerAuthFailure("Customer sign-in is not configured yet. Please contact support.")
            code == "NETWORK" || code == "ERROR_NETWORK_REQUEST_FAILED" ->
                CustomerAuthFailure("Check your internet connection and try again.")
            code == "TOO_MANY_REQUESTS" || code == "ERROR_TOO_MANY_REQUESTS" ->
                CustomerAuthFailure("Too many attempts. Please wait a while and try again.")
            code == "ERROR_INVALID_EMAIL" ->
                CustomerAuthFailure("Please enter a valid email address.", AuthErrorField.EMAIL)
            code == "ERROR_EMAIL_ALREADY_IN_USE" ->
                CustomerAuthFailure("An account already uses this email. Sign in or reset your password.", AuthErrorField.EMAIL)
            code == "ERROR_WEAK_PASSWORD" || code == "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS" ->
                CustomerAuthFailure("Choose a stronger password that meets the account password policy.", AuthErrorField.PASSWORD)
            code == "ERROR_USER_DISABLED" ->
                CustomerAuthFailure("This account is disabled. Please contact support.")
            code == "ERROR_OPERATION_NOT_ALLOWED" || code == "ERROR_PASSWORD_LOGIN_DISABLED" ->
                CustomerAuthFailure("Email/password sign-in has not been enabled yet. Please contact support.")
            code == "ERROR_APP_NOT_AUTHORIZED" ->
                CustomerAuthFailure("This app is not authorized to use Firebase Authentication. Please contact support.")
            code in setOf("ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_LOGIN_CREDENTIALS") ->
                CustomerAuthFailure("Email or password is incorrect.")
            else -> CustomerAuthFailure("Could not complete the request. Please try again.")
        }
    }
}
