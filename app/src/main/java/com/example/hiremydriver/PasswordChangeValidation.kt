package com.example.hiremydriver

internal enum class PasswordChangeField { CURRENT, NEW, CONFIRM, FORM }
internal data class PasswordChangeFailure(val field: PasswordChangeField, val message: String)

internal object PasswordChangeValidation {
    // Do not trim passwords: whitespace is part of the credential supplied to Firebase.
    fun validate(current: String, next: String, confirmation: String): PasswordChangeFailure? = when {
        current.isEmpty() -> PasswordChangeFailure(PasswordChangeField.CURRENT, "Please enter current password")
        next.length < 8 -> PasswordChangeFailure(PasswordChangeField.NEW, "Password must be at least 8 characters long")
        next == current -> PasswordChangeFailure(PasswordChangeField.NEW, "New password must be different from current password")
        next.none { it in 'A'..'Z' } || next.none { it in 'a'..'z' } || next.none { it in '0'..'9' } ->
            PasswordChangeFailure(PasswordChangeField.NEW, "Include uppercase and lowercase letters and at least one number")
        confirmation.isEmpty() -> PasswordChangeFailure(PasswordChangeField.CONFIRM, "Please confirm new password")
        confirmation != next -> PasswordChangeFailure(PasswordChangeField.CONFIRM, "Passwords do not match")
        else -> null
    }
}
