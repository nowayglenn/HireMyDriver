package com.example.hiremydriver

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task

internal data class PasswordChangeState(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val failure: PasswordChangeFailure? = null
)

internal class ChangePasswordViewModel(
    private val changePassword: (Context, String, String) -> Task<Void> = CustomerAuth::changePassword
) : ViewModel() {
    private val mutableState = MutableLiveData(PasswordChangeState())
    val state: LiveData<PasswordChangeState> = mutableState
    private var cleared = false

    fun save(context: Context, current: String, next: String, confirmation: String) {
        if (mutableState.value?.saving == true || mutableState.value?.saved == true) return
        val invalid = PasswordChangeValidation.validate(current, next, confirmation)
        if (invalid != null) {
            mutableState.value = PasswordChangeState(failure = invalid)
            return
        }
        mutableState.value = PasswordChangeState(saving = true)
        // Retain the request across rotation, without storing passwords in saved state or preferences.
        changePassword(context.applicationContext, current, next).addOnCompleteListener { task ->
            if (!cleared) {
                mutableState.value = if (task.isSuccessful) PasswordChangeState(saved = true)
                    else PasswordChangeState(failure = CustomerAuth.passwordChangeFailure(task.exception))
            }
        }
    }

    override fun onCleared() { cleared = true }
}
